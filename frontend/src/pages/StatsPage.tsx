import { useState } from "react";
import { getMonthlyStats } from "../api";
import type { AccountScope, MonthlyReport } from "../types";

function currentMonth(): string {
  const now = new Date();
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, "0")}`;
}

export function StatsPage() {
  const [month, setMonth] = useState(currentMonth());
  const [scope, setScope] = useState<AccountScope>("PERSONAL");
  const [report, setReport] = useState<MonthlyReport | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function load() {
    setBusy(true);
    setError(null);
    try {
      setReport(await getMonthlyStats(month, scope));
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }

  const categoryRows = report
    ? Object.entries(report.byCategory).sort(
        ([, a], [, b]) => (b ?? 0) - (a ?? 0),
      )
    : [];

  return (
    <section>
      <h2>Monthly stats</h2>
      <div className="row">
        <input
          type="month"
          value={month}
          onChange={(e) => setMonth(e.target.value)}
        />
        <select
          value={scope}
          onChange={(e) => setScope(e.target.value as AccountScope)}
        >
          <option value="PERSONAL">Personal</option>
          <option value="BUSINESS">Business</option>
        </select>
        <button disabled={busy} onClick={load}>
          {busy ? "Loading..." : "Load"}
        </button>
      </div>

      {error && <p className="error">{error}</p>}

      {report && (
        <>
          <table className="summary">
            <tbody>
              <tr>
                <td>Total expenses</td>
                <td>{report.totalExpenses.toFixed(2)}</td>
              </tr>
              <tr>
                <td>Total income</td>
                <td>{report.totalIncome.toFixed(2)}</td>
              </tr>
              <tr>
                <td>Net</td>
                <td>{report.net.toFixed(2)}</td>
              </tr>
              <tr>
                <td>Excluded internal transfers</td>
                <td>{report.excludedInternalTransfers.toFixed(2)}</td>
              </tr>
              <tr>
                <td>Uncategorized</td>
                <td>{report.uncategorizedCount}</td>
              </tr>
            </tbody>
          </table>

          {report.warnings.length > 0 && (
            <ul className="warnings">
              {report.warnings.map((w, i) => (
                <li key={i}>{w}</li>
              ))}
            </ul>
          )}

          <table className="summary">
            <thead>
              <tr>
                <th>Category</th>
                <th>Amount</th>
              </tr>
            </thead>
            <tbody>
              {categoryRows.map(([category, amount]) => (
                <tr key={category}>
                  <td>{category}</td>
                  <td>{(amount ?? 0).toFixed(2)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </>
      )}
    </section>
  );
}
