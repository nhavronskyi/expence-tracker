import { useState } from "react";
import { createWorkspace, deleteWorkspace } from "./api";
import type { Workspace } from "./types";

export function WorkspaceSwitcher({
  workspaces,
  currentId,
  onSelect,
  onCreated,
  onDeleted,
}: {
  workspaces: Workspace[];
  currentId: number;
  onSelect: (id: number) => void;
  onCreated: (workspace: Workspace) => void;
  onDeleted: (id: number) => void;
}) {
  const [adding, setAdding] = useState(false);
  const [name, setName] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const current = workspaces.find((w) => w.id === currentId);

  async function submitNew() {
    if (!name.trim()) return;
    setBusy(true);
    setError(null);
    try {
      const workspace = await createWorkspace({ name: name.trim() });
      setName("");
      setAdding(false);
      onCreated(workspace);
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }

  async function remove() {
    if (!current) return;
    if (
      !window.confirm(
        `Delete workspace "${current.name}"? This permanently removes all its accounts, transactions, categories and rules.`,
      )
    ) {
      return;
    }
    setBusy(true);
    setError(null);
    try {
      await deleteWorkspace(current.id);
      onDeleted(current.id);
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="workspace-switcher">
      <select
        value={currentId}
        disabled={busy}
        onChange={(e) => onSelect(Number(e.target.value))}
      >
        {workspaces.map((w) => (
          <option key={w.id} value={w.id}>
            {w.name}
          </option>
        ))}
      </select>

      {adding ? (
        <span className="row">
          <input
            autoFocus
            placeholder="Workspace name"
            value={name}
            onChange={(e) => setName(e.target.value)}
            onKeyDown={(e) => e.key === "Enter" && submitNew()}
          />
          <button disabled={busy || !name.trim()} onClick={submitNew}>
            {busy ? "Adding..." : "Add"}
          </button>
          <button
            className="link"
            disabled={busy}
            onClick={() => {
              setAdding(false);
              setName("");
            }}
          >
            Cancel
          </button>
        </span>
      ) : (
        <button className="link" onClick={() => setAdding(true)}>
          + New
        </button>
      )}

      <button
        className="link"
        disabled={busy || workspaces.length <= 1}
        onClick={remove}
      >
        Delete
      </button>

      {error && <p className="error">{error}</p>}
    </div>
  );
}
