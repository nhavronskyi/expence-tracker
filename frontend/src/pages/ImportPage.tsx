import { useEffect, useRef, useState } from "react";
import {
  backfillFx,
  cancelImport,
  clearTransactions,
  createAccount,
  getAccounts,
  getImportStatus,
  renormalizeMerchants,
  startImport,
} from "../api";
import type {
  Account,
  AccountScope,
  AccountType,
  ImportJobStatus,
} from "../types";

const PHASE_LABELS: Record<string, string> = {
  PARSING: "Parsing...",
  CATEGORIZING: "Categorizing...",
  CANCELLED: "Cancelled",
  FAILED: "Failed",
};

function NewAccountForm({
  onCreated,
}: {
  onCreated: (account: Account) => void;
}) {
  const [label, setLabel] = useState("");
  const [iban, setIban] = useState("");
  const [scope, setScope] = useState<AccountScope>("PERSONAL");
  const [type, setType] = useState<AccountType>("CURRENT");
  const [currency, setCurrency] = useState("PLN");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function submit() {
    if (!label.trim()) return;
    setBusy(true);
    setError(null);
    try {
      const account = await createAccount({
        label: label.trim(),
        iban: iban.trim(),
        scope,
        type,
        currency,
      });
      setLabel("");
      setIban("");
      onCreated(account);
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="new-account">
      <div className="row">
        <input
          placeholder="Label (e.g. Checking)"
          value={label}
          onChange={(e) => setLabel(e.target.value)}
        />
        <input
          placeholder="IBAN (optional)"
          value={iban}
          onChange={(e) => setIban(e.target.value)}
        />
        <select
          value={scope}
          onChange={(e) => setScope(e.target.value as AccountScope)}
        >
          <option value="PERSONAL">Personal</option>
          <option value="BUSINESS">Business</option>
        </select>
        <select
          value={type}
          onChange={(e) => setType(e.target.value as AccountType)}
        >
          <option value="CURRENT">Current</option>
          <option value="CREDIT_CARD">Credit card</option>
        </select>
        <input
          className="currency"
          placeholder="PLN"
          value={currency}
          onChange={(e) => setCurrency(e.target.value)}
        />
        <button disabled={busy || !label.trim()} onClick={submit}>
          {busy ? "Adding..." : "Add account"}
        </button>
      </div>
      {error && <p className="error">{error}</p>}
    </div>
  );
}

export function ImportPage({ onDataChanged }: { onDataChanged: () => void }) {
  const [accounts, setAccounts] = useState<Account[]>([]);
  const [accountId, setAccountId] = useState<number | null>(null);
  const [file, setFile] = useState<File | null>(null);
  const [job, setJob] = useState<ImportJobStatus | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [showNewAccount, setShowNewAccount] = useState(false);
  const [fxBusy, setFxBusy] = useState(false);
  const [fxResult, setFxResult] = useState<string | null>(null);
  const [normBusy, setNormBusy] = useState(false);
  const [normResult, setNormResult] = useState<string | null>(null);
  const [clearBusy, setClearBusy] = useState(false);
  const pollRef = useRef<number | null>(null);

  function loadAccounts() {
    getAccounts()
      .then((list) => {
        setAccounts(list);
        setAccountId((current) => current ?? list[0]?.id ?? null);
      })
      .catch((e: Error) => setError(e.message));
  }

  useEffect(loadAccounts, []);

  useEffect(() => {
    return () => {
      if (pollRef.current != null) window.clearInterval(pollRef.current);
    };
  }, []);

  function handleCreated(account: Account) {
    setShowNewAccount(false);
    setAccounts((prev) =>
      [...prev, account].sort((a, b) => a.label.localeCompare(b.label)),
    );
    setAccountId(account.id);
  }

  function poll(jobId: string) {
    pollRef.current = window.setInterval(async () => {
      try {
        const status = await getImportStatus(jobId);
        setJob(status);
        if (
          status.phase === "DONE" ||
          status.phase === "CANCELLED" ||
          status.phase === "FAILED"
        ) {
          if (pollRef.current != null) window.clearInterval(pollRef.current);
          if (status.error) setError(status.error);
          onDataChanged();
        }
      } catch (e) {
        if (pollRef.current != null) window.clearInterval(pollRef.current);
        setError((e as Error).message);
      }
    }, 1000);
  }

  async function submit() {
    if (accountId == null || !file) return;
    setError(null);
    setJob(null);
    try {
      const { jobId } = await startImport(accountId, file);
      setJob({
        jobId,
        phase: "PARSING",
        processed: 0,
        total: 0,
        summary: null,
        error: null,
      });
      poll(jobId);
    } catch (e) {
      setError((e as Error).message);
    }
  }

  async function cancel() {
    if (!job) return;
    try {
      await cancelImport(job.jobId);
    } catch (e) {
      setError((e as Error).message);
    }
  }

  async function runFxBackfill() {
    setFxBusy(true);
    setFxResult(null);
    setError(null);
    try {
      const { fixed } = await backfillFx();
      setFxResult(
        `Converted ${fixed} transaction${fixed === 1 ? "" : "s"} to PLN.`,
      );
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setFxBusy(false);
    }
  }

  async function runRenormalize() {
    setNormBusy(true);
    setNormResult(null);
    setError(null);
    try {
      const { changed } = await renormalizeMerchants();
      setNormResult(
        `Updated ${changed} transaction${changed === 1 ? "" : "s"}.`,
      );
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setNormBusy(false);
    }
  }

  async function runClear() {
    if (
      !window.confirm(
        "Delete all transactions, imports, and review items? Accounts and categories are kept.",
      )
    ) {
      return;
    }
    setClearBusy(true);
    setError(null);
    try {
      await clearTransactions();
      setJob(null);
      onDataChanged();
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setClearBusy(false);
    }
  }

  const running =
    job != null && (job.phase === "PARSING" || job.phase === "CATEGORIZING");
  const summary = job?.summary ?? null;

  return (
    <section>
      <h2>Import statement</h2>

      {accounts.length === 0 && !showNewAccount && (
        <p>No accounts yet — add one to import a statement.</p>
      )}

      <div className="row">
        <select
          value={accountId ?? ""}
          onChange={(e) => setAccountId(Number(e.target.value))}
          disabled={accounts.length === 0}
        >
          {accounts.map((a) => (
            <option key={a.id} value={a.id}>
              {a.label} ({a.currency})
            </option>
          ))}
        </select>
        <input
          type="file"
          accept=".csv"
          onChange={(e) => setFile(e.target.files?.[0] ?? null)}
        />
        <button
          disabled={running || accountId == null || !file}
          onClick={submit}
        >
          {running ? "Importing..." : "Import"}
        </button>
        <button className="link" onClick={() => setShowNewAccount((v) => !v)}>
          {showNewAccount ? "Cancel" : "+ Add account"}
        </button>
      </div>

      {showNewAccount && <NewAccountForm onCreated={handleCreated} />}

      {error && <p className="error">{error}</p>}

      {job && running && (
        <div className="progress">
          <div className="row" style={{ marginBottom: 0 }}>
            <strong>{PHASE_LABELS[job.phase] ?? job.phase}</strong>
            {job.phase === "CATEGORIZING" && job.total > 0 && (
              <span>
                {job.processed} / {job.total}
              </span>
            )}
            <button className="link" onClick={cancel}>
              Cancel
            </button>
          </div>
          {job.phase === "CATEGORIZING" && job.total > 0 && (
            <div className="progress-bar">
              <div
                className="progress-bar-fill"
                style={{
                  width: `${Math.round((job.processed / job.total) * 100)}%`,
                }}
              />
            </div>
          )}
        </div>
      )}

      {job && job.phase === "CANCELLED" && (
        <p className="error">
          Import cancelled — anything not yet categorized was queued for review.
        </p>
      )}

      {summary && (
        <table className="summary">
          <tbody>
            <tr>
              <td>Rows parsed</td>
              <td>{summary.rowsParsed}</td>
            </tr>
            <tr>
              <td>Inserted</td>
              <td>{summary.inserted}</td>
            </tr>
            <tr>
              <td>Duplicates skipped</td>
              <td>{summary.duplicatesSkipped}</td>
            </tr>
            <tr>
              <td>Internal transfers</td>
              <td>{summary.internalTransfers}</td>
            </tr>
            <tr>
              <td>Categorized by rule</td>
              <td>{summary.categorizedByRule}</td>
            </tr>
            <tr>
              <td>Categorized by LLM</td>
              <td>{summary.categorizedByLlm}</td>
            </tr>
            <tr>
              <td>Queued for review</td>
              <td>{summary.queuedForReview}</td>
            </tr>
          </tbody>
        </table>
      )}

      <div className="row">
        <button className="link" disabled={fxBusy} onClick={runFxBackfill}>
          {fxBusy ? "Converting..." : "Backfill missing FX rates"}
        </button>
        <button className="link" disabled={normBusy} onClick={runRenormalize}>
          {normBusy ? "Updating..." : "Re-normalize merchants"}
        </button>
        <button className="link danger" disabled={clearBusy} onClick={runClear}>
          {clearBusy ? "Clearing..." : "Clear all transactions"}
        </button>
      </div>
      {fxResult && <p>{fxResult}</p>}
      {normResult && <p>{normResult}</p>}
    </section>
  );
}
