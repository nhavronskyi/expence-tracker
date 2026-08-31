package pl.havronskyi.finance.web;

public record UpdateCategoryRequest(String label, String definition, boolean active) {
}
