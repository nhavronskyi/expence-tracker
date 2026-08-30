import { useEffect, useState } from "react";
import { getCategories, getOpenReviews, resolveReview } from "../api";
import type {
  Category,
  CategoryOption,
  ReviewCard,
  Suggestion,
  TxnKind,
} from "../types";

const KINDS: TxnKind[] = ["EXPENSE", "INCOME", "INTERNAL_TRANSFER", "UNKNOWN"];

function parseSuggestions(raw: string): Suggestion[] {
  try {
    return JSON.parse(raw) as Suggestion[];
  } catch {
    return [];
  }
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
  const [kind, setKind] = useState<TxnKind>("EXPENSE");
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
    <li className="review-item">
      <p className="question">{card.question}</p>

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
              title={s.reason}
            >
              {s.category} ({Math.round(s.confidence * 100)}%)
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
    </li>
  );
}

export function ReviewPage() {
  const [cards, setCards] = useState<ReviewCard[]>([]);
  const [categories, setCategories] = useState<CategoryOption[]>([]);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    Promise.all([getOpenReviews(), getCategories()])
      .then(([openCards, cats]) => {
        setCards(openCards);
        setCategories(cats);
      })
      .catch((e: Error) => setError(e.message));
  }, []);

  return (
    <section>
      <h2>Review queue ({cards.length})</h2>
      {error && <p className="error">{error}</p>}
      {cards.length === 0 && !error && <p>Nothing to review.</p>}
      <ul className="review-list">
        {cards.map((card) => (
          <ReviewItemRow
            key={card.reviewId}
            card={card}
            categories={categories}
            onResolved={(id) =>
              setCards((prev) => prev.filter((c) => c.reviewId !== id))
            }
          />
        ))}
      </ul>
    </section>
  );
}
