package pl.havronskyi.finance.llm;

import java.util.List;

/** The model's response for one transaction: best type + alternatives for the review queue. */
public record CategorySuggestion(long txnId, List<Suggestion> ranked) {

    public Suggestion best() {
        return ranked.isEmpty() ? null : ranked.get(0);
    }
}
