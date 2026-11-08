-- Transactional outbox (Hands-on 7 do curso): o evento é gravado na MESMA
-- transação que a conta. O OutboxRelay lê as linhas não publicadas e as envia
-- ao Kafka; se o Kafka estiver fora, elas esperam aqui e nada se perde.
CREATE TABLE accounts.outbox (
    id             UUID         PRIMARY KEY,
    aggregate_type VARCHAR(50)  NOT NULL,
    aggregate_id   VARCHAR(36)  NOT NULL,
    event_type     VARCHAR(50)  NOT NULL,
    payload        JSONB        NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT clock_timestamp(),
    published_at   TIMESTAMPTZ
);

-- o relay só olha as linhas ainda não publicadas: índice parcial pequeno
CREATE INDEX outbox_unpublished ON accounts.outbox (created_at) WHERE published_at IS NULL;
