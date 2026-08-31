package pl.havronskyi.finance.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.havronskyi.finance.domain.Account;

import java.util.List;
import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long> {
    List<Account> findByWorkspaceId(Long workspaceId);

    Optional<Account> findByWorkspaceIdAndIban(Long workspaceId, String iban);

    void deleteAllByWorkspaceId(Long workspaceId);
}
