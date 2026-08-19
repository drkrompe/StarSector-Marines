package com.dillon.starsectormarines.battle.command.objective;

import com.dillon.starsectormarines.battle.evacuation.CivilianEvacuationTracker;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.unit.Faction;

/**
 * Marine objective that measures registered mission civilians crossing a
 * dedicated evacuation zone. Ambient civilians are invisible to it because
 * the objective iterates tracker identities, never faction or unit type.
 *
 * <p>The zone is a square centered on {@code (cellX, cellY)} with inclusive
 * cell radius. A registered live civilian becomes evacuated on entry. A
 * registered identity that is no longer live becomes lost. When every member
 * is terminal, the tracker seals: at least one evacuation completes the
 * objective, while a measured zero fails it.
 */
public final class CivilianEvacuationObjective implements Objective {

    private final CivilianEvacuationTracker tracker;
    private final int cellX;
    private final int cellY;
    private final int radius;
    private final boolean requireAnyEvacuated;
    private boolean complete;
    private boolean failed;

    public CivilianEvacuationObjective(CivilianEvacuationTracker tracker,
                                       int cellX, int cellY, int radius) {
        this(tracker, cellX, cellY, radius, true);
    }

    public CivilianEvacuationObjective(CivilianEvacuationTracker tracker,
                                       int cellX, int cellY, int radius,
                                       boolean requireAnyEvacuated) {
        if (tracker == null) {
            throw new IllegalArgumentException("tracker is required");
        }
        if (radius < 0) {
            throw new IllegalArgumentException("radius must not be negative");
        }
        this.tracker = tracker;
        this.cellX = cellX;
        this.cellY = cellY;
        this.radius = radius;
        this.requireAnyEvacuated = requireAnyEvacuated;
    }

    @Override
    public Faction owningFaction() {
        return Faction.MARINE;
    }

    @Override
    public void tick(BattleView sim) {
        if (complete || failed) return;
        for (int i = 0, n = tracker.registeredCount(); i < n; i++) {
            long id = tracker.entityIdAt(i);
            if (tracker.state(id) != CivilianEvacuationTracker.State.ACTIVE) {
                continue;
            }
            if (sim.resolveUnit(id) == 0L) {
                tracker.markLost(id);
                continue;
            }
            int dx = Math.abs(sim.world().cellX(id) - cellX);
            int dy = Math.abs(sim.world().cellY(id) - cellY);
            if (dx <= radius && dy <= radius) {
                tracker.markEvacuated(id);
            }
        }
        if (tracker.activeCount() != 0 || !tracker.seal()) return;
        if (tracker.evacuatedCount() > 0 || !requireAnyEvacuated) {
            complete = true;
        } else {
            failed = true;
        }
    }

    public int cellX() {
        return cellX;
    }

    public int cellY() {
        return cellY;
    }

    public int radius() {
        return radius;
    }

    @Override
    public boolean isComplete() {
        return complete;
    }

    @Override
    public boolean isFailed() {
        return failed;
    }

    @Override
    public String displayName() {
        return "Evacuate civilians";
    }
}
