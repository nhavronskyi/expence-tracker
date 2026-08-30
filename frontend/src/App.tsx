import { useState } from "react";
import { ImportPage } from "./pages/ImportPage";
import { ReviewPage } from "./pages/ReviewPage";
import { StatsPage } from "./pages/StatsPage";

type Tab = "import" | "review" | "stats";

const TABS: { id: Tab; label: string }[] = [
  { id: "import", label: "Import" },
  { id: "review", label: "Review" },
  { id: "stats", label: "Stats" },
];

export default function App() {
  const [tab, setTab] = useState<Tab>("import");

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
      </header>
      <main>
        {tab === "import" && <ImportPage />}
        {tab === "review" && <ReviewPage />}
        {tab === "stats" && <StatsPage />}
      </main>
    </div>
  );
}
