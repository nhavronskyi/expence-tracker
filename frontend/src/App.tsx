import { useEffect, useState } from "react";
import { getTransactionCount } from "./api";
import { AccountsPage } from "./pages/AccountsPage";
import { ImportPage } from "./pages/ImportPage";
import { ReviewPage } from "./pages/ReviewPage";
import { StatsPage } from "./pages/StatsPage";

type Tab = "import" | "accounts" | "review" | "stats";

const TABS: { id: Tab; label: string }[] = [
  { id: "import", label: "Import" },
  { id: "accounts", label: "Accounts" },
  { id: "review", label: "Review" },
  { id: "stats", label: "Stats" },
];

export default function App() {
  const [tab, setTab] = useState<Tab>("import");
  const [txnCount, setTxnCount] = useState<number | null>(null);
  const [refreshCount, setRefreshCount] = useState(0);

  useEffect(() => {
    getTransactionCount()
      .then(({ total }) => setTxnCount(total))
      .catch(() => setTxnCount(null));
  }, [tab, refreshCount]);

  return (
    <div className="app">
      <header>
        <h1>Finance</h1>
        <nav>
          {TABS.map((t) => (
            <button
              key={t.id}
              className={tab === t.id ? "tab tab-active" : "tab"}
              onClick={() => setTab(t.id)}
            >
              {t.label}
            </button>
          ))}
        </nav>
        {txnCount !== null && (
          <span className="txn-count">{txnCount} transactions</span>
        )}
      </header>
      <main>
        {tab === "import" && (
          <ImportPage onDataChanged={() => setRefreshCount((n) => n + 1)} />
        )}
        {tab === "accounts" && <AccountsPage />}
        {tab === "review" && <ReviewPage />}
        {tab === "stats" && <StatsPage />}
      </main>
    </div>
  );
}
