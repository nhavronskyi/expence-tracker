package pl.havronskyi.finance.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.havronskyi.finance.domain.ImportBatch;

public interface ImportBatchRepository extends JpaRepository<ImportBatch, Long> {
}
