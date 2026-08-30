package pl.havronskyi.finance.web;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import pl.havronskyi.finance.pipeline.ImportService;
import pl.havronskyi.finance.pipeline.ImportSummary;

import java.io.IOException;

@RestController
@RequestMapping("/api/import")
public class ImportController {

    private final ImportService importService;

    public ImportController(ImportService importService) {
        this.importService = importService;
    }

    @PostMapping(consumes = "multipart/form-data")
    public ResponseEntity<ImportSummary> upload(@RequestParam Long accountId,
                                                @RequestParam("file") MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        ImportSummary summary = importService.importFile(
                accountId, file.getOriginalFilename(), file.getBytes());
        return ResponseEntity.ok(summary);
    }
}
