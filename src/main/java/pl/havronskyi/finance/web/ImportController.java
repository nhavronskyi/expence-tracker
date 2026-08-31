package pl.havronskyi.finance.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import pl.havronskyi.finance.domain.Account;
import pl.havronskyi.finance.pipeline.ImportJobRegistry;
import pl.havronskyi.finance.pipeline.ImportJobStatus;
import pl.havronskyi.finance.pipeline.ImportService;
import pl.havronskyi.finance.repo.AccountRepository;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api/import")
public class ImportController {

    private final ImportService importService;
    private final ImportJobRegistry jobRegistry;
    private final AccountRepository accounts;

    public ImportController(ImportService importService, ImportJobRegistry jobRegistry, AccountRepository accounts) {
        this.importService = importService;
        this.jobRegistry = jobRegistry;
        this.accounts = accounts;
    }

    @PostMapping(consumes = "multipart/form-data")
    public ResponseEntity<Map<String, String>> upload(@RequestHeader("X-Workspace-Id") Long workspaceId,
                                                       @RequestParam Long accountId,
                                                       @RequestParam("file") MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        Account account = accounts.findById(accountId).orElse(null);
        if (account == null || !account.getWorkspaceId().equals(workspaceId)) {
            return ResponseEntity.badRequest().build();
        }
        String jobId = importService.startImport(workspaceId, accountId, file.getOriginalFilename(),
                file.getBytes());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of("jobId", jobId));
    }

    @GetMapping("/{jobId}/status")
    public ResponseEntity<ImportJobStatus> status(@PathVariable String jobId) {
        return jobRegistry.get(jobId)
                .map(job -> ResponseEntity.ok(ImportJobStatus.of(job)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/{jobId}/cancel")
    public ResponseEntity<Void> cancel(@PathVariable String jobId) {
        return jobRegistry.get(jobId)
                .map(job -> {
                    job.requestCancel();
                    return ResponseEntity.ok().<Void>build();
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/clear")
    public ResponseEntity<Void> clear(@RequestHeader("X-Workspace-Id") Long workspaceId) {
        importService.clearTransactionData(workspaceId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/backfill-fx")
    public ResponseEntity<Map<String, Integer>> backfillFx(@RequestHeader("X-Workspace-Id") Long workspaceId) {
        int fixed = importService.backfillMissingFxAmounts(workspaceId);
        return ResponseEntity.ok(Map.of("fixed", fixed));
    }

    @PostMapping("/renormalize-merchants")
    public ResponseEntity<Map<String, Integer>> renormalizeMerchants(
            @RequestHeader("X-Workspace-Id") Long workspaceId) {
        int changed = importService.renormalizeMerchants(workspaceId);
        return ResponseEntity.ok(Map.of("changed", changed));
    }

    @PostMapping("/reclassify-transfers")
    public ResponseEntity<Map<String, Integer>> reclassifyTransfers(
            @RequestHeader("X-Workspace-Id") Long workspaceId) {
        int reclassified = importService.reclassifyTransfers(workspaceId);
        return ResponseEntity.ok(Map.of("reclassified", reclassified));
    }
}
