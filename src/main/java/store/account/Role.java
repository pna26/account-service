package store.account;

/**
 * Papéis do RBAC. Quem decide o que cada papel pode fazer é o gateway
 * (AccessPolicy); aqui o Account só guarda o papel de cada conta.
 */
public enum Role {
    USER,
    ADMIN
}
