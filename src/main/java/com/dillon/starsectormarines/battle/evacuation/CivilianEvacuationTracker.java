package com.dillon.starsectormarines.battle.evacuation;

import java.util.Arrays;

/**
 * Battle-owned lifecycle for the small cohort that represents a rescue event's
 * campaign population. This tracker does not own entity birth, movement, or
 * death; those systems report identity transitions here.
 *
 * <p>Identity lookup is intentionally linear. V1 contains eight members, so a
 * map would add more state and indirection than it removes.
 */
public final class CivilianEvacuationTracker {

    public static final int V1_REPRESENTATIVE_COUNT = 8;

    public enum State {
        ACTIVE,
        EVACUATED,
        LOST
    }

    private static final byte ACTIVE = 0;
    private static final byte EVACUATED = 1;
    private static final byte LOST = 2;

    private long[] entityIds;
    private byte[] states;
    private boolean[] confirmedDead;
    private int registered;
    private int active;
    private int evacuated;
    private int lost;
    private boolean sealed;

    public CivilianEvacuationTracker() {
        this(V1_REPRESENTATIVE_COUNT);
    }

    /** Visible for controlled fixtures and later balance variants. */
    public CivilianEvacuationTracker(int expectedCount) {
        if (expectedCount <= 0) {
            throw new IllegalArgumentException("expectedCount must be positive");
        }
        entityIds = new long[expectedCount];
        states = new byte[expectedCount];
        confirmedDead = new boolean[expectedCount];
        Arrays.fill(entityIds, -1L);
    }

    /**
     * Re-sizes an untouched tracker for a mission-authored representative
     * cohort. Once registration starts, its identity boundary is immutable.
     */
    public boolean prepareExpectedCount(int expectedCount) {
        if (expectedCount <= 0 || registered != 0 || sealed) return false;
        if (entityIds.length == expectedCount) return true;
        entityIds = new long[expectedCount];
        states = new byte[expectedCount];
        confirmedDead = new boolean[expectedCount];
        Arrays.fill(entityIds, -1L);
        return true;
    }

    /**
     * Registers one positive entity identity as active. Duplicate, excess, and
     * post-seal registration are harmless no-ops.
     */
    public boolean register(long entityId) {
        if (entityId <= 0L || sealed || registered == entityIds.length
                || indexOf(entityId) >= 0) {
            return false;
        }
        entityIds[registered] = entityId;
        states[registered] = ACTIVE;
        registered++;
        active++;
        return true;
    }

    /**
     * Grows a fully registered cohort by one member and registers it, for a
     * mission whose cohort is gathered from more than one place (Colony
     * Breach's holdouts). Refused once sealed, while the planned registration
     * is incomplete, and for an identity already registered.
     */
    public boolean enlist(long entityId) {
        if (entityId <= 0L || sealed || registered != entityIds.length
                || indexOf(entityId) >= 0) {
            return false;
        }
        int grown = entityIds.length + 1;
        entityIds = Arrays.copyOf(entityIds, grown);
        states = Arrays.copyOf(states, grown);
        confirmedDead = Arrays.copyOf(confirmedDead, grown);
        entityIds[registered] = -1L;
        return register(entityId);
    }

    /** Marks a registered active civilian as having crossed into safety. */
    public boolean markEvacuated(long entityId) {
        return transition(entityId, EVACUATED);
    }

    /** Marks a registered active civilian as dead or otherwise unrecoverable. */
    public boolean markLost(long entityId) {
        return transition(entityId, LOST);
    }

    /**
     * A death event, unlike terminal abandonment, is irreversible across story retries. It also confirms a member
     * already marked lost: the movement driver marks a member lost the tick its body is gone, which can come before
     * the death mailbox drains, and a death read after that must still count as a death.
     *
     * @return whether this call newly confirmed the death
     */
    public boolean markDead(long entityId) {
        int index = indexOf(entityId);
        if (sealed || index < 0) return false;
        if (states[index] == ACTIVE) transition(entityId, LOST);
        if (states[index] != LOST || confirmedDead[index]) return false;
        confirmedDead[index] = true;
        return true;
    }

    public boolean isConfirmedDead(long entityId) {
        int index = indexOf(entityId);
        return index >= 0 && confirmedDead[index];
    }

    /**
     * Closes a fully registered cohort and treats every remaining active member
     * as unsaved. An incomplete cohort cannot seal or manufacture a report.
     * Repeated sealing after success is harmless.
     */
    public boolean seal() {
        if (sealed) return true;
        if (registered != entityIds.length) return false;
        for (int i = 0; i < registered; i++) {
            if (states[i] == ACTIVE) {
                states[i] = LOST;
                active--;
                lost++;
            }
        }
        sealed = true;
        return true;
    }

    /** Returns the immutable terminal report, or {@code null} while unfinished. */
    public CivilianEvacuationReport report() {
        return sealed
                ? new CivilianEvacuationReport(registered, evacuated, lost)
                : null;
    }

    public State state(long entityId) {
        int index = indexOf(entityId);
        if (index < 0) return null;
        return switch (states[index]) {
            case EVACUATED -> State.EVACUATED;
            case LOST -> State.LOST;
            default -> State.ACTIVE;
        };
    }

    public int expectedCount() {
        return entityIds.length;
    }

    public int registeredCount() {
        return registered;
    }

    /** Registered identity at insertion index, for the mission objective's tiny cohort sweep. */
    public long entityIdAt(int index) {
        if (index < 0 || index >= registered) {
            throw new IndexOutOfBoundsException(index);
        }
        return entityIds[index];
    }

    public int activeCount() {
        return active;
    }

    public int evacuatedCount() {
        return evacuated;
    }

    public int lostCount() {
        return lost;
    }

    public boolean isSealed() {
        return sealed;
    }

    private boolean transition(long entityId, byte target) {
        if (sealed) return false;
        int index = indexOf(entityId);
        if (index < 0 || states[index] != ACTIVE) return false;
        states[index] = target;
        active--;
        if (target == EVACUATED) {
            evacuated++;
        } else {
            lost++;
        }
        return true;
    }

    private int indexOf(long entityId) {
        for (int i = 0; i < registered; i++) {
            if (entityIds[i] == entityId) return i;
        }
        return -1;
    }
}
