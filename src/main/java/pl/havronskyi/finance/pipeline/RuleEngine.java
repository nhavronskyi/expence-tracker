package pl.havronskyi.finance.pipeline;

import org.springframework.stereotype.Service;
import pl.havronskyi.finance.domain.MerchantRule;
import pl.havronskyi.finance.domain.Txn;
import pl.havronskyi.finance.repo.MerchantRuleRepository;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/** The layer before the LLM. Every rule that matches is a request we don't send. */
@Service
public class RuleEngine {

    private final MerchantRuleRepository rules;

    public RuleEngine(MerchantRuleRepository rules) {
        this.rules = rules;
    }

    public Optional<MerchantRule> match(Txn txn) {
        String norm = txn.getMerchantNorm() == null ? "" : txn.getMerchantNorm();
        if (norm.isBlank()) return Optional.empty();

        List<MerchantRule> all = rules.findAllByOrderByPriorityAsc();
        for (MerchantRule r : all) {
            if (matches(r, norm)) {
                r.setHitCount(r.getHitCount() + 1);
                rules.save(r);
                return Optional.of(r);
            }
        }
        return Optional.empty();
    }

    private boolean matches(MerchantRule r, String norm) {
        return switch (r.getMatchType()) {
            case EXACT  -> norm.equals(r.getPattern());
            case PREFIX -> norm.startsWith(r.getPattern());
            case REGEX  -> Pattern.compile(r.getPattern()).matcher(norm).find();
        };
    }
}
