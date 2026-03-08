package com.kevel.des;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

/**
 * Single-threaded discrete-event simulation engine.
 *
 * <p>Global invariants (hold after {@link #create(Object)} and after any public method returns
 * normally):
 *
 * <ul>
 *   <li>Single-threaded use only: this class is not thread-safe.
 *   <li>{@code now} is monotonically non-decreasing.
 *   <li>Every pending event has {@code event.time() >= now}.
 *   <li>Execution order is total and deterministic by {@code (time, sequence)}.
 *   <li>Assigned sequence numbers are strictly increasing.
 * </ul>
 */
public final class Simulation<S> {

    private final PriorityQueue<Event<S>> pendingEvents;
    private long now;
    private S state;
    private long nextSequence;
    private long nextEventId;

    private Simulation(S initialState) {
        this.now = 0L;
        this.state = initialState;
        this.nextSequence = 0L;
        this.nextEventId = 0L;
        Comparator<Event<S>> comparator =
                Comparator.comparingLong(Event<S>::time).thenComparingLong(Event<S>::sequence);
        this.pendingEvents = new PriorityQueue<>(comparator);
    }

    /**
     * Creates a new simulation with time {@code 0} and an empty queue.
     *
     * <p>Preconditions:
     *
     * <ul>
     *   <li>{@code initialState != null}
     * </ul>
     *
     * <p>Postconditions:
     *
     * <ul>
     *   <li>{@code now() == 0}
     *   <li>{@code state().equals(initialState)}
     *   <li>{@code isEmpty() == true}
     *   <li>{@code peekNext().isEmpty() == true}
     * </ul>
     *
     * @throws NullPointerException if {@code initialState} is null
     */
    public static <S> Simulation<S> create(S initialState) {
        Objects.requireNonNull(initialState, "initialState must not be null");
        return new Simulation<>(initialState);
    }

    /** Returns the current simulation time tick. */
    public long now() {
        return now;
    }

    /** Returns the current simulation state. */
    public S state() {
        return state;
    }

    /** Returns {@code true} when no events are pending. */
    public boolean isEmpty() {
        return pendingEvents.isEmpty();
    }

    /**
     * Returns the next event to execute, if any.
     *
     * <p>Preconditions: none.
     *
     * <p>Postconditions:
     *
     * <ul>
     *   <li>empty iff queue is empty
     *   <li>if present, event is minimal by {@code (time, sequence)}
     *   <li>does not mutate {@code now}, {@code state}, or queue contents
     * </ul>
     */
    public Optional<Event<S>> peekNext() {
        return Optional.ofNullable(pendingEvents.peek());
    }

    /**
     * Schedules an event at absolute time {@code time}.
     *
     * <p>Preconditions:
     *
     * <ul>
     *   <li>{@code action != null}
     *   <li>{@code time >= now()}
     * </ul>
     *
     * <p>Postconditions:
     *
     * <ul>
     *   <li>exactly one new event is enqueued
     *   <li>enqueued event has given {@code time} and {@code action}
     *   <li>returns a unique non-null {@link EventId}
     *   <li>assigned sequence is strictly greater than all previously assigned sequences
     * </ul>
     *
     * @throws NullPointerException if {@code action} is null
     * @throws IllegalArgumentException if {@code time < now()}
     */
    public EventId scheduleAt(long time, UnaryOperator<S> action) {
        Objects.requireNonNull(action, "action must not be null");
        if (time < now) {
            throw new IllegalArgumentException("cannot schedule event in the past");
        }

        long sequence = allocateNextSequence();
        EventId eventId = new EventId(allocateNextEventId());
        pendingEvents.add(new Event<>(time, sequence, eventId, action));
        return eventId;
    }

    /**
     * Schedules an event at relative delay {@code delay} from {@link #now()}.
     *
     * <p>Preconditions:
     *
     * <ul>
     *   <li>{@code action != null}
     *   <li>{@code delay >= 0}
     *   <li>{@code now() + delay} does not overflow {@code long}
     * </ul>
     *
     * <p>Postconditions:
     *
     * <ul>
     *   <li>equivalent to {@code scheduleAt(now() + delay, action)}
     * </ul>
     *
     * @throws NullPointerException if {@code action} is null
     * @throws IllegalArgumentException if {@code delay < 0}
     * @throws ArithmeticException if {@code now() + delay} overflows
     */
    public EventId scheduleIn(long delay, UnaryOperator<S> action) {
        Objects.requireNonNull(action, "action must not be null");
        if (delay < 0) {
            throw new IllegalArgumentException("delay must be >= 0");
        }
        long time = Math.addExact(now, delay);
        return scheduleAt(time, action);
    }

    /**
     * Executes exactly one next event.
     *
     * <p>Preconditions:
     *
     * <ul>
     *   <li>queue is not empty
     * </ul>
     *
     * <p>Postconditions:
     *
     * <ul>
     *   <li>exactly one event is removed and executed (minimum by {@code (time, sequence)})
     *   <li>{@code now()} becomes the executed event time
     *   <li>{@code state()} becomes {@code action.apply(previousState)}
     *   <li>returns an accurate {@link StepResult}
     * </ul>
     *
     * @throws IllegalStateException if queue is empty
     */
    public StepResult<S> step() {
        Event<S> next = pendingEvents.poll();
        if (next == null) {
            throw new IllegalStateException("cannot step when queue is empty");
        }

        long previousTime = now;
        S previousState = state;
        now = next.time();
        S newState = Objects.requireNonNull(next.action().apply(previousState), "action returned null state");
        state = newState;

        return new StepResult<>(previousTime, now, next.id(), previousState, newState);
    }

    /**
     * Executes all pending events.
     *
     * <p>Postconditions:
     *
     * <ul>
     *   <li>queue is empty on return
     *   <li>{@code stoppedByCondition == false}
     *   <li>{@code stepResults} contains all executed steps in order and is unmodifiable
     * </ul>
     */
    public RunResult<S> run() {
        return runInternal(simulation -> false, true, false, 0L);
    }

    /**
     * Executes events while next event time is {@code <= tEnd}.
     *
     * <p>Preconditions:
     *
     * <ul>
     *   <li>{@code tEnd >= now()}
     * </ul>
     *
     * @throws IllegalArgumentException if {@code tEnd < now()}
     */
    public RunResult<S> runUntilTime(long tEnd) {
        if (tEnd < now) {
            throw new IllegalArgumentException("tEnd must be >= now");
        }
        return runInternal(simulation -> false, false, true, tEnd);
    }

    /**
     * Executes until queue is empty or {@code stopCondition} becomes true.
     *
     * <p>Preconditions:
     *
     * <ul>
     *   <li>{@code stopCondition != null}
     * </ul>
     *
     * @throws NullPointerException if {@code stopCondition} is null
     */
    public RunResult<S> runUntil(Predicate<Simulation<S>> stopCondition) {
        Objects.requireNonNull(stopCondition, "stopCondition must not be null");
        return runInternal(stopCondition, false, false, 0L);
    }

    private RunResult<S> runInternal(
            Predicate<Simulation<S>> stopCondition, boolean runAll, boolean boundedByTime, long tEnd) {
        long startTime = now;
        List<StepResult<S>> steps = new ArrayList<>();
        boolean stoppedByCondition = false;

        while (!pendingEvents.isEmpty()) {
            if (stopCondition.test(this)) {
                stoppedByCondition = true;
                break;
            }

            if (!runAll && boundedByTime) {
                Event<S> next = pendingEvents.peek();
                if (next != null && next.time() > tEnd) {
                    break;
                }
            }

            steps.add(step());
        }

        return new RunResult<>(steps.size(), startTime, now, state, stoppedByCondition, List.copyOf(steps));
    }

    private long allocateNextSequence() {
        if (nextSequence == Long.MAX_VALUE) {
            throw new IllegalStateException("sequence counter overflow");
        }
        return nextSequence++;
    }

    private long allocateNextEventId() {
        if (nextEventId == Long.MAX_VALUE) {
            throw new IllegalStateException("event id counter overflow");
        }
        return nextEventId++;
    }
}
