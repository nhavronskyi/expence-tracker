package pl.havronskyi.finance.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.havronskyi.finance.domain.ReviewItem;
import pl.havronskyi.finance.domain.ReviewStatus;

import java.util.List;

public interface ReviewItemRepository extends JpaRepository<ReviewItem, Long> {
    List<ReviewItem> findByWorkspaceIdAndStatusOrderByIdAsc(Long workspaceId, ReviewStatus status);

    void deleteAllByWorkspaceId(Long workspaceId);
}
