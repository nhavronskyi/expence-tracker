# finance

Backend for monthly import of Pekao bank statements, categorization, and statistics, with a
small React + TypeScript frontend (`frontend/`) for import, the review queue, and monthly stats.

Compiles and passes tests on Java 25 (`./gradlew test`).

## Running locally

**Full stack (Docker):** `docker compose up --build` — builds the frontend, bundles it into the
Spring Boot jar as static content, and serves everything from `http://localhost:8080`. Needs a
`.env` with `FINANCE_DB_PASSWORD` and `OLLAMA_API_KEY`.

**Frontend dev server (hot reload):** run the backend separately (`./gradlew bootRun`, or
`docker compose up finance-db finance-app`), then in `frontend/`: `npm install && npm run dev`.
Vite proxies `/api` to `http://localhost:8080`, so no CORS setup is needed.

## Import pipeline

```
CSV file
  → sha256 (rejects re-importing the same file)
  → parser (column mapping from config, not from code)
  → raw_transaction (never mutated)
  → merchant name normalization
  → dedup_key (account + date + amount + description + occurrence number)
  → type resolution:  own IBAN → INTERNAL_TRANSFER
                       amount > 0 → INCOME
                       otherwise  → EXPENSE
  → rules learned from previous answers
  → LLM (only EXPENSE without a category)
  → confidence < 0.80 → review queue
  → pairing internal transfer legs
```

## Why internal transfers never go through the LLM

This is the one decision in the system that affects the correctness of every
monthly total. A language model gives no guarantee of global consistency: it
might call a transfer between your own accounts internal once and an expense
the next time, and the monthly numbers would drift apart with no error signal.
So this is decided deterministically and repeatably by the registry of your
own IBANs plus amount pairing within a window of a few days.

The LLM only ever sees external expenses and only answers the question "which
category". It never computes anything and never decides the transaction type.

## The learning loop

Every answer in the review queue with `learnRule=true` creates an entry in
`merchant_rule` keyed on the normalized merchant name. Without this the system
would ask about Biedronka every month and the whole project would be
pointless. Normalization collapses `BIEDRONKA 1234 WARSZAWA` and `BIEDRONKA
5678 KRAKOW` into the same key — the single biggest win in the pipeline, and
it costs zero API calls.

## Endpoints

| Method | Path                                              | Description                                               |
| ------ | ------------------------------------------------- | --------------------------------------------------------- |
| GET    | `/api/accounts`                                   | active accounts, for the import account picker            |
| GET    | `/api/categories`                                 | category enum as `{name,label}` pairs                     |
| POST   | `/api/import?accountId=1`                         | multipart `file` — import a statement                     |
| GET    | `/api/review`                                     | open questions awaiting resolution                        |
| POST   | `/api/review/{id}`                                | `{"category":"FRIDGE","kind":"EXPENSE","learnRule":true}` |
| GET    | `/api/stats/monthly?month=2026-07&scope=PERSONAL` | monthly report                                            |

The report returns a `warnings` field — unpaired transfers, uncategorized
expenses, foreign-currency amounts without conversion. This is the QA layer:
the numbers don't pretend to be complete when they aren't.

## What needs to be configured before the first import

1. **Column mapping** in `application.yml` → `finance.pekao.columns`.
   The names in the repo are a guess — Pekao's export format isn't publicly
   documented. They need to be swapped for the real header from an actual file.
2. **Encoding and delimiter** — `windows-1250` and `;` are typical for a
   Polish export, but need confirming against a real file.
3. **Records in the `account` table** — every one of your own IBANs must be
   there, otherwise transfers between your accounts will be counted as
   expenses.

## What's intentionally not here

- Currency conversion via NBP exchange rates — there's an `amount_pln_minor`
  column and a warning in the report, but actually fetching rates is a
  separate step.
- Handling credit card billing cycles — the month is deliberately counted by
  transaction date.
- Authorization. The app listens on loopback only; exposure happens only
  through NPM.
