package com.dillon.starsectormarines.marine;

/** Campaign availability of one persisted rank-and-file marine. */
public enum MarineSoldierStatus {
    ACTIVE,
    /**
     * A settlement's fate, never a marine's state: they went down and came home fit, so the
     * marine stays {@link #ACTIVE} and their service record takes the wound. The constant also
     * keeps older saves loading; {@code MarineSoldier} returns anyone saved with it to duty.
     */
    WIA,
    MIA,
    KIA
}
