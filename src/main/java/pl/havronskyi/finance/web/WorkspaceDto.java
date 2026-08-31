package pl.havronskyi.finance.web;

import java.time.Instant;

public record WorkspaceDto(Long id, String name, Instant createdAt) {
}
