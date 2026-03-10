package com.kevel.des;

import java.util.Objects;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Single-threaded discrete-event simulation engine.
 *
 * <p>Usage is intentionally single-threaded: instances are mutable and not thread-safe.
 *
 * <p>Event actions are expected to be pure functions {@code S -> ActionResult<S>} that describe
 * both state updates and follow-on scheduling declaratively via {@link ActionResult}. If actions
 * mutate shared state or perform side effects, engine ordering remains deterministic but functional
 * semantics can be broken by user code.
 *
 * <p>Minimal usage example:
 *
 * <pre>{@code
 * Simulation<Integer> sim = Simulation.create(0);
 * sim.scheduleAt(1, state -> new ActionResult<>(state + 1, List.of(
 *         new ActionResult.ScheduledEvent<>(2, s -> ActionResult.of(s + 10)))));
 * RunResult<Integer> result = sim.run();
 * // result.stepsExecuted() == 2
 * // result.finalState() == 11
 * }</pre>
 *
 * <p>Class invariants: current time is non-negative and the sequence counter is non-negative.
 */
public final class Simulation<S> {

    /*@ private invariant currentTime >= 0; @*/
    /*@ private invariant nextSequence >= 0; @*/

    private long currentTime;
    private long nextSequence;
    private long nextEventId;
    /*@ nullable @*/ private S currentState;
    private final PriorityQueue<Event<S>> queue;

    // @ skipesc skiprac
    private Simulation(S initialState) {
        this.currentTime = 0L;
        this.nextSequence = 0L;
        this.nextEventId = 0L;
        this.currentState = initialState;
        this.queue = new PriorityQueue<>();
    }

    /**
     * Creates a simulation with initial time {@code 0}, empty queue, and a non-null initial state.
     *
     * @throws NullPointerException if {@code initialState} is null
     */
    // @ skipesc skiprac
    public static <S> Simulation<S> create(S initialState) {
        Objects.requireNonNull(initialState, "initialState must not be null");
        return new Simulation<>(initialState);
    }

    /** Returns current simulation time. */
    // @ skipesc skiprac
    /*@ pure @*/
    public long currentTime() {
        return currentTime;
    }

    /** Returns current simulation state. */
    // @ skipesc skiprac
    /*@ pure @*/
    public S currentState() {
        return currentState;
    }

    /** Returns whether the pending event queue is empty. */
    // @ skipesc skiprac
    /*@ pure @*/
    public boolean isQueueEmpty() {
        return queue.isEmpty();
    }

    /**
     * Returns the next event to execute, if present, without mutating simulation state.
     *
     * <p>Postcondition: this method does not modify queue contents, current time, or current state.
     */
    // @ skipesc skiprac
    public Optional<Event<S>> peekNext() {
        return Optional.ofNullable(queue.peek());
    }

    /**
     * Schedules an event for an absolute simulation time.
     *
     * <p>Preconditions: action is non-null and time is not in the past relative to current
     * simulation time.
     *
     * <p>Postconditions: queue gains one event ordered by {@code (time, sequence)} and a non-null
     * unique event id is returned.
     *
     * @throws NullPointerException if {@code action} is null
     * @throws IllegalArgumentException if {@code time} is earlier than current time
     */
    // @ skipesc skiprac
    /*@ requires action != null; @*/
    /*@ requires time >= currentTime(); @*/
    public EventId scheduleAt(long time, Function<S, ActionResult<S>> action) {
        Objects.requireNonNull(action, "action must not be null");
        if (time < currentTime) {
            throw new IllegalArgumentException("time must not be in the past");
        }
        return scheduleInternal(time, action);
    }

    /**
     * Schedules an event for {@code delay} ticks relative to current simulation time.
     *
     * <p>Preconditions: action is non-null and delay is non-negative.
     *
     * <p>Postconditions: equivalent to scheduling at {@code currentTime + delay} with overflow
     * checked by {@link Math#addExact(long, long)}.
     *
     * @throws NullPointerException if {@code action} is null
     * @throws IllegalArgumentException if {@code delay} is negative
     * @throws ArithmeticException if target time overflows {@code long}
     */
    // @ skipesc skiprac
    /*@ requires action != null; @*/
    /*@ requires delay >= 0; @*/
    public EventId scheduleIn(long delay, Function<S, ActionResult<S>> action) {
        Objects.requireNonNull(action, "action must not be null");
        if (delay < 0) {
            throw new IllegalArgumentException("delay must be non-negative");
        }
        long targetTime = Math.addExact(currentTime, delay);
        return scheduleAt(targetTime, action);
    }

    /**
     * Executes the earliest pending event by {@code (time, sequence)}.
     *
     * <p>Precondition: queue is not empty.
     *
     * <p>Postconditions: exactly one queued event is executed, current time advances to the
     * executed event time, current state becomes the returned action result state, and any
     * action-produced event descriptors are inserted when valid.
     *
     * @throws IllegalStateException if queue is empty
     * @throws IllegalArgumentException if an action-produced event has time earlier than current
     */
    // @ skipesc skiprac
    /*@ requires !isQueueEmpty(); @*/
    /*@ ensures currentTime() >= \old(currentTime()); @*/
    public StepResult<S> step() {
        if (queue.isEmpty()) {
            throw new IllegalStateException("cannot step an empty queue");
        }

        long previousTime = currentTime;
        S previousState = currentState;

        Event<S> nextEvent = Objects.requireNonNull(queue.poll(), "queue poll must not be null");
        currentTime = nextEvent.time();

        ActionResult<S> actionResult =
                Objects.requireNonNull(nextEvent.action().apply(previousState), "action result must not be null");
        currentState = actionResult.newState();

        for (ActionResult.ScheduledEvent<S> descriptor : actionResult.scheduledEvents()) {
            if (descriptor.time() < currentTime) {
                throw new IllegalArgumentException("action-produced event is in the past");
            }
            scheduleInternal(descriptor.time(), descriptor.action());
        }

        return new StepResult<>(previousTime, currentTime, nextEvent.eventId(), previousState, currentState);
    }

    /**
     * Runs until queue is empty.
     *
     * <p>Postcondition: queue is empty at return and the returned result captures start/end times,
     * executed steps, final state, and stop reason.
     */
    // @ skipesc skiprac
    public RunResult<S> run() {
        long startTime = currentTime;
        long steps = 0L;

        while (!queue.isEmpty()) {
            step();
            steps++;
        }

        return new RunResult<>(steps, startTime, currentTime, currentState, false);
    }

    /**
     * Runs while next event time is less than or equal to {@code targetTime}.
     *
     * <p>Precondition: target time is not in the past.
     *
     * <p>Postcondition: all executed events have time {@code <= targetTime}; if events remain,
     * next event time is {@code > targetTime}.
     *
     * @throws IllegalArgumentException if target time is earlier than current time
     */
    // @ skipesc skiprac
    public RunResult<S> runUntilTime(long targetTime) {
        if (targetTime < currentTime) {
            throw new IllegalArgumentException("target time must not be in the past");
        }

        long startTime = currentTime;
        long steps = 0L;

        Event<S> next = queue.peek();
        while (next != null && next.time() <= targetTime) {
            step();
            steps++;
            next = queue.peek();
        }

        boolean stoppedByCondition = !queue.isEmpty();
        return new RunResult<>(steps, startTime, currentTime, currentState, stoppedByCondition);
    }

    /**
     * Runs until {@code stopCondition} is true, checking before each step.
     *
     * <p>Precondition: stop condition is non-null.
     *
     * <p>Postcondition: predicate is evaluated before every potential step and no step executes
     * once it is satisfied.
     *
     * @throws NullPointerException if stop condition is null
     */
    // @ skipesc skiprac
    public RunResult<S> runUntil(Predicate<S> stopCondition) {
        Objects.requireNonNull(stopCondition, "stopCondition must not be null");

        long startTime = currentTime;
        long steps = 0L;
        boolean stoppedByCondition = false;

        while (true) {
            if (stopCondition.test(currentState)) {
                stoppedByCondition = true;
                break;
            }
            if (queue.isEmpty()) {
                break;
            }
            step();
            steps++;
        }

        return new RunResult<>(steps, startTime, currentTime, currentState, stoppedByCondition);
    }

    // @ skipesc skiprac
    private EventId scheduleInternal(long time, Function<S, ActionResult<S>> action) {
        EventId eventId = new EventId(nextEventId);
        nextEventId = Math.addExact(nextEventId, 1L);

        long sequence = nextSequence;
        nextSequence = Math.addExact(nextSequence, 1L);

        queue.add(new Event<S>(time, sequence, eventId, action));
        return eventId;
    }
}
