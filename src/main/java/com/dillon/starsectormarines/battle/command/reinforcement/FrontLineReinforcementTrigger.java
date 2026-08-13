package com.dillon.starsectormarines.battle.command.reinforcement;

import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.gen.BiomeKind;
import com.dillon.starsectormarines.battle.world.gen.TraversalAxis;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Biome-slice round-robin reinforcement trigger — the front-line dispatch
 * design in {@code roadmap/conquest/stories/progressive-reinforcement.md}
 * (slice 3). Replaces {@link GarrisonDepletedTrigger} on maps that carry a
 * {@link RecaptureTargetService} (conquest only; see
 * {@code BattleSetup#installReinforcementLayer}).
 *
 * <p>Each poll:
 * <ol>
 *   <li>Reads {@link RecaptureTargetService#eligibleTargets()} — open,
 *       undispatched targets in slices the defender still contests.</li>
 *   <li>Groups them by biome slice and picks the nearest-to-defender slice
 *       (FORTRESS_DISTRICT before CITY before PORT before BEACH; OUTSKIRTS
 *       last as a legacy catch-all) that has at least one eligible target —
 *       reinforcing the defender's own rear before falling through toward
 *       the marine side.</li>
 *   <li>Round-robins across that slice's eligible targets so repeated
 *       dispatches spread across positions instead of stacking on one.</li>
 * </ol>
 *
 * <p>Posts at most one request per {@link #check} call — the trigger runs on
 * {@link ReinforcementSystem}'s 1&nbsp;Hz cadence, so there's no need for an
 * internal accumulator. When {@link RecaptureTargetService#eligibleTargets()}
 * is empty (every lost position is already held or has a reinforcement en
 * route, or every slice is conceded) the trigger posts nothing — overflow →
 * patrol is deferred per the design doc; the existing {@link WalkInMeans}
 * free-agent fallback covers ambient patrol in the meantime.
 */
public final class FrontLineReinforcementTrigger implements ReinforcementTrigger {

    /**
     * Nearest-to-defender slice order the round-robin picker walks. The
     * defender reinforces their own rear first and only falls through toward
     * a slice nearer the marine side once every closer slice has no eligible
     * target (either fully held, or already answered by a squad en route).
     * OUTSKIRTS is a legacy sparse-fallback kind, ordered last for totality —
     * it never appears in a conquest biome layout's normal band sequence.
     */
    private static final BiomeKind[] SLICE_ORDER = {
            BiomeKind.FORTRESS_DISTRICT, BiomeKind.CITY, BiomeKind.PORT,
            BiomeKind.BEACH, BiomeKind.OUTSKIRTS
    };

    /**
     * Cells the rally hint is shifted from the target's anchor, toward the
     * defender rear along {@link #axis}. The rally is only a search seed for
     * the means + {@link LandingZoneScorer} — not the literal deboard cell —
     * so this just needs to land solidly inside defender territory, not on a
     * specific viable cell.
     */
    private static final int RALLY_REAR_SHIFT = 8;

    private final RecaptureTargetService targets;
    private final TraversalAxis axis;

    /** Per-slice round-robin cursor — the index of the next target to dispatch within that slice's eligible list. */
    private final Map<BiomeKind, Integer> rotation = new EnumMap<>(BiomeKind.class);

    public FrontLineReinforcementTrigger(RecaptureTargetService targets, TraversalAxis axis) {
        this.targets = targets;
        this.axis = axis;
    }

    @Override
    public void check(BattleView sim, Consumer<ReinforcementRequest> out) {
        RecaptureTarget target = selectDispatchTarget();
        if (target == null) return;
        int[] rally = rallyRearShift(target.node.anchorX, target.node.anchorY, axis, sim.getGrid());
        out.accept(new ReinforcementRequest(
                Faction.DEFENDER,
                ReinforcementRequest.Reason.GARRISON_DEPLETED,
                ReinforcementRequest.Strength.SMALL,
                rally[0], rally[1],
                target.objectiveX(), target.objectiveY()));
        targets.markDispatched(target);
    }

    /**
     * Picks the next dispatch target: nearest-to-defender slice (by
     * {@link #SLICE_ORDER}) with at least one eligible target, then the next
     * target in that slice's round-robin rotation. Advances {@link #rotation}
     * as a side effect. Returns {@code null} when
     * {@link RecaptureTargetService#eligibleTargets()} is empty.
     *
     * <p>Package-private (not {@code check}'s {@link BattleView} dependency)
     * so tests can exercise the selection logic directly against a
     * synthetic {@link RecaptureTargetService}.
     */
    RecaptureTarget selectDispatchTarget() {
        List<RecaptureTarget> eligible = targets.eligibleTargets();
        if (eligible.isEmpty()) return null;

        Map<BiomeKind, List<RecaptureTarget>> bySlice = new EnumMap<>(BiomeKind.class);
        for (RecaptureTarget t : eligible) {
            bySlice.computeIfAbsent(t.slice, k -> new ArrayList<>()).add(t);
        }
        for (BiomeKind slice : SLICE_ORDER) {
            List<RecaptureTarget> inSlice = bySlice.get(slice);
            if (inSlice == null || inSlice.isEmpty()) continue;
            int idx = rotation.getOrDefault(slice, 0);
            RecaptureTarget picked = inSlice.get(idx % inSlice.size());
            rotation.put(slice, idx + 1);
            return picked;
        }
        return null;
    }

    /**
     * Shifts {@code (anchorX, anchorY)} by {@link #RALLY_REAR_SHIFT} cells
     * toward the defender rear along {@code axis} (SOUTH_TO_NORTH &rarr;
     * +y, WEST_TO_EAST &rarr; +x, null axis defaults to +y), then clamps the
     * result into {@code grid}'s bounds. Package-private static so the
     * clamping behavior is unit-testable without a {@link BattleView}.
     */
    static int[] rallyRearShift(int anchorX, int anchorY, TraversalAxis axis, NavigationGrid grid) {
        int rx = anchorX;
        int ry = anchorY;
        if (axis == TraversalAxis.WEST_TO_EAST) {
            rx += RALLY_REAR_SHIFT;
        } else {
            ry += RALLY_REAR_SHIFT;
        }
        rx = Math.max(0, Math.min(grid.getWidth() - 1, rx));
        ry = Math.max(0, Math.min(grid.getHeight() - 1, ry));
        return new int[]{rx, ry};
    }
}
