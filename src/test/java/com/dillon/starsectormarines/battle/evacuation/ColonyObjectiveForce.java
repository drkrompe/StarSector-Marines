package com.dillon.starsectormarines.battle.evacuation;

/**
 * Forces Colony Breach's side objective for the length of a look or an evidence run
 * ({@link ColonyBreachObjectives#FORCE_PROPERTY}), and puts the property back as it was, so a suite that
 * reads one objective never leaves the next suite in the same JVM playing it.
 */
public final class ColonyObjectiveForce implements AutoCloseable {

    private final String was;

    private ColonyObjectiveForce(String key) {
        this.was = System.setProperty(ColonyBreachObjectives.FORCE_PROPERTY, key);
    }

    /** Forces the pool entry {@code key} until closed. */
    public static ColonyObjectiveForce force(String key) {
        return new ColonyObjectiveForce(key);
    }

    @Override
    public void close() {
        if (was == null) System.clearProperty(ColonyBreachObjectives.FORCE_PROPERTY);
        else System.setProperty(ColonyBreachObjectives.FORCE_PROPERTY, was);
    }
}
