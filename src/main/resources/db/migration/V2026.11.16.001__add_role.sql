-- Bottleneck: autenticação e autorização (RBAC).
-- Toda conta nasce USER; ADMIN só por promoção direta no banco (a API nunca
-- aceita o papel na entrada, então ninguém se autopromove).
ALTER TABLE accounts.accounts
    ADD COLUMN role VARCHAR(16) NOT NULL DEFAULT 'USER'
    CONSTRAINT accounts_role_check CHECK (role IN ('USER', 'ADMIN'));
