package com.dillon.starsectormarines.battle.mech;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Command mailbox for battle-only mech doctrine overrides and lance-wide
 * cohesion orders. Requests are applied by {@link MechDoctrineSystem} in the
 * serial command phase immediately before squad replanning.
 *
 * <p>The player reaches only the doctrine override. The lance order has no
 * player control: player mechs move individually (each drop is its own
 * one-mech squad) and follow infantry through an attachment instead
 * ({@link MechAttachmentService}); the lance order stays the coordination rule
 * of commander and enemy lances, and this request is its scripting and evidence
 * seam.
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

    /** One lance-wide cohesion order requested through an exact selected mech. */
    static final class PendingLanceOrder {
        final long mechId;
        final MechLanceOrder order;

        PendingLanceOrder(long mechId, MechLanceOrder order) {
            this.mechId = mechId;
            this.order = order;
        }
    }

    private final List<PendingOverride> pending = new ArrayList<>();
    private final List<PendingLanceOrder> pendingLanceOrders = new ArrayList<>();

    /**
     * Queues a doctrine change for one selected mech. Validation is deferred
     * to the command-phase drain so stale, dead, enemy, and non-mech ids are
     * harmless. Passing null restores the doctrine frozen at deployment.
     */
    public void requestOverride(long mechId, MechRole role) {
        pending.add(new PendingOverride(mechId, role));
    }

    /**
     * Queues one battle-local cohesion order for the selected mech's entire
     * lance. Validation is deferred to the serial command-phase drain.
     */
    public void requestLanceOrder(long mechId, MechLanceOrder order) {
        pendingLanceOrders.add(new PendingLanceOrder(mechId, order));
    }

    List<PendingOverride> drainPending() {
        if (pending.isEmpty()) return Collections.emptyList();
        List<PendingOverride> drained = new ArrayList<>(pending);
        pending.clear();
        return drained;
    }

    List<PendingLanceOrder> drainPendingLanceOrders() {
        if (pendingLanceOrders.isEmpty()) return Collections.emptyList();
        List<PendingLanceOrder> drained = new ArrayList<>(pendingLanceOrders);
        pendingLanceOrders.clear();
        return drained;
    }
}
