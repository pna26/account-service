package store.account;

import java.time.Instant;
import java.util.UUID;

/**
 * Fato publicado no tópico account-events. O eventId é único por evento:
 * o consumidor (notification-service) deduplica por ele.
 * Nunca leva senha nem hash: só o necessário para o e-mail de boas-vindas.
 */
public record AccountEvent(
        UUID eventId,
        String eventType,
        String accountId,
        String name,
        String email,
        Instant occurredAt) {

    public static AccountEvent created(Account account) {
        return new AccountEvent(UUID.randomUUID(), "AccountCreated", account.id(),
                account.name(), account.email(), Instant.now());
    }
}
