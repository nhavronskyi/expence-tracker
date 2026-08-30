package pl.havronskyi.finance.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pl.havronskyi.finance.domain.Txn;
import pl.havronskyi.finance.domain.TxnKind;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface TxnRepository extends JpaRepository<Txn, Long> {

    Optional<Txn> findByDedupKey(String dedupKey);

    boolean existsByDedupKey(String dedupKey);

    List<Txn> findByTxnDateBetween(LocalDate from, LocalDate to);

    /** Candidates for pairing the other leg of an internal transfer. */
    @Query("""
           select t from Txn t
           where t.transferGroup is null
             and t.amountMinor = :amount
             and t.accountId <> :accountId
             and t.txnDate between :from and :to
           """)
    List<Txn> findTransferCandidates(@Param("amount") long amount,
                                     @Param("accountId") Long accountId,
                                     @Param("from") LocalDate from,
                                     @Param("to") LocalDate to);

    List<Txn> findByKindAndTxnDateBetween(TxnKind kind, LocalDate from, LocalDate to);
}
