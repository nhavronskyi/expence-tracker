package pl.havronskyi.finance.web;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pl.havronskyi.finance.domain.Category;
import pl.havronskyi.finance.repo.CategoryRepository;

import java.util.Comparator;
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
    public List<CategoryDto> list(@RequestHeader("X-Workspace-Id") Long workspaceId,
                                   @RequestParam(defaultValue = "false") boolean includeInactive) {
        List<Category> all = includeInactive
                ? categories.findByWorkspaceIdOrderByLabelAsc(workspaceId)
                : categories.findByWorkspaceIdAndActiveTrueOrderByLabelAsc(workspaceId);
        return all.stream()
                .sorted(Comparator.comparing(Category::getLabel))
                .map(this::toDto)
                .toList();
    }

    @PostMapping
    public ResponseEntity<CategoryDto> create(@RequestHeader("X-Workspace-Id") Long workspaceId,
                                               @RequestBody NewCategoryRequest req) {
        if (req.label() == null || req.label().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        String code = deriveCode(req.label());
        if (code.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        if (categories.existsByWorkspaceIdAndCodeIgnoreCase(workspaceId, code)) {
            return ResponseEntity.status(409).build();
        }

        Category c = new Category();
        c.setWorkspaceId(workspaceId);
        c.setCode(code);
        c.setLabel(req.label().trim());
        c.setDefinition(req.definition() == null ? "" : req.definition().trim());
        c.setActive(true);
        c = categories.save(c);

        return ResponseEntity.ok(toDto(c));
    }

    /**
     * Label and definition are editable, but code is not - it's the stable identifier already
     * stored on every txn.category and merchant_rule.category row, and renaming it out from
     * under them would silently orphan those references. Deactivate/reactivate happens here
     * too, the same "active is just another editable field" pattern AccountController uses -
     * no separate toggle endpoint.
     */
    @PatchMapping("/{id}")
    public ResponseEntity<CategoryDto> update(@RequestHeader("X-Workspace-Id") Long workspaceId,
                                               @PathVariable Long id, @RequestBody UpdateCategoryRequest req) {
        if (req.label() == null || req.label().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        Category c = categories.findById(id).orElse(null);
        if (c == null || !c.getWorkspaceId().equals(workspaceId)) {
            return ResponseEntity.notFound().build();
        }

        c.setLabel(req.label().trim());
        c.setDefinition(req.definition() == null ? "" : req.definition().trim());
        c.setActive(req.active());
        c = categories.save(c);

        return ResponseEntity.ok(toDto(c));
    }

    private CategoryDto toDto(Category c) {
        return new CategoryDto(c.getId(), c.getCode(), c.getLabel(), c.getDefinition(), c.isActive());
    }
}
