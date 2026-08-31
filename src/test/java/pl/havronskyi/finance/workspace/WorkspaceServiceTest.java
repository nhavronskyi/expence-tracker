package pl.havronskyi.finance.workspace;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import pl.havronskyi.finance.domain.Category;
import pl.havronskyi.finance.domain.ImportBatch;
import pl.havronskyi.finance.domain.Workspace;
import pl.havronskyi.finance.repo.AccountRepository;
import pl.havronskyi.finance.repo.CategoryRepository;
import pl.havronskyi.finance.repo.ImportBatchRepository;
import pl.havronskyi.finance.repo.MerchantRuleRepository;
import pl.havronskyi.finance.repo.RawTransactionRepository;
import pl.havronskyi.finance.repo.ReviewItemRepository;
import pl.havronskyi.finance.repo.TxnRepository;
import pl.havronskyi.finance.repo.WorkspaceRepository;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkspaceServiceTest {

    private static ImportBatch batch(long id) {
        ImportBatch b = new ImportBatch();
        ReflectionTestUtils.setField(b, "id", id);
        return b;
    }

    /**
     * The order matters: each step must run before the table it references is emptied
     * (review_item -> txn -> raw_transaction -> import_batch, then the leaf tables, then
     * the workspace row itself) - the same dependency order proven out in
     * ImportService.clearTransactionData.
     */
    @Test
    void deletesChildrenBeforeParents() {
        WorkspaceRepository workspaces = mock(WorkspaceRepository.class);
        when(workspaces.existsById(1L)).thenReturn(true);
        AccountRepository accounts = mock(AccountRepository.class);
        CategoryRepository categories = mock(CategoryRepository.class);
        MerchantRuleRepository rules = mock(MerchantRuleRepository.class);
        ImportBatchRepository batches = mock(ImportBatchRepository.class);
        when(batches.findAllByWorkspaceId(1L)).thenReturn(List.of(batch(10L), batch(11L)));
        RawTransactionRepository raws = mock(RawTransactionRepository.class);
        TxnRepository txns = mock(TxnRepository.class);
        ReviewItemRepository reviews = mock(ReviewItemRepository.class);

        WorkspaceService service = new WorkspaceService(workspaces, accounts, categories, rules, batches, raws,
                txns, reviews);
        service.delete(1L);

        InOrder order = inOrder(reviews, txns, raws, batches, accounts, categories, rules, workspaces);
        order.verify(reviews).deleteAllByWorkspaceId(1L);
        order.verify(txns).deleteAllByWorkspaceId(1L);
        order.verify(raws).deleteAllByBatchIdIn(anyList());
        order.verify(batches).deleteAllByWorkspaceId(1L);
        order.verify(accounts).deleteAllByWorkspaceId(1L);
        order.verify(categories).deleteAllByWorkspaceId(1L);
        order.verify(rules).deleteAllByWorkspaceId(1L);
        order.verify(workspaces).deleteById(1L);
    }

    @Test
    void createSeedsTheDefaultCategorySet() {
        WorkspaceRepository workspaces = mock(WorkspaceRepository.class);
        when(workspaces.save(any())).thenAnswer(invocation -> {
            Workspace w = invocation.getArgument(0);
            ReflectionTestUtils.setField(w, "id", 7L);
            return w;
        });
        CategoryRepository categories = mock(CategoryRepository.class);

        WorkspaceService service = new WorkspaceService(workspaces, mock(AccountRepository.class), categories,
                mock(MerchantRuleRepository.class), mock(ImportBatchRepository.class),
                mock(RawTransactionRepository.class), mock(TxnRepository.class), mock(ReviewItemRepository.class));

        Workspace created = service.create("Personal");

        assertEquals(7L, created.getId());
        ArgumentCaptor<Category> captor = ArgumentCaptor.forClass(Category.class);
        verify(categories, times(17)).save(captor.capture());
        Set<String> codes = captor.getAllValues().stream().map(Category::getCode).collect(Collectors.toSet());
        assertTrue(codes.contains("FRIDGE"));
        assertTrue(codes.contains("SAVINGS"));
        assertTrue(codes.stream().noneMatch(c -> c.equals("TAX")));
        captor.getAllValues().forEach(c -> assertEquals(7L, c.getWorkspaceId()));
    }

    @Test
    void deletingUnknownWorkspaceThrows() {
        WorkspaceRepository workspaces = mock(WorkspaceRepository.class);
        when(workspaces.existsById(99L)).thenReturn(false);

        WorkspaceService service = new WorkspaceService(workspaces, mock(AccountRepository.class),
                mock(CategoryRepository.class), mock(MerchantRuleRepository.class),
                mock(ImportBatchRepository.class), mock(RawTransactionRepository.class), mock(TxnRepository.class),
                mock(ReviewItemRepository.class));

        assertThrows(IllegalArgumentException.class, () -> service.delete(99L));
    }
}
