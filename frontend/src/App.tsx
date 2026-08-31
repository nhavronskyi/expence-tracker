import { useEffect, useState } from "react";
import {
  createWorkspace,
  getTransactionCount,
  getWorkspaces,
  setWorkspaceId,
} from "./api";
import { AccountsPage } from "./pages/AccountsPage";
import { CategoriesPage } from "./pages/CategoriesPage";
import { ImportPage } from "./pages/ImportPage";
import { ReviewPage } from "./pages/ReviewPage";
import { StatsPage } from "./pages/StatsPage";
import { WorkspaceSwitcher } from "./WorkspaceSwitcher";
import type { Workspace } from "./types";

type Tab = "import" | "accounts" | "categories" | "review" | "stats";

const TABS: { id: Tab; label: string }[] = [
  { id: "import", label: "Import" },
  { id: "accounts", label: "Accounts" },
  { id: "categories", label: "Categories" },
  { id: "review", label: "Review" },
  { id: "stats", label: "Stats" },
];

const LAST_WORKSPACE_KEY = "financeLastWorkspaceId";

function NewWorkspaceForm({
  onCreated,
}: {
  onCreated: (workspace: Workspace) => void;
}) {
  const [name, setName] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function submit() {
    if (!name.trim()) return;
    setBusy(true);
    setError(null);
    try {
      const workspace = await createWorkspace({ name: name.trim() });
      onCreated(workspace);
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="app">
      <main>
        <section>
          <h2>Create your first workspace</h2>
          <p>
            A workspace holds its own bank accounts, transactions, categories
            and learned rules.
          </p>
          <div className="row">
            <input
              autoFocus
              placeholder="e.g. Personal"
              value={name}
              onChange={(e) => setName(e.target.value)}
              onKeyDown={(e) => e.key === "Enter" && submit()}
            />
            <button disabled={busy || !name.trim()} onClick={submit}>
              {busy ? "Creating..." : "Create workspace"}
            </button>
          </div>
          {error && <p className="error">{error}</p>}
        </section>
      </main>
    </div>
  );
}

export default function App() {
  const [tab, setTab] = useState<Tab>("import");
  const [txnCount, setTxnCount] = useState<number | null>(null);
  const [refreshCount, setRefreshCount] = useState(0);
  const [workspaces, setWorkspaces] = useState<Workspace[] | null>(null);
  const [currentWorkspace, setCurrentWorkspace] = useState<number | null>(null);

  useEffect(() => {
    getWorkspaces().then((list) => {
      setWorkspaces(list);
      if (list.length === 0) return;
      const stored = Number(localStorage.getItem(LAST_WORKSPACE_KEY));
      const initial = list.some((w) => w.id === stored) ? stored : list[0].id;
      setWorkspaceId(initial);
      setCurrentWorkspace(initial);
    });
  }, []);

  useEffect(() => {
    if (currentWorkspace === null) return;
    getTransactionCount()
      .then(({ total }) => setTxnCount(total))
      .catch(() => setTxnCount(null));
  }, [tab, refreshCount, currentWorkspace]);

  function selectWorkspace(id: number) {
    localStorage.setItem(LAST_WORKSPACE_KEY, String(id));
    setWorkspaceId(id);
    setCurrentWorkspace(id);
    setTab("import");
  }

  function handleWorkspaceCreated(workspace: Workspace) {
    setWorkspaces((prev) => [...(prev ?? []), workspace]);
    selectWorkspace(workspace.id);
  }

  function handleWorkspaceDeleted(id: number) {
    setWorkspaces((prev) => {
      const remaining = (prev ?? []).filter((w) => w.id !== id);
      if (currentWorkspace === id) {
        const next = remaining[0]?.id ?? null;
        setWorkspaceId(next);
        setCurrentWorkspace(next);
      }
      return remaining;
    });
  }

  if (workspaces === null) {
    return null;
  }

  if (workspaces.length === 0 || currentWorkspace === null) {
    return <NewWorkspaceForm onCreated={handleWorkspaceCreated} />;
  }

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
        <WorkspaceSwitcher
          workspaces={workspaces}
          currentId={currentWorkspace}
          onSelect={selectWorkspace}
          onCreated={handleWorkspaceCreated}
          onDeleted={handleWorkspaceDeleted}
        />
        {txnCount !== null && (
          <span className="txn-count">{txnCount} transactions</span>
        )}
      </header>
      <main key={currentWorkspace}>
        {tab === "import" && (
          <ImportPage onDataChanged={() => setRefreshCount((n) => n + 1)} />
        )}
        {tab === "accounts" && <AccountsPage />}
        {tab === "categories" && <CategoriesPage />}
        {tab === "review" && <ReviewPage />}
        {tab === "stats" && <StatsPage />}
      </main>
    </div>
  );
}
