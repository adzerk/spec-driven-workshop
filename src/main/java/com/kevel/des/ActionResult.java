package com.kevel.des;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * Result of executing an event action.
 *
 * <p>An action is expected to be a pure function {@code S -> ActionResult<S>}. The returned value
 * contains both the next state and any declarative scheduling intent as zero or more event
 * descriptors. The simulation engine interprets those descriptors after applying the state update.
 *
 * <p>Self-scheduling happens by returning entries in {@link #scheduledEvents()}, not by calling
 * methods on {@link Simulation} from inside an action.
 *
 * <p>Preconditions: {@code newState}, {@code scheduledEvents}, and each entry of
 * {@code scheduledEvents} are non-null.
 *
 * <p>Postconditions: this value is immutable, and {@code scheduledEvents()} is a defensive copy.
 */
public record ActionResult<S>(S newState, List<ScheduledEvent<S>> scheduledEvents) {

    /**
     * Descriptor for a future event to be scheduled by the engine after an action executes.
     *
     * <p>Precondition: {@code action} is non-null.
     *
     * @param time absolute event time in simulation ticks
     * @param action pure state-transition action
     */
    public record ScheduledEvent<S>(long time, Function<S, ActionResult<S>> action) {
        public ScheduledEvent {
            Objects.requireNonNull(action, "action must not be null");
        }
    }

    public ActionResult {
        Objects.requireNonNull(newState, "newState must not be null");
        Objects.requireNonNull(scheduledEvents, "scheduledEvents must not be null");
        scheduledEvents.forEach(event -> Objects.requireNonNull(event, "scheduled event must not be null"));
        scheduledEvents = List.copyOf(scheduledEvents);
    }

    /**
     * Returns an action result with no follow-on events.
     *
     * <p>Precondition: {@code newState} is non-null.
     */
    public static <S> ActionResult<S> of(S newState) {
        return new ActionResult<>(newState, List.of());
    }

    /**
     * Returns an action result with explicit follow-on event descriptors.
     *
     * <p>Preconditions: {@code newState} is non-null; {@code scheduledEvents} is non-null; each
     * scheduled descriptor is non-null.
     */
    public static <S> ActionResult<S> of(S newState, List<ScheduledEvent<S>> scheduledEvents) {
        return new ActionResult<>(newState, scheduledEvents);
    }
}
