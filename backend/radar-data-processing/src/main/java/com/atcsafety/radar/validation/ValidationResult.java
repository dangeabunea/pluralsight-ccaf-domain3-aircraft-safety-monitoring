package com.atcsafety.radar.validation;

/**
 * The outcome of validating a radar position.
 *
 * <p>Use pattern matching to handle both cases:
 * <pre>{@code
 * switch (validator.validate(position)) {
 *     case ValidationResult.Valid v   -> publish(position);
 *     case ValidationResult.Invalid i -> log.warn(i.reason());
 * }
 * }</pre>
 */
public sealed interface ValidationResult
        permits ValidationResult.Valid, ValidationResult.Invalid {

    /** The position passed all checks and may be forwarded to the next stage. */
    record Valid() implements ValidationResult {
        /** Shared instance — {@code Valid} carries no state, so allocation is unnecessary. */
        static final Valid INSTANCE = new Valid();
    }

    /**
     * The position was rejected.
     *
     * <p>Use {@link #of(String)} to create an instance — it guards against blank reasons.
     * {@code reason} describes which field(s) failed and why.
     */
    record Invalid(String reason) implements ValidationResult {

        /**
         * Creates an {@code Invalid} result with a non-blank reason.
         *
         * @param reason description of the validation failure; must not be blank
         * @throws IllegalArgumentException if {@code reason} is null or blank
         */
        static Invalid of(String reason) {
            if (reason == null || reason.isBlank()) {
                throw new IllegalArgumentException("reason must not be blank");
            }
            return new Invalid(reason);
        }
    }
}
