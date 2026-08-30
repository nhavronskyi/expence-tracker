import { useEffect, useState } from "react";
import { getAccounts, importFile } from "../api";
import type { Account, ImportSummary } from "../types";

export function ImportPage() {
  const [accounts, setAccounts] = useState<Account[]>([]);
  const [accountId, setAccountId] = useState<number | null>(null);
  const [file, setFile] = useState<File | null>(null);
  const [summary, setSummary] = useState<ImportSummary | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    getAccounts()
      .then((list) => {
        setAccounts(list);
        setAccountId(list[0]?.id ?? null);
      })
      .catch((e: Error) => setError(e.message));
  }, []);

  async function submit() {
    if (accountId == null || !file) return;
    setBusy(true);
    setError(null);
    setSummary(null);
    try {
      setSummary(await importFile(accountId, file));
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <section>
      <h2>Import statement</h2>
      <div className="row">
        <select
          value={accountId ?? ""}
          onChange={(e) => setAccountId(Number(e.target.value))}
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
        <button disabled={busy || accountId == null || !file} onClick={submit}>
          {busy ? "Importing..." : "Import"}
        </button>
      </div>

      {error && <p className="error">{error}</p>}

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
    </section>
  );
}
