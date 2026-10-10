-- Events between services (libs/events). A platform migration: recorded in flyway_platform_history,
-- apart from the application's migrations. It ships in the events library, so every service that
-- uses the library gets these tables with its platform migrations.
--
-- Until the schema split the services share one schema, and so these two tables: source and
-- consumer keep each service's rows apart.

-- The outbox: an event is written in the transaction that changes the data it is about, and the
-- relay publishes it to SNS after the commit.
CREATE TABLE outbox_event (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    event_id     CHAR(36)     NOT NULL,
    source       VARCHAR(50)  NOT NULL,
    type         VARCHAR(100) NOT NULL,
    payload      JSON         NOT NULL,
    occurred_at  DATETIME(6)  NOT NULL,
    published_at DATETIME(6)  NULL,
    attempts     INT          NOT NULL DEFAULT 0,
    last_error   VARCHAR(500) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_outbox_event_event_id (event_id),
    KEY ix_outbox_event_pending (source, published_at, id)
);

-- The events a service has handled. SQS delivers a message at least once; a second delivery finds
-- its row here and is skipped.
CREATE TABLE consumed_message (
    consumer    VARCHAR(50)  NOT NULL,
    message_id  CHAR(36)     NOT NULL,
    event_type  VARCHAR(100) NOT NULL,
    consumed_at DATETIME(6)  NOT NULL,
    PRIMARY KEY (consumer, message_id)
);
