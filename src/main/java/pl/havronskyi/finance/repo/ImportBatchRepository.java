package pl.havronskyi.finance.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.havronskyi.finance.domain.ImportBatch;

import java.util.List;

public interface ImportBatchRepository extends JpaRepository<ImportBatch, Long> {
    List<ImportBatch> findAllByWorkspaceId(Long workspaceId);

    void deleteAllByWorkspaceId(Long workspaceId);
}
