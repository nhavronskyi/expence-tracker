# finance

Backend for monthly import of Pekao bank statements, categorization via an LLM (Ollama Cloud),
and statistics, with a small React + TypeScript frontend (`frontend/`) for import (with live
progress and cancellation), the review queue, and stats.

Compiles and passes tests on Java 25 (`./gradlew test`).

## Running locally

**Full stack (Docker):** `docker compose up --build` (uses `docker-compose.yml`) — builds the
frontend, bundles it into the Spring Boot jar as static content, and serves everything from
`http://localhost:8080`. Needs a `.env` with `FINANCE_DB_PASSWORD` and `OLLAMA_API_KEY` (an
Ollama Cloud key from ollama.com/settings/keys — this app calls Ollama Cloud, not a local Ollama
instance). `finance.yml` is a separate compose file for the actual homelab deployment; don't
confuse the two.

**Homelab deployment (`finance.yml`):** every push to `main` builds the image via GitHub Actions
(`.github/workflows/docker-publish.yml`) and publishes it to
`ghcr.io/nhavronskyi/expence-tracker:latest`. On the prod host, deploying a new version is just:

```
docker compose -f finance.yml pull
docker compose -f finance.yml up -d
```

The GHCR package is private by default, so the prod host needs a one-time
`docker login ghcr.io -u nhavronskyi` with a PAT that has `read:packages` scope (or make the
package public in GitHub if that's acceptable for this repo).

**Frontend dev server (hot reload):** run the backend separately (`./gradlew bootRun`, or
`docker compose up finance-db finance-app`), then in `frontend/`: `npm install && npm run dev`.
Vite proxies `/api` to `http://localhost:8080`, so no CORS setup is needed.

## Import pipeline

```
CSV file
  → parser (column mapping from config, not from code)
  → raw_transaction (never mutated)
  → merchant name normalization (strips store numbers, order-id-style reference codes, city noise)
  → dedup_key (account + date + amount + description + occurrence number) - the only duplicate
    guard; re-importing the same export is safe and expected (e.g. after deleting some rows,
    or a periodic full-history export) - unchanged rows are skipped, missing ones re-inserted
  → type resolution:  own IBAN → INTERNAL_TRANSFER
                       amount > 0 → INCOME
                       otherwise  → EXPENSE
  → NBP exchange rate conversion for non-PLN rows (amount_pln_minor)
  → rules learned from previous answers (any kind, not just EXPENSE)
  → LLM (EXPENSE and INCOME without a category)
  → EXPENSE, confidence ≥ 0.80 → accepted automatically
  → EXPENSE, confidence < 0.80, or any INCOME, or no answer → review queue
  → pairing internal transfer legs
```

Import runs asynchronously: `POST /api/import` returns a job id immediately, the frontend polls
progress (batches sent to the LLM so far) and can cancel mid-run - whatever wasn't categorized
yet when cancelled just falls into the same review-queue path as an unanswered LLM batch, so
nothing is lost.

## Why internal transfers never go through the LLM

This is the one decision in the system that affects the correctness of every
monthly total. A language model gives no guarantee of global consistency: it
might call a transfer between your own accounts internal once and an expense
the next time, and the monthly numbers would drift apart with no error signal.
So this is decided deterministically and repeatably by the registry of your
own IBANs plus amount pairing within a window of a few days.

The LLM only ever sees external expenses (and, since income can also carry a category now,
uncategorized income) and only answers the question "which category". It never computes
anything and never decides the transaction type.

## Income is never auto-accepted

Unlike expenses, an LLM category suggestion for an INCOME transaction is never silently
accepted, no matter how confident the model is - it always goes to the review queue first.
Only a learned `merchant_rule` (from resolving one instance with "remember this merchant"
checked) can auto-categorize a future payment from the same counterparty. The reasoning: an
expense miscategorized by the LLM is a minor annoyance; a stranger's payment silently absorbed
into your numbers without you ever seeing it is not something this app should decide alone.

## The learning loop

Every answer in the review queue with `learnRule=true` creates an entry in
`merchant_rule` keyed on the normalized merchant name. Without this the system
would ask about Biedronka every month and the whole project would be
pointless. Normalization collapses `BIEDRONKA 1234 WARSZAWA` and `BIEDRONKA
5678 KRAKOW` into the same key — the single biggest win in the pipeline, and
it costs zero API calls.

## Netting

If money moves both ways with the same counterparty (a refund, a settle-up) or under the same
_category_ (e.g. you send one person money and get paid back by someone else for the same
thing), stats show the net effect instead of the full gross amounts on both sides - checked
first by merchant, then by category for whatever didn't net at the merchant level. Every time
netting changes a number, `PeriodReport.nettedCounterparties` says so explicitly, so it's never
a silent adjustment. Category totals (`byCategory`) are a straightforward signed net per
category (income positive, expense negative) across every EXPENSE/INCOME transaction with that
category, independent of the merchant/category netting used for the top-line totals.

## Endpoints

| Method | Path                                                                | Description                                                                  |
| ------ | ------------------------------------------------------------------- | ---------------------------------------------------------------------------- |
| GET    | `/api/accounts`                                                     | active accounts                                                              |
| POST   | `/api/accounts`                                                     | `{"iban","label","scope","type","currency"}` — add an account                |
| GET    | `/api/categories`                                                   | `{name,label}` pairs — categories are user-addable, not a fixed enum         |
| POST   | `/api/categories`                                                   | `{"label","definition"}` — code is derived from the label                    |
| POST   | `/api/import?accountId=1`                                           | multipart `file` — starts an import, returns `{"jobId"}`                     |
| GET    | `/api/import/{jobId}/status`                                        | `{phase,processed,total,summary,error}` — poll while importing               |
| POST   | `/api/import/{jobId}/cancel`                                        | cancel an in-flight import (no-op once terminal)                             |
| POST   | `/api/import/clear`                                                 | wipe all transactions/imports/review items (keeps accounts/categories/rules) |
| POST   | `/api/import/backfill-fx`                                           | re-run NBP conversion for rows that missed it                                |
| POST   | `/api/import/renormalize-merchants`                                 | re-run merchant normalization on every existing row                          |
| GET    | `/api/review`                                                       | open review items, with merchant/amount/kind/suggestions                     |
| POST   | `/api/review/{id}`                                                  | `{"category":"FRIDGE","kind":"EXPENSE","learnRule":true}`                    |
| GET    | `/api/stats/range?from=2026-07-01&to=2026-07-31&scope=PERSONAL`     | period report (any date range, not just a calendar month)                    |
| GET    | `/api/stats/count`                                                  | `{"total"}` transactions in the database                                     |
| GET    | `/api/stats/transactions?category=FRIDGE&from=...&to=...&scope=...` | transactions in one category for that period, most-recent-first              |

The report returns a `warnings` field — unpaired transfers, uncategorized
expenses, foreign-currency amounts without conversion. This is the QA layer:
the numbers don't pretend to be complete when they aren't.

## What needs to be configured before the first import

1. **Column mapping** in `application.yml` → `finance.pekao.columns`, matched
   against Pekao's "Lista operacji" CSV export (`UTF-8`, `;` delimiter).
2. **Records in the `account` table** — every one of your own IBANs must be
   there, otherwise transfers between your accounts will be counted as
   expenses. Add them via `POST /api/accounts` or the "+ Add account" button
   on the Import page.

## What's intentionally not here

- Handling credit card billing cycles — the month is deliberately counted by
  transaction date.
- Authorization. The app listens on loopback only; exposure happens only
  through NPM.
- Deleting or editing individual transactions — `/api/import/clear` wipes
  everything; there's no per-row delete/edit.
- Category deletion or renaming once created.
