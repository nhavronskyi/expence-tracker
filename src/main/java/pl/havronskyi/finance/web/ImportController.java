package pl.havronskyi.finance.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import pl.havronskyi.finance.pipeline.ImportJobRegistry;
import pl.havronskyi.finance.pipeline.ImportJobStatus;
import pl.havronskyi.finance.pipeline.ImportService;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api/import")
public class ImportController {

    private final ImportService importService;
    private final ImportJobRegistry jobRegistry;

    public ImportController(ImportService importService, ImportJobRegistry jobRegistry) {
        this.importService = importService;
        this.jobRegistry = jobRegistry;
    }

    @PostMapping(consumes = "multipart/form-data")
    public ResponseEntity<Map<String, String>> upload(@RequestParam Long accountId,
                                                       @RequestParam("file") MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        String jobId = importService.startImport(accountId, file.getOriginalFilename(), file.getBytes());
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
    public ResponseEntity<Void> clear() {
        importService.clearTransactionData();
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/backfill-fx")
    public ResponseEntity<Map<String, Integer>> backfillFx() {
        int fixed = importService.backfillMissingFxAmounts();
        return ResponseEntity.ok(Map.of("fixed", fixed));
    }

    @PostMapping("/renormalize-merchants")
    public ResponseEntity<Map<String, Integer>> renormalizeMerchants() {
        int changed = importService.renormalizeMerchants();
        return ResponseEntity.ok(Map.of("changed", changed));
    }

    @PostMapping("/reclassify-transfers")
    public ResponseEntity<Map<String, Integer>> reclassifyTransfers() {
        int reclassified = importService.reclassifyTransfers();
        return ResponseEntity.ok(Map.of("reclassified", reclassified));
    }
}
