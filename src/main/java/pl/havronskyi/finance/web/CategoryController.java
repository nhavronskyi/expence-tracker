package pl.havronskyi.finance.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.havronskyi.finance.domain.Category;

import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api/categories")
public class CategoryController {

    @GetMapping
    public List<CategoryDto> list() {
        return Arrays.stream(Category.values())
                .map(c -> new CategoryDto(c.name(), c.label()))
                .toList();
    }
}
