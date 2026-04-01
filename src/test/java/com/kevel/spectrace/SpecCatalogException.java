package com.kevel.spectrace;

/**
 * Exceptional failure raised when canonical spec files violate catalog invariants.
 *
 * <p>This exception is thrown directly by the catalog builder and then observed by the JUnit
 * integration boundary when spec metadata is structurally invalid.
 *
 * <p>Example:
 *
 * <pre>{@code
 * try {
 *     SpecCatalogBuilder.build(projectRoot);
 * } catch (SpecCatalogException exception) {
 *     String failure = exception.getMessage();
 * }
 * }</pre>
 */
final class SpecCatalogException extends RuntimeException {

    /**
     * Creates an exception with a provenance-rich diagnostic message.
     *
     * @param message human-readable explanation of the catalog invariant violation
     */
    SpecCatalogException(String message) {
        super(message);
    }
}
