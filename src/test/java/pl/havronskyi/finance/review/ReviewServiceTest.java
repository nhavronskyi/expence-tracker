package pl.havronskyi.finance.review;

import org.junit.jupiter.api.Test;
import pl.havronskyi.finance.domain.CategorySource;
import pl.havronskyi.finance.domain.Txn;
import pl.havronskyi.finance.domain.TxnKind;
import pl.havronskyi.finance.repo.CategoryRepository;
import pl.havronskyi.finance.repo.MerchantRuleRepository;
import pl.havronskyi.finance.repo.ReviewItemRepository;
import pl.havronskyi.finance.repo.TxnRepository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReviewServiceTest {

    private static Txn txn(long id, TxnKind kind, UUID transferGroup) {
        Txn t = new Txn();
        t.setAccountId(1L);
        t.setTxnDate(LocalDate.of(2026, 8, 5));
        t.setAmountMinor(-3180);
        t.setCurrency("PLN");
        t.setKind(kind);
        t.setTransferGroup(transferGroup);
        t.setDedupKey("txn-" + id);
        return t;
    }

    private static ReviewService service(TxnRepository txns, CategoryRepository categories) {
        return new ReviewService(mock(ReviewItemRepository.class), txns,
                mock(MerchantRuleRepository.class), categories);
    }

    /**
     * The direct-recategorize path (from the Stats drill-down) touches only the transaction -
     * unlike resolve(), there's no ReviewItem involved at all.
     */
    @Test
    void recategorizeSetsCategoryAndKindDirectly() {
        Txn t = txn(1L, TxnKind.EXPENSE, null);
        TxnRepository txns = mock(TxnRepository.class);
        when(txns.findById(1L)).thenReturn(Optional.of(t));

        CategoryRepository categories = mock(CategoryRepository.class);
        when(categories.existsByCodeIgnoreCaseAndActiveTrue("HOBBY")).thenReturn(true);

        ReviewItemRepository reviews = mock(ReviewItemRepository.class);
        ReviewService service = new ReviewService(reviews, txns, mock(MerchantRuleRepository.class), categories);

        service.recategorize(1L, new ResolveRequest("hobby", TxnKind.EXPENSE, false));

        assertEquals("HOBBY", t.getCategory());
        assertEquals(TxnKind.EXPENSE, t.getKind());
        assertEquals(CategorySource.MANUAL, t.getCategorySource());
        assertFalse(t.isNeedsReview());
        verify(reviews, never()).findById(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void movingOutOfInternalTransferClearsTransferGroup() {
        Txn t = txn(2L, TxnKind.INTERNAL_TRANSFER, UUID.randomUUID());
        TxnRepository txns = mock(TxnRepository.class);
        when(txns.findById(2L)).thenReturn(Optional.of(t));

        CategoryRepository categories = mock(CategoryRepository.class);
        when(categories.existsByCodeIgnoreCaseAndActiveTrue("HOBBY")).thenReturn(true);

        ReviewService service = service(txns, categories);
        service.recategorize(2L, new ResolveRequest("HOBBY", TxnKind.EXPENSE, false));

        assertEquals(TxnKind.EXPENSE, t.getKind());
        assertNull(t.getTransferGroup());
    }

    @Test
    void recategorizingUnknownTxnThrows() {
        TxnRepository txns = mock(TxnRepository.class);
        when(txns.findById(99L)).thenReturn(Optional.empty());

        ReviewService service = service(txns, mock(CategoryRepository.class));

        assertThrows(IllegalArgumentException.class,
                () -> service.recategorize(99L, new ResolveRequest("HOBBY", TxnKind.EXPENSE, false)));
    }
}
