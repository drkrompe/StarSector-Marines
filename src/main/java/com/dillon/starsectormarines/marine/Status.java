package com.dillon.starsectormarines.marine;

public enum Status {
    /** Available to lead raids. */
    ACTIVE,
    /** Benched — recovering. {@code injuredUntilTimestamp} on the captain says when they return. */
    INJURED,
    /** Killed in action. Stays in the roster as history; cannot return. */
    KIA
}
