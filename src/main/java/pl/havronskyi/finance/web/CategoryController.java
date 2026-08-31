package pl.havronskyi.finance.web;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.havronskyi.finance.domain.Category;
import pl.havronskyi.finance.repo.CategoryRepository;

import java.util.List;

@RestController
@RequestMapping("/api/categories")
public class CategoryController {

    private final CategoryRepository categories;

    public CategoryController(CategoryRepository categories) {
        this.categories = categories;
    }

    private static String deriveCode(String label) {
        return label.trim().toUpperCase()
                .replaceAll("[^A-Z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
    }

    @GetMapping
    public List<CategoryDto> list() {
        return categories.findByActiveTrueOrderByLabelAsc().stream()
                .map(c -> new CategoryDto(c.getCode(), c.getLabel()))
                .toList();
    }

    @PostMapping
    public ResponseEntity<CategoryDto> create(@RequestBody NewCategoryRequest req) {
        if (req.label() == null || req.label().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        String code = deriveCode(req.label());
        if (code.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        if (categories.existsByCodeIgnoreCase(code)) {
            return ResponseEntity.status(409).build();
        }

        Category c = new Category();
        c.setCode(code);
        c.setLabel(req.label().trim());
        c.setDefinition(req.definition() == null ? "" : req.definition().trim());
        c.setActive(true);
        c = categories.save(c);

        return ResponseEntity.ok(new CategoryDto(c.getCode(), c.getLabel()));
    }
}
