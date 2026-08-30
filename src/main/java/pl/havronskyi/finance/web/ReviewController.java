package pl.havronskyi.finance.web;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pl.havronskyi.finance.review.ResolveRequest;
import pl.havronskyi.finance.review.ReviewCard;
import pl.havronskyi.finance.review.ReviewService;

import java.util.List;

@RestController
@RequestMapping("/api/review")
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @GetMapping
    public List<ReviewCard> open() {
        return reviewService.open();
    }

    @PostMapping("/{id}")
    public ResponseEntity<Void> resolve(@PathVariable Long id, @RequestBody ResolveRequest request) {
        reviewService.resolve(id, request);
        return ResponseEntity.noContent().build();
    }
}
