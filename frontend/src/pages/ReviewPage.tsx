import { useEffect, useState } from "react";
import {
  createCategory,
  getCategories,
  getOpenReviews,
  resolveReview,
} from "../api";
import type {
  Category,
  CategoryOption,
  ReviewCard,
  Suggestion,
  TxnKind,
} from "../types";

const KINDS: TxnKind[] = ["EXPENSE", "INCOME", "INTERNAL_TRANSFER", "UNKNOWN"];
const KIND_LABELS: Record<TxnKind, string> = {
  EXPENSE: "Expense",
  INCOME: "Income",
  INTERNAL_TRANSFER: "Internal transfer",
  UNKNOWN: "Unknown",
};

function parseSuggestions(raw: string): Suggestion[] {
  try {
    return JSON.parse(raw) as Suggestion[];
  } catch {
    return [];
  }
}

function groupByMerchant(cards: ReviewCard[]): [string, ReviewCard[]][] {
  const groups = new Map<string, ReviewCard[]>();
  for (const card of cards) {
    const list = groups.get(card.merchant);
    if (list) {
      list.push(card);
    } else {
      groups.set(card.merchant, [card]);
    }
  }
  return [...groups.entries()];
}

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
    <div className="new-account">
      <div className="row">
        <input
          placeholder="Category name"
          value={label}
          onChange={(e) => setLabel(e.target.value)}
        />
        <input
          placeholder="Description (helps the LLM classify it)"
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

function ReviewItemRow({
  card,
  categories,
  onResolved,
}: {
  card: ReviewCard;
  categories: CategoryOption[];
  onResolved: (reviewId: number) => void;
}) {
  const suggestions = parseSuggestions(card.suggestionsJson);
  const [category, setCategory] = useState<Category | "">(
    suggestions[0]?.category ?? "",
  );
  const [kind, setKind] = useState<TxnKind>(card.kind);
  const [learnRule, setLearnRule] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function submit() {
    if (!category) return;
    setBusy(true);
    setError(null);
    try {
      await resolveReview(card.reviewId, { category, kind, learnRule });
      onResolved(card.reviewId);
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="review-item">
      <p className="question">
        {card.merchant}
        {card.merchant !== card.description && card.description
          ? ` (${card.description})`
          : ""}
        {", "}
        <span className={card.kind === "INCOME" ? "amount income" : "amount"}>
          {card.amount.toFixed(2)} {card.currency}
        </span>
        {", "}
        {card.txnDate}
      </p>

      {suggestions.length > 0 && (
        <div className="suggestions">
          {suggestions.map((s) => (
            <button
              key={s.category}
              type="button"
              className={
                s.category === category ? "chip chip-selected" : "chip"
              }
              onClick={() => setCategory(s.category)}
            >
              <strong>{s.category}</strong> ({Math.round(s.confidence * 100)}%)
              {s.reason && <span className="reason"> — {s.reason}</span>}
            </button>
          ))}
        </div>
      )}

      <div className="row">
        <select
          value={category}
          onChange={(e) => setCategory(e.target.value as Category)}
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
              {k}
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
        <button disabled={busy || !category} onClick={submit}>
          {busy ? "Saving..." : "Resolve"}
        </button>
      </div>

      {error && <p className="error">{error}</p>}
    </div>
  );
}

function MerchantGroup({
  merchant,
  cards,
  categories,
  onResolved,
}: {
  merchant: string;
  cards: ReviewCard[];
  categories: CategoryOption[];
  onResolved: (reviewId: number) => void;
}) {
  if (cards.length === 1) {
    return (
      <ReviewItemRow
        card={cards[0]}
        categories={categories}
        onResolved={onResolved}
      />
    );
  }

  return (
    <details className="merchant-folder">
      <summary>
        {merchant} ({cards.length})
      </summary>
      <div className="review-list">
        {cards.map((card) => (
          <ReviewItemRow
            key={card.reviewId}
            card={card}
            categories={categories}
            onResolved={onResolved}
          />
        ))}
      </div>
    </details>
  );
}

export function ReviewPage() {
  const [cards, setCards] = useState<ReviewCard[]>([]);
  const [categories, setCategories] = useState<CategoryOption[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [showNewCategory, setShowNewCategory] = useState(false);

  useEffect(() => {
    Promise.all([getOpenReviews(), getCategories()])
      .then(([openCards, cats]) => {
        setCards(openCards);
        setCategories(cats);
      })
      .catch((e: Error) => setError(e.message));
  }, []);

  function handleCategoryCreated(category: CategoryOption) {
    setShowNewCategory(false);
    setCategories((prev) =>
      [...prev, category].sort((a, b) => a.label.localeCompare(b.label)),
    );
  }

  function handleResolved(reviewId: number) {
    setCards((prev) => prev.filter((c) => c.reviewId !== reviewId));
  }

  return (
    <section>
      <div className="row">
        <h2>Review queue ({cards.length})</h2>
        <button className="link" onClick={() => setShowNewCategory((v) => !v)}>
          {showNewCategory ? "Cancel" : "+ Add category"}
        </button>
      </div>

      {showNewCategory && <NewCategoryForm onCreated={handleCategoryCreated} />}

      {error && <p className="error">{error}</p>}
      {cards.length === 0 && !error && <p>Nothing to review.</p>}

      {KINDS.map((kind) => {
        const kindCards = cards.filter((c) => c.kind === kind);
        if (kindCards.length === 0) return null;
        const groups = groupByMerchant(kindCards);

        return (
          <div key={kind} className="kind-section">
            <h3 className={kind === "INCOME" ? "income" : ""}>
              {KIND_LABELS[kind]} ({kindCards.length})
            </h3>
            <div className="review-list">
              {groups.map(([merchant, group]) => (
                <MerchantGroup
                  key={merchant}
                  merchant={merchant}
                  cards={group}
                  categories={categories}
                  onResolved={handleResolved}
                />
              ))}
            </div>
          </div>
        );
      })}
    </section>
  );
}
