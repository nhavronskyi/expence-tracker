import { useEffect, useState } from "react";
import {
  getCategories,
  getCategoryTransactions,
  getStats,
  getTransfers,
  recategorizeTransaction,
} from "../api";
import type {
  CategoryOption,
  CategoryTransaction,
  PeriodReport,
  TxnKind,
} from "../types";

type Mode = "month" | "range";

const TRANSFERS = "__transfers__";

const KINDS: TxnKind[] = ["EXPENSE", "INCOME", "INTERNAL_TRANSFER", "UNKNOWN"];
const KIND_LABELS: Record<TxnKind, string> = {
  EXPENSE: "Expense",
  INCOME: "Income",
  INTERNAL_TRANSFER: "Internal transfer",
  UNKNOWN: "Unknown",
};

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

function fetchBucket(
  category: string,
  range: { from: string; to: string },
): Promise<CategoryTransaction[]> {
  return category === TRANSFERS
    ? getTransfers(range.from, range.to)
    : getCategoryTransactions(category, range.from, range.to);
}

function TxnRow({
  t,
  categories,
  defaultCategory,
  onSaved,
}: {
  t: CategoryTransaction;
  categories: CategoryOption[];
  defaultCategory: string;
  onSaved: () => void;
}) {
  const [editing, setEditing] = useState(false);
  const [category, setCategory] = useState(defaultCategory);
  const [kind, setKind] = useState<TxnKind>(t.kind);
  const [learnRule, setLearnRule] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function save() {
    if (!category) return;
    setBusy(true);
    setError(null);
    try {
      await recategorizeTransaction(t.txnId, { category, kind, learnRule });
      setEditing(false);
      onSaved();
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <tr>
      <td>{t.txnDate}</td>
      <td>{t.merchant}</td>
      <td className={t.amount > 0 ? "income" : ""}>
        {t.amount > 0 ? "+" : ""}
        {t.amount.toFixed(2)} {t.currency}
      </td>
      <td>
        {editing ? (
          <>
            <div className="row">
              <select
                value={category}
                onChange={(e) => setCategory(e.target.value)}
              >
                <option value="" disabled>
                  Choose category
                </option>
                {categories.map((c) => (
                  <option key={c.name} value={c.name}>
                    {c.label}
                  </option>
                ))}
              </select>
              <select
                value={kind}
                onChange={(e) => setKind(e.target.value as TxnKind)}
              >
                {KINDS.map((k) => (
                  <option key={k} value={k}>
                    {KIND_LABELS[k]}
                  </option>
                ))}
              </select>
              <label>
                <input
                  type="checkbox"
                  checked={learnRule}
                  onChange={(e) => setLearnRule(e.target.checked)}
                />
                remember this merchant
              </label>
              <button disabled={busy || !category} onClick={save}>
                {busy ? "Saving..." : "Save"}
              </button>
              <button
                className="link"
                disabled={busy}
                onClick={() => setEditing(false)}
              >
                Cancel
              </button>
            </div>
            {error && <p className="error">{error}</p>}
          </>
        ) : (
          <button className="link" onClick={() => setEditing(true)}>
            Move
          </button>
        )}
      </td>
    </tr>
  );
}

export function StatsPage() {
  const [mode, setMode] = useState<Mode>("month");
  const [month, setMonth] = useState(currentMonth());
  const [from, setFrom] = useState(today());
  const [to, setTo] = useState(today());
  const [report, setReport] = useState<PeriodReport | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [categories, setCategories] = useState<CategoryOption[]>([]);

  const [selectedCategory, setSelectedCategory] = useState<string | null>(null);
  const [categoryTxns, setCategoryTxns] = useState<CategoryTransaction[]>([]);
  const [txnsBusy, setTxnsBusy] = useState(false);
  const [txnsError, setTxnsError] = useState<string | null>(null);
  const [loadedRange, setLoadedRange] = useState<{
    from: string;
    to: string;
  } | null>(null);

  useEffect(() => {
    getCategories()
      .then(setCategories)
      .catch(() => setCategories([]));
  }, []);

  async function load(keepSelection = false) {
    const range = mode === "month" ? monthToRange(month) : { from, to };
    if (range.from > range.to) {
      setError("The start date must be before the end date.");
      return;
    }
    setBusy(true);
    setError(null);
    if (!keepSelection) setSelectedCategory(null);
    try {
      setReport(await getStats(range.from, range.to));
      setLoadedRange(range);
      if (keepSelection && selectedCategory) {
        setTxnsBusy(true);
        setTxnsError(null);
        try {
          setCategoryTxns(await fetchBucket(selectedCategory, range));
        } catch (e) {
          setTxnsError((e as Error).message);
        } finally {
          setTxnsBusy(false);
        }
      }
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
      setCategoryTxns(await fetchBucket(category, loadedRange));
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
        <button disabled={busy} onClick={() => load()}>
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
              <tr
                className="clickable-row"
                onClick={() => selectCategory(TRANSFERS)}
              >
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
              <h3>
                {selectedCategory === TRANSFERS
                  ? "Internal transfer"
                  : selectedCategory}{" "}
                transactions
              </h3>
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
                      <th>Actions</th>
                    </tr>
                  </thead>
                  <tbody>
                    {categoryTxns.map((t) => (
                      <TxnRow
                        key={t.txnId}
                        t={t}
                        categories={categories}
                        defaultCategory={
                          selectedCategory === TRANSFERS ? "" : selectedCategory
                        }
                        onSaved={() => load(true)}
                      />
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
