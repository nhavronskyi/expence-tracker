package pl.havronskyi.finance.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.havronskyi.finance.domain.MatchType;
import pl.havronskyi.finance.domain.MerchantRule;

import java.util.List;
import java.util.Optional;

public interface MerchantRuleRepository extends JpaRepository<MerchantRule, Long> {
    List<MerchantRule> findAllByWorkspaceIdOrderByPriorityAsc(Long workspaceId);

    Optional<MerchantRule> findByWorkspaceIdAndMatchTypeAndPattern(Long workspaceId, MatchType matchType,
                                                                    String pattern);

    void deleteAllByWorkspaceId(Long workspaceId);
}
