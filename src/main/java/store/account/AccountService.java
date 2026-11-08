package store.account;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import store.account.outbox.Outbox;

@Service
public class AccountService {

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private Outbox outbox;

    // conta e evento AccountCreated na mesma transação (transactional outbox):
    // ou os dois são gravados, ou nenhum (ex.: e-mail duplicado desfaz tudo)
    @Transactional
    public Account create(Account account) {

        if (null == account.password()) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST, "Password is empty"
            );
        }

        final String pass = account.password().trim();
        if (pass.length() < 6) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST, "Password must have at least 6 characters"
            );
        }

        if (account.email() == null || account.email().trim().isEmpty()) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST, "Email is empty"
            );
        }

        account.hashPassword(calcHash(pass));

        final Account saved = accountRepository.save(
            new AccountModel(account)
        ).to();

        final AccountEvent event = AccountEvent.created(saved);
        outbox.append(event.eventId(), "account", saved.id(), event.eventType(), event);

        return saved;
    }

    public Account findById(String id) {
        // null quando o id não existe: o AccountResource responde 404
        return accountRepository.findById(id)
            .map(AccountModel::to)
            .orElse(null);
    }

    public void delete(String id) {
        accountRepository.deleteById(id);
    }

    public List<Account> findAll() {
        return accountRepository.findAll().stream()
            .map(AccountModel::to)
            .toList();
    }

    public Account findByEmailAndPassword(String email, String password) {

        if (password == null || password.isEmpty()) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST, "Password is empty"
            );
        }

        final String hash = calcHash(password.trim());
        return accountRepository.findByEmailAndHashPassword(email, hash)
            .map(AccountModel::to)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.UNAUTHORIZED, "Invalid credentials"
            ));

    }

    private String calcHash(String text) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(text.getBytes(StandardCharsets.UTF_8));
            byte[] digest = md.digest();
            return Base64.getEncoder().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

}
