package pl.havronskyi.finance.web;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.havronskyi.finance.domain.Workspace;
import pl.havronskyi.finance.workspace.WorkspaceService;

import java.util.List;

/**
 * The one endpoint group that doesn't take an X-Workspace-Id header - it's how the
 * frontend discovers, creates and deletes workspaces in the first place.
 */
@RestController
@RequestMapping("/api/workspaces")
public class WorkspaceController {

    private final WorkspaceService workspaceService;

    public WorkspaceController(WorkspaceService workspaceService) {
        this.workspaceService = workspaceService;
    }

    @GetMapping
    public List<WorkspaceDto> list() {
        return workspaceService.list().stream().map(this::toDto).toList();
    }

    @PostMapping
    public ResponseEntity<WorkspaceDto> create(@RequestBody NewWorkspaceRequest req) {
        if (req.name() == null || req.name().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        Workspace w = workspaceService.create(req.name().trim());
        return ResponseEntity.ok(toDto(w));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        workspaceService.delete(id);
        return ResponseEntity.noContent().build();
    }

    private WorkspaceDto toDto(Workspace w) {
        return new WorkspaceDto(w.getId(), w.getName(), w.getCreatedAt());
    }
}
