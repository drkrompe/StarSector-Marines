package com.dillon.starsectormarines.battle.mech;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Player command mailbox for battle-only mech doctrine overrides. Requests
 * arrive from the input pass and are applied by {@link MechDoctrineSystem} in
 * the serial command phase immediately before squad replanning.
 */
public final class MechDoctrineService {

    /** One exact-mech override request. A null role means reset to deployment. */
    static final class PendingOverride {
        final long mechId;
        final MechRole role;

        PendingOverride(long mechId, MechRole role) {
            this.mechId = mechId;
            this.role = role;
        }
    }

    private final List<PendingOverride> pending = new ArrayList<>();

    /**
     * Queues a doctrine change for one selected mech. Validation is deferred
     * to the command-phase drain so stale, dead, enemy, and non-mech ids are
     * harmless. Passing null restores the doctrine frozen at deployment.
     */
    public void requestOverride(long mechId, MechRole role) {
        pending.add(new PendingOverride(mechId, role));
    }

    List<PendingOverride> drainPending() {
        if (pending.isEmpty()) return Collections.emptyList();
        List<PendingOverride> drained = new ArrayList<>(pending);
        pending.clear();
        return drained;
    }
}
