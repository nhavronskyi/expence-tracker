import { useState } from "react";
import { getCategoryTransactions, getStats } from "../api";
import type { CategoryTransaction, PeriodReport } from "../types";

type Mode = "month" | "range";

function currentMonth(): string {
  const now = new Date();
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, "0")}`;
}

function today(): string {
  return new Date().toISOString().slice(0, 10);
}

function monthToRange(month: string): { from: string; to: string } {
  const [yearStr, monthStr] = month.split("-");
  const year = Number(yearStr);
  const monthIndex = Number(monthStr) - 1;
  const lastDay = new Date(year, monthIndex + 1, 0).getDate();
  return {
    from: `${yearStr}-${monthStr}-01`,
    to: `${yearStr}-${monthStr}-${String(lastDay).padStart(2, "0")}`,
  };
}

export function StatsPage() {
  const [mode, setMode] = useState<Mode>("month");
  const [month, setMonth] = useState(currentMonth());
  const [from, setFrom] = useState(today());
  const [to, setTo] = useState(today());
  const [report, setReport] = useState<PeriodReport | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const [selectedCategory, setSelectedCategory] = useState<string | null>(null);
  const [categoryTxns, setCategoryTxns] = useState<CategoryTransaction[]>([]);
  const [txnsBusy, setTxnsBusy] = useState(false);
  const [txnsError, setTxnsError] = useState<string | null>(null);
  const [loadedRange, setLoadedRange] = useState<{
    from: string;
    to: string;
  } | null>(null);

  async function load() {
    const range = mode === "month" ? monthToRange(month) : { from, to };
    if (range.from > range.to) {
      setError("The start date must be before the end date.");
      return;
    }
    setBusy(true);
    setError(null);
    setSelectedCategory(null);
    try {
      setReport(await getStats(range.from, range.to));
      setLoadedRange(range);
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }

  async function selectCategory(category: string) {
    if (selectedCategory === category) {
      setSelectedCategory(null);
      return;
    }
    if (!loadedRange) return;
    setSelectedCategory(category);
    setTxnsBusy(true);
    setTxnsError(null);
    try {
      setCategoryTxns(
        await getCategoryTransactions(
          category,
          loadedRange.from,
          loadedRange.to,
        ),
      );
    } catch (e) {
      setTxnsError((e as Error).message);
    } finally {
      setTxnsBusy(false);
    }
  }

  const categoryRows = report
    ? Object.entries(report.byCategory).sort(
        ([, a], [, b]) => Math.abs(b ?? 0) - Math.abs(a ?? 0),
      )
    : [];

  return (
    <section>
      <h2>Stats</h2>
      <div className="row">
        <button
          className={mode === "month" ? "tab tab-active" : "tab"}
          onClick={() => setMode("month")}
        >
          Month
        </button>
        <button
          className={mode === "range" ? "tab tab-active" : "tab"}
          onClick={() => setMode("range")}
        >
          Custom range
        </button>
      </div>

      <div className="row">
        {mode === "month" ? (
          <input
            type="month"
            value={month}
            onChange={(e) => setMonth(e.target.value)}
          />
        ) : (
          <>
            <input
              type="date"
              value={from}
              onChange={(e) => setFrom(e.target.value)}
            />
            <span>to</span>
            <input
              type="date"
              value={to}
              onChange={(e) => setTo(e.target.value)}
            />
          </>
        )}
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

          {report.nettedCounterparties.length > 0 && (
            <ul className="netted">
              {report.nettedCounterparties.map((n, i) => (
                <li key={i}>{n}</li>
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
                <tr
                  key={category}
                  className="clickable-row"
                  onClick={() => selectCategory(category)}
                >
                  <td>{category}</td>
                  <td className={(amount ?? 0) > 0 ? "income" : ""}>
                    {(amount ?? 0) > 0 ? "+" : ""}
                    {(amount ?? 0).toFixed(2)}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>

          {selectedCategory && (
            <div className="category-detail">
              <h3>{selectedCategory} transactions</h3>
              {txnsBusy && <p>Loading...</p>}
              {txnsError && <p className="error">{txnsError}</p>}
              {!txnsBusy && !txnsError && categoryTxns.length === 0 && (
                <p>No transactions.</p>
              )}
              {categoryTxns.length > 0 && (
                <table className="summary">
                  <thead>
                    <tr>
                      <th>Date</th>
                      <th>Merchant</th>
                      <th>Amount</th>
                    </tr>
                  </thead>
                  <tbody>
                    {categoryTxns.map((t) => (
                      <tr key={t.txnId}>
                        <td>{t.txnDate}</td>
                        <td>{t.merchant}</td>
                        <td className={t.amount > 0 ? "income" : ""}>
                          {t.amount > 0 ? "+" : ""}
                          {t.amount.toFixed(2)} {t.currency}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
            </div>
          )}
        </>
      )}
    </section>
  );
}
