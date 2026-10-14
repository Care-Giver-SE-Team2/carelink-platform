-- The Open Platform's providers (PT01, docs/api/open-platform.yaml): each application to join, and each
-- provider a manager approved, with its client credentials and its standing. provider_webhook_endpoint (V2)
-- is keyed by provider.id.
--
-- No password and no client secret is ever kept as itself. The portal password and the client secret are kept
-- only as hashes, in provider_portal_user and provider_client below; the client secret and the webhook signing
-- secret are also kept encrypted (AES-256-GCM, the key carelink.webhooks.encryption-key) until the provider
-- collects them, once; after that only their last four characters are kept to show which one is in use.
--
-- A provider takes the id of the application it was approved from: one application makes one provider, and the
-- id is known before anything is kept.

CREATE TABLE provider_application (
    id                BIGINT        NOT NULL AUTO_INCREMENT COMMENT 'shown as APP-<year submitted>-<id, five digits>',
    organisation_name VARCHAR(200)  NOT NULL,
    contact_name      VARCHAR(100)  NOT NULL,
    contact_email     VARCHAR(254)  NOT NULL COMMENT 'lower case; also the portal sign-in',
    contact_phone     VARCHAR(30)   NOT NULL,
    services          VARCHAR(1000) NOT NULL COMMENT 'a JSON array of names from report''s value-added catalogue',
    webhook_url       VARCHAR(2048) NOT NULL,
    portal_user_id    VARCHAR(128)  NOT NULL COMMENT 'provider_portal_user.subject: the sub its tokens carry',
    status            ENUM('PENDING','APPROVED','REJECTED') NOT NULL,
    reason            VARCHAR(500)  NULL COMMENT 'why it was rejected, shown to the provider; null otherwise',
    submitted_at      DATETIME(6)   NOT NULL,
    reviewed_at       DATETIME(6)   NULL,
    reviewed_by       BIGINT        NULL COMMENT 'core''s account of the manager who decided',
    provider_id       BIGINT        NULL COMMENT 'the provider it became, once approved',
    PRIMARY KEY (id),
    KEY idx_provider_application_email (contact_email, status),
    KEY idx_provider_application_portal_user (portal_user_id, submitted_at),
    KEY idx_provider_application_status (status, submitted_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE provider (
    id                       BIGINT        NOT NULL COMMENT 'the id of the application it was approved from',
    organisation_name        VARCHAR(200)  NOT NULL,
    contact_email            VARCHAR(254)  NOT NULL,
    services                 VARCHAR(1000) NOT NULL COMMENT 'a JSON array, as on the application',
    portal_user_id           VARCHAR(128)  NOT NULL,
    client_id                VARCHAR(128)  NOT NULL COMMENT 'the OAuth2 app client the job API''s tokens name',
    client_secret_hint       CHAR(4)       NOT NULL COMMENT 'the client secret''s last four characters',
    client_secret_issued_at  DATETIME(6)   NOT NULL,
    uncollected_client_secret  VARCHAR(512) NULL COMMENT 'encrypted; kept only until the provider collects it',
    uncollected_webhook_secret VARCHAR(512) NULL COMMENT 'encrypted; kept only until the provider collects it',
    webhook_secret_issued_at DATETIME(6)   NOT NULL,
    credentials_collected_at DATETIME(6)   NULL,
    status                   ENUM('ACTIVE','SUSPENDED') NOT NULL,
    version                  INT           NOT NULL COMMENT 'ProviderUpdated''s version: up by one with every change',
    approved_at              DATETIME(6)   NOT NULL,
    updated_at               DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_provider_portal_user (portal_user_id),
    UNIQUE KEY uk_provider_client (client_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- Who may call the Open Platform: each provider's portal user, who signs in to the Partner Portal, and the
-- client its server gets job tokens with. notification issues and checks these tokens itself
-- (docs/section5/decisions.md, D15).
CREATE TABLE provider_portal_user (
    subject         VARCHAR(128) NOT NULL COMMENT 'the sub its tokens carry',
    username        VARCHAR(254) NOT NULL COMMENT 'the contact email, lower case',
    password_hash   VARCHAR(100) NOT NULL COMMENT 'bcrypt',
    failed_sign_ins INT          NOT NULL DEFAULT 0 COMMENT 'wrong passwords in a row; reset by a sign-in or a lock',
    locked_until    DATETIME(6)  NULL COMMENT 'set after five wrong passwords in a row: no sign-in until then',
    created_at      DATETIME(6)  NOT NULL,
    updated_at      DATETIME(6)  NOT NULL,
    PRIMARY KEY (subject),
    UNIQUE KEY uk_provider_portal_user_username (username)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE provider_client (
    client_id   VARCHAR(128) NOT NULL,
    secret_hash CHAR(64)     NOT NULL COMMENT 'SHA-256 of the secret, hex: the secret is random, so no salt or stretching is needed',
    scopes      VARCHAR(200) NOT NULL COMMENT 'space-separated',
    created_at  DATETIME(6)  NOT NULL,
    PRIMARY KEY (client_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- The key the Open Platform's tokens are signed with. The first replica to start makes it; every replica then
-- signs with the same one, and a restart keeps every token valid.
CREATE TABLE provider_token_key (
    name        VARCHAR(20)  NOT NULL COMMENT 'current: the only one, until keys are rotated',
    key_id      VARCHAR(64)  NOT NULL COMMENT 'the kid in the tokens'' header and the published key set',
    private_key TEXT         NOT NULL COMMENT 'the RSA key as a JWK, encrypted as the signing secrets are',
    created_at  DATETIME(6)  NOT NULL,
    PRIMARY KEY (name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
