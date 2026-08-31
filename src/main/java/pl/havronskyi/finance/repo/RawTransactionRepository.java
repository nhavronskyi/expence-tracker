package pl.havronskyi.finance.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.havronskyi.finance.domain.RawTransaction;

import java.util.Collection;

public interface RawTransactionRepository extends JpaRepository<RawTransaction, Long> {
    void deleteAllByBatchIdIn(Collection<Long> batchIds);
}
