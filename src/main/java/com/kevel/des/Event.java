package com.kevel.des;

import java.util.Objects;
import java.util.function.UnaryOperator;

/**
 * Scheduled simulation event.
 *
 * <p>Invariants:
 *
 * <ul>
 *   <li>{@code time >= 0}
 *   <li>{@code sequence >= 0}
 *   <li>{@code id != null}
 *   <li>{@code action != null}
 * </ul>
 */
public record Event<S>(long time, long sequence, EventId id, UnaryOperator<S> action) {

    public Event {
        if (time < 0) {
            throw new IllegalArgumentException("time must be >= 0");
        }
        if (sequence < 0) {
            throw new IllegalArgumentException("sequence must be >= 0");
        }
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(action, "action must not be null");
    }
}
