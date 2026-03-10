package com.kevel.des;

/**
 * Unique identifier for an event scheduled in a {@link Simulation} instance.
 *
 * <p>Identifiers are assigned by a simulation and are only meaningful within that simulation.
 *
 * <p>Invariant: values assigned by {@link Simulation} are non-negative and unique within one
 * simulation instance.
 */
public record EventId(long value) {}
