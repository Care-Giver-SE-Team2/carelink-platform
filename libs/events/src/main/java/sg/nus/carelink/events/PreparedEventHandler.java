package sg.nus.carelink.events;

/**
 * Opt-in two-phase event handling for slow external calls.
 * prepare runs without the consumer transaction; commit runs in the same
 * transaction as consumed_message. Preparation MUST have no persistent side effects.
 */
public interface PreparedEventHandler<T> extends EventHandler<T> {
    Runnable prepare(T payload, EventMetadata metadata);

    @Override
    default void handle(T payload, EventMetadata metadata) {
        prepare(payload, metadata).run();
    }
}
