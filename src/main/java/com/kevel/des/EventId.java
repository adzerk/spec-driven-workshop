package com.kevel.des;

/**
 * Identifier for a scheduled event.
 *
 * <p>Invariant: {@code value >= 0}.
 */
public record EventId(long value) {

    public EventId {
        if (value < 0) {
            throw new IllegalArgumentException("event id value must be >= 0");
        }
    }
}
