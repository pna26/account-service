package store.account;

import java.util.List;
import java.util.Optional;

import org.springframework.data.repository.CrudRepository;

public interface AccountRepository extends CrudRepository<AccountModel, String> {

    List<AccountModel> findAll();

    Optional<AccountModel> findByEmailAndHashPassword(
        String email,
        String hashPassword
    );

}
