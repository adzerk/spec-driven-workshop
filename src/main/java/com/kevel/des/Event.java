package com.kevel.des;

import java.util.Objects;
import java.util.function.Function;

/**
 * Immutable scheduled event ordered by {@code (time, sequence)}.
 *
 * <p>Preconditions: {@code time >= 0}, {@code sequence >= 0}, {@code eventId != null}, and
 * {@code action != null}.
 *
 * <p>Invariant: record fields are immutable after construction.
 *
 * @param time absolute simulation time in ticks
 * @param sequence monotonic insertion sequence for deterministic FIFO ordering at equal time
 * @param eventId unique event identifier within one simulation
 * @param action pure state-transition function
 */
public record Event<S>(long time, long sequence, EventId eventId, Function<S, ActionResult<S>> action)
        implements Comparable<Event<S>> {

    public Event {
        if (time < 0) {
            throw new IllegalArgumentException("time must be non-negative");
        }
        if (sequence < 0) {
            throw new IllegalArgumentException("sequence must be non-negative");
        }
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(action, "action must not be null");
    }

    @Override
    // @ skipesc skiprac
    /**
     * Compares events in deterministic total order by {@code (time, sequence)}.
     *
     * <p>Postcondition: returns a negative value when this event occurs earlier, positive when
     * later, and zero when both time and sequence are equal.
     */
    public int compareTo(Event<S> other) {
        int timeOrder = Long.compare(this.time, other.time);
        if (timeOrder != 0) {
            return timeOrder;
        }
        return Long.compare(this.sequence, other.sequence);
    }
}
