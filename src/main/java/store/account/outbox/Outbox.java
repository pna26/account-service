package store.account.outbox;

import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.ObjectMapper;

@Component
public class Outbox {

    private final JdbcClient jdbc;
    private final ObjectMapper mapper;

    public Outbox(JdbcClient jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    /** MANDATORY: falha na hora se alguém chamar fora da transação de negócio. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void append(UUID eventId, String aggregateType, String aggregateId, String eventType, Object payload) {
        String json = mapper.writeValueAsString(payload); // Jackson 3: exceções não checadas
        jdbc.sql("""
                INSERT INTO accounts.outbox (id, aggregate_type, aggregate_id, event_type, payload)
                VALUES (?, ?, ?, ?, ?::jsonb)
                """)
                .params(eventId, aggregateType, aggregateId, eventType, json)
                .update();
    }
}
