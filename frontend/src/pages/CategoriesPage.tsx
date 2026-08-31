import { useEffect, useState } from "react";
import { createCategory, getCategories, updateCategory } from "../api";
import type { CategoryOption } from "../types";

function NewCategoryForm({
  onCreated,
}: {
  onCreated: (category: CategoryOption) => void;
}) {
  const [label, setLabel] = useState("");
  const [definition, setDefinition] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function submit() {
    if (!label.trim()) return;
    setBusy(true);
    setError(null);
    try {
      const category = await createCategory({
        label: label.trim(),
        definition: definition.trim(),
      });
      setLabel("");
      setDefinition("");
      onCreated(category);
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="new-category">
      <div className="row">
        <input
          placeholder="Label (e.g. Hobby)"
          value={label}
          onChange={(e) => setLabel(e.target.value)}
        />
        <input
          placeholder="Definition (goes into the LLM prompt)"
          value={definition}
          onChange={(e) => setDefinition(e.target.value)}
        />
        <button disabled={busy || !label.trim()} onClick={submit}>
          {busy ? "Adding..." : "Add category"}
        </button>
      </div>
      {error && <p className="error">{error}</p>}
    </div>
  );
}

function CategoryRow({
  category,
  onSaved,
}: {
  category: CategoryOption;
  onSaved: (category: CategoryOption) => void;
}) {
  const [editing, setEditing] = useState(false);
  const [label, setLabel] = useState(category.label);
  const [definition, setDefinition] = useState(category.definition);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  function startEdit() {
    setLabel(category.label);
    setDefinition(category.definition);
    setError(null);
    setEditing(true);
  }

  async function save() {
    if (!label.trim()) return;
    setBusy(true);
    setError(null);
    try {
      const updated = await updateCategory(category.id, {
        label: label.trim(),
        definition: definition.trim(),
        active: category.active,
      });
      onSaved(updated);
      setEditing(false);
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }

  async function toggleActive() {
    if (
      category.active &&
      !window.confirm(
        `Deactivate "${category.label}"? It won't be offered for new transactions.`,
      )
    ) {
      return;
    }
    setBusy(true);
    setError(null);
    try {
      const updated = await updateCategory(category.id, {
        label: category.label,
        definition: category.definition,
        active: !category.active,
      });
      onSaved(updated);
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }

  if (editing) {
    return (
      <tr>
        <td>{category.name}</td>
        <td>
          <input value={label} onChange={(e) => setLabel(e.target.value)} />
        </td>
        <td>
          <input
            value={definition}
            onChange={(e) => setDefinition(e.target.value)}
          />
        </td>
        <td>{category.active ? "Active" : "Inactive"}</td>
        <td>
          <button disabled={busy || !label.trim()} onClick={save}>
            {busy ? "Saving..." : "Save"}
          </button>
          <button
            className="link"
            disabled={busy}
            onClick={() => setEditing(false)}
          >
            Cancel
          </button>
          {error && <p className="error">{error}</p>}
        </td>
      </tr>
    );
  }

  return (
    <tr>
      <td>{category.name}</td>
      <td>{category.label}</td>
      <td>{category.definition || "—"}</td>
      <td>{category.active ? "Active" : "Inactive"}</td>
      <td>
        <button className="link" disabled={busy} onClick={startEdit}>
          Edit
        </button>
        <button className="link" disabled={busy} onClick={toggleActive}>
          {category.active ? "Deactivate" : "Reactivate"}
        </button>
        {error && <p className="error">{error}</p>}
      </td>
    </tr>
  );
}

export function CategoriesPage() {
  const [categories, setCategories] = useState<CategoryOption[]>([]);
  const [showInactive, setShowInactive] = useState(false);
  const [error, setError] = useState<string | null>(null);

  function load() {
    getCategories(true)
      .then(setCategories)
      .catch((e: Error) => setError(e.message));
  }

  useEffect(load, []);

  function handleSaved(updated: CategoryOption) {
    setCategories((prev) =>
      prev
        .map((c) => (c.id === updated.id ? updated : c))
        .sort((a, b) => a.label.localeCompare(b.label)),
    );
  }

  function handleCreated(category: CategoryOption) {
    setCategories((prev) =>
      [...prev, category].sort((a, b) => a.label.localeCompare(b.label)),
    );
  }

  const visible = showInactive
    ? categories
    : categories.filter((c) => c.active);

  return (
    <section>
      <h2>Categories</h2>

      {error && <p className="error">{error}</p>}

      {visible.length > 0 && (
        <table className="summary">
          <thead>
            <tr>
              <th>Code</th>
              <th>Label</th>
              <th>Definition</th>
              <th>Status</th>
              <th>Actions</th>
            </tr>
          </thead>
          <tbody>
            {visible.map((c) => (
              <CategoryRow key={c.id} category={c} onSaved={handleSaved} />
            ))}
          </tbody>
        </table>
      )}

      <label className="row">
        <input
          type="checkbox"
          checked={showInactive}
          onChange={(e) => setShowInactive(e.target.checked)}
        />
        Show inactive categories
      </label>

      <NewCategoryForm onCreated={handleCreated} />
    </section>
  );
}
