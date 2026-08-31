import { useEffect, useState } from "react";
import { createAccount, getAccounts, updateAccount } from "../api";
import type { Account, AccountScope, AccountType } from "../types";

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
  const [result, setResult] = useState<string | null>(null);

  async function submit() {
    if (!label.trim()) return;
    setBusy(true);
    setError(null);
    setResult(null);
    try {
      const { account, reclassifiedTransfers } = await createAccount({
        label: label.trim(),
        iban: iban.trim(),
        scope,
        type,
        currency,
      });
      setLabel("");
      setIban("");
      onCreated(account);
      if (reclassifiedTransfers > 0) {
        setResult(
          `Reclassified ${reclassifiedTransfers} existing transaction${reclassifiedTransfers === 1 ? "" : "s"} as internal transfers.`,
        );
      }
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
      {result && <p>{result}</p>}
    </div>
  );
}

function AccountRow({
  account,
  onSaved,
}: {
  account: Account;
  onSaved: (account: Account) => void;
}) {
  const [editing, setEditing] = useState(false);
  const [label, setLabel] = useState(account.label);
  const [iban, setIban] = useState(account.iban ?? "");
  const [scope, setScope] = useState<AccountScope>(account.scope);
  const [type, setType] = useState<AccountType>(account.type);
  const [currency, setCurrency] = useState(account.currency);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [result, setResult] = useState<string | null>(null);

  function startEdit() {
    setLabel(account.label);
    setIban(account.iban ?? "");
    setScope(account.scope);
    setType(account.type);
    setCurrency(account.currency);
    setError(null);
    setEditing(true);
  }

  async function save() {
    if (!label.trim()) return;
    setBusy(true);
    setError(null);
    setResult(null);
    try {
      const { account: updated, reclassifiedTransfers } = await updateAccount(
        account.id,
        {
          label: label.trim(),
          iban: iban.trim(),
          scope,
          type,
          currency,
          active: account.active,
        },
      );
      onSaved(updated);
      setEditing(false);
      if (reclassifiedTransfers > 0) {
        setResult(
          `Reclassified ${reclassifiedTransfers} transaction${reclassifiedTransfers === 1 ? "" : "s"} as internal transfers.`,
        );
      }
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }

  async function toggleActive() {
    if (
      account.active &&
      !window.confirm(
        `Deactivate "${account.label}"? It won't be offered when importing new statements.`,
      )
    ) {
      return;
    }
    setBusy(true);
    setError(null);
    try {
      const { account: updated } = await updateAccount(account.id, {
        label: account.label,
        iban: account.iban ?? "",
        scope: account.scope,
        type: account.type,
        currency: account.currency,
        active: !account.active,
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
        <td>
          <input value={label} onChange={(e) => setLabel(e.target.value)} />
        </td>
        <td>
          <input value={iban} onChange={(e) => setIban(e.target.value)} />
        </td>
        <td>
          <select
            value={scope}
            onChange={(e) => setScope(e.target.value as AccountScope)}
          >
            <option value="PERSONAL">Personal</option>
            <option value="BUSINESS">Business</option>
          </select>
        </td>
        <td>
          <select
            value={type}
            onChange={(e) => setType(e.target.value as AccountType)}
          >
            <option value="CURRENT">Current</option>
            <option value="CREDIT_CARD">Credit card</option>
          </select>
        </td>
        <td>
          <input
            className="currency"
            value={currency}
            onChange={(e) => setCurrency(e.target.value)}
          />
        </td>
        <td>{account.active ? "Active" : "Inactive"}</td>
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
      <td>{account.label}</td>
      <td>{account.iban || "—"}</td>
      <td>{account.scope}</td>
      <td>{account.type}</td>
      <td>{account.currency}</td>
      <td>{account.active ? "Active" : "Inactive"}</td>
      <td>
        <button className="link" disabled={busy} onClick={startEdit}>
          Edit
        </button>
        <button className="link" disabled={busy} onClick={toggleActive}>
          {account.active ? "Deactivate" : "Reactivate"}
        </button>
        {error && <p className="error">{error}</p>}
        {result && <p>{result}</p>}
      </td>
    </tr>
  );
}

export function AccountsPage() {
  const [accounts, setAccounts] = useState<Account[]>([]);
  const [showInactive, setShowInactive] = useState(false);
  const [error, setError] = useState<string | null>(null);

  function load() {
    getAccounts(true)
      .then(setAccounts)
      .catch((e: Error) => setError(e.message));
  }

  useEffect(load, []);

  function handleSaved(updated: Account) {
    setAccounts((prev) =>
      prev
        .map((a) => (a.id === updated.id ? updated : a))
        .sort((a, b) => a.label.localeCompare(b.label)),
    );
  }

  function handleCreated(account: Account) {
    setAccounts((prev) =>
      [...prev, account].sort((a, b) => a.label.localeCompare(b.label)),
    );
  }

  const visible = showInactive ? accounts : accounts.filter((a) => a.active);

  return (
    <section>
      <h2>Accounts</h2>

      {error && <p className="error">{error}</p>}

      {visible.length > 0 && (
        <table className="summary">
          <thead>
            <tr>
              <th>Label</th>
              <th>IBAN</th>
              <th>Scope</th>
              <th>Type</th>
              <th>Currency</th>
              <th>Status</th>
              <th>Actions</th>
            </tr>
          </thead>
          <tbody>
            {visible.map((a) => (
              <AccountRow key={a.id} account={a} onSaved={handleSaved} />
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
        Show inactive accounts
      </label>

      <NewAccountForm onCreated={handleCreated} />
    </section>
  );
}
