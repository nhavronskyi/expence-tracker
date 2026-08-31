package pl.havronskyi.finance.web;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.havronskyi.finance.review.ResolveRequest;
import pl.havronskyi.finance.review.ReviewService;

@RestController
@RequestMapping("/api/transactions")
public class TransactionController {

    private final ReviewService reviewService;

    public TransactionController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @PatchMapping("/{id}")
    public ResponseEntity<Void> recategorize(@PathVariable Long id, @RequestBody ResolveRequest request) {
        reviewService.recategorize(id, request);
        return ResponseEntity.noContent().build();
    }
}
