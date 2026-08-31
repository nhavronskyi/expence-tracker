package pl.havronskyi.finance.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.havronskyi.finance.domain.Category;

import java.util.List;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    List<Category> findByWorkspaceIdOrderByLabelAsc(Long workspaceId);

    List<Category> findByWorkspaceIdAndActiveTrueOrderByLabelAsc(Long workspaceId);

    boolean existsByWorkspaceIdAndCodeIgnoreCase(Long workspaceId, String code);

    boolean existsByWorkspaceIdAndCodeIgnoreCaseAndActiveTrue(Long workspaceId, String code);

    void deleteAllByWorkspaceId(Long workspaceId);
}
