package pl.havronskyi.finance.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.havronskyi.finance.domain.Workspace;

public interface WorkspaceRepository extends JpaRepository<Workspace, Long> {
}
