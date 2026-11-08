package store.account.outbox;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Publica no Kafka os eventos que o Outbox gravou, depois do commit (Hands-on 7). */
@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    record Pending(UUID id, String aggregateId, String eventType, String payload) {
    }

    private final JdbcClient jdbc;
    private final JdbcTemplate jdbcTemplate;
    private final KafkaTemplate<String, String> kafka;
    private final String topic;

    public OutboxRelay(JdbcClient jdbc, JdbcTemplate jdbcTemplate,
                       KafkaTemplate<String, String> kafka,
                       @Value("${store.account.topic}") String topic) {
        this.jdbc = jdbc;
        this.jdbcTemplate = jdbcTemplate;
        this.kafka = kafka;
        this.topic = topic;
    }

    @Scheduled(fixedDelayString = "${store.account.relay-interval-ms:200}")
    @Transactional
    public void relay() throws Exception {
        // SKIP LOCKED: várias réplicas do account podem rodar o relay sem pegar as mesmas linhas
        List<Pending> batch = jdbc.sql("""
                SELECT id, aggregate_id, event_type, payload::text AS payload
                FROM accounts.outbox
                WHERE published_at IS NULL
                ORDER BY created_at
                LIMIT 100
                FOR UPDATE SKIP LOCKED
                """)
                .query(Pending.class)
                .list();
        if (batch.isEmpty()) {
            return;
        }

        List<CompletableFuture<SendResult<String, String>>> acks = new ArrayList<>();
        for (Pending p : batch) {
            // chave = id da conta: eventos da mesma conta caem na mesma partição (ordem por chave)
            ProducerRecord<String, String> record =
                    new ProducerRecord<>(topic, p.aggregateId(), p.payload());
            record.headers().add("eventId", p.id().toString().getBytes(StandardCharsets.UTF_8));
            record.headers().add("eventType", p.eventType().getBytes(StandardCharsets.UTF_8));
            acks.add(kafka.send(record));
        }
        // espera o ack de todos; se falhar, a transação volta e as linhas são tentadas de novo
        CompletableFuture.allOf(acks.toArray(new CompletableFuture<?>[0])).get(10, TimeUnit.SECONDS);

        jdbcTemplate.batchUpdate("UPDATE accounts.outbox SET published_at = clock_timestamp() WHERE id = ?",
                batch.stream().map(p -> new Object[] {p.id()}).toList());
        log.info("relayed {} event(s) to {}", batch.size(), topic);
    }

    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void purge() {
        int n = jdbcTemplate.update(
                "DELETE FROM accounts.outbox WHERE published_at < now() - interval '1 day'");
        log.info("purged {} published outbox row(s)", n);
    }
}
