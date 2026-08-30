package com.dillon.starsectormarines.battle.air;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleControl;
import com.dillon.starsectormarines.battle.sim.World;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import it.unimi.dsi.fastutil.longs.LongArrayList;

/**
 * Stateless tick consumer that puts a field's airframes on the map, notices
 * when one has been destroyed on its pad, settles the wreck onto the concrete,
 * and counts down a turnaround.
 *
 * <p>Split from {@link AirfieldService} on the project's usual line: the
 * service owns berth state, this owns the actions that state implies. Spawning
 * and removing units is tick-side work and does not belong to a state owner.
 *
 * <p>Placement is driven off the berth rather than commanded: a berth that is
 * {@code PARKED} and has no live airframe on it gets one. That covers the first
 * tick of the battle and the tick a refit completes with the same rule, and it
 * means no caller has to remember to put an aircraft back.
 */
public final class AirfieldSystem {

    private final Faction faction;
    /** Names each airframe as it is placed. Monotonic — an airframe put back after a sortie is a new unit. */
    private int nextAirframeId;

    public AirfieldSystem(Faction faction) {
        this.faction = faction;
    }

    /**
     * Advance every berth. Runs at full tick rate: the work is a walk of at
     * most a handful of berths doing nothing, and a destroyed airframe should
     * stop supplying sorties on the tick it dies rather than up to a cadence
     * period later.
     */
    public void tick(float dt, BattleControl sim, AirfieldService service) {
        if (service == null || service.berths().isEmpty()) return;
        for (AirfieldService.Berth berth : service.berths()) {
            switch (berth.state) {
                case PARKED -> {
                    if (berth.airframeId != 0L && !sim.world().isAlive(berth.airframeId)) {
                        // Burned where it stood. The berth is written off for
                        // the battle, nothing replaces it, and the hulk stays
                        // on the concrete — this is the one death path that
                        // leaves wreckage on a pad, so it is the one that says
                        // so.
                        service.burnedOnPad(berth);
                        settleWreck(sim, berth);
                    } else if (berth.airframeId == 0L) {
                        place(sim, service, berth);
                    }
                }
                case REFITTING -> {
                    berth.refitRemaining -= dt;
                    if (berth.refitRemaining <= 0f) {
                        berth.refitRemaining = 0f;
                        berth.hullHp = service.repaired(berth);
                        berth.state = AirfieldService.BerthState.PARKED;
                        // Placed on the next pass through PARKED above, so
                        // there is one rule for "a parked berth has an
                        // aircraft on it" rather than two.
                    }
                }
                case AWAY -> {
                    // The aircraft is in the air now, so the unit that was
                    // standing in for it comes off the map entirely. Not
                    // killed — a death here would owe a wreck and a casualty
                    // nobody took — and not merely released, which leaves the
                    // hull's row in the world for the render pass to keep
                    // drawing on an empty pad.
                    if (berth.airframeId != 0L) {
                        sim.takeOffTheField(berth.airframeId);
                        berth.airframeId = 0L;
                    }
                }
                case DESTROYED -> {
                    // Terminal. Nothing to place, nothing to count down.
                }
            }
        }
    }

    /**
     * How far from their own cell somebody caught under a settling wreck is
     * allowed to be moved. Chebyshev rings, so this is the apron immediately
     * around the hardstand — a step out from under it, not a relocation.
     */
    private static final int STEP_CLEAR_RADIUS = 3;

    /**
     * Settles the burnt hull onto the hardstand: it is an obstacle from here
     * on, and whoever is still standing where it lands steps clear of it.
     *
     * <p><b>It blocks movement and nothing else.</b> The cells are explicitly
     * marked see-through, because a non-walkable cell is opaque here unless it
     * says otherwise, and a burnt-out airframe is a frame with holes in it. You
     * can see across an apron covered in wreckage and you can shoot across it;
     * what you cannot do is walk through it. That is deliberately not how the
     * intact scenery hulls on civilian berths behave — a whole aircraft is a
     * solid object.
     *
     * <p>Sized to the same ground an aircraft hull covers anywhere else on the
     * map, since it is the same hull; it has simply stopped being able to fly.
     *
     * <p><b>A cell somebody could not be stepped clear of stays open.</b> A
     * wreck that settled on top of a survivor would seal them into a cell they
     * can never leave, and a unit that cannot move is one that stops answering
     * its orders for the rest of the battle. A gap in the wreckage is by far
     * the cheaper wrong.
     */
    private void settleWreck(BattleControl sim, AirfieldService.Berth berth) {
        NavigationGrid grid = sim.getGrid();
        CellTopology topology = sim.getTopology();
        int half = ParkedAircraft.FOOTPRINT_HALF;
        int centerX = berth.centerX;
        int centerY = berth.centerY;

        // Gathered before anybody is moved: displacing mid-walk would have the
        // scan reading positions it has already passed judgement on.
        LongArrayList caught = new LongArrayList();
        World world = sim.world();
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            long u = sim.liveUnitAt(i);
            if (within(world.cellX(u), world.cellY(u), centerX, centerY, half)) caught.add(u);
        }
        for (int i = 0; i < caught.size(); i++) stepClear(sim, caught.getLong(i), centerX, centerY, half);

        for (int y = centerY - half; y <= centerY + half; y++) {
            for (int x = centerX - half; x <= centerX + half; x++) {
                if (!grid.inBounds(x, y)) continue;
                if (occupied(sim, x, y)) continue;
                grid.setWalkable(x, y, false);
                grid.setSeeThrough(x, y, true);
                topology.setVehicle(x, y, true);
            }
        }
        // Cover is derived from the neighbourhood, so the ring around the new
        // obstacle is stale until it is asked again.
        for (int y = centerY - half - 1; y <= centerY + half + 1; y++) {
            for (int x = centerX - half - 1; x <= centerX + half + 1; x++) {
                if (grid.inBounds(x, y)) grid.recomputeCoverAt(x, y);
            }
        }
    }

    /**
     * Moves one unit to the nearest free cell outside the wreck's footprint.
     *
     * <p>Box-spiral outward from where they are standing, so somebody at the
     * edge of the hull steps one cell off it rather than being sent to a
     * canonical corner with everybody else. Leaves the unit exactly where it is
     * when nothing within reach will take it — {@link #settleWreck} then
     * declines to close that cell.
     */
    private void stepClear(BattleControl sim, long unit, int centerX, int centerY, int half) {
        World world = sim.world();
        int fromX = world.cellX(unit);
        int fromY = world.cellY(unit);
        NavigationGrid grid = sim.getGrid();
        for (int r = 1; r <= STEP_CLEAR_RADIUS; r++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dx = -r; dx <= r; dx++) {
                    if (Math.max(Math.abs(dx), Math.abs(dy)) != r) continue;
                    int x = fromX + dx;
                    int y = fromY + dy;
                    if (!grid.inBounds(x, y)) continue;
                    if (within(x, y, centerX, centerY, half)) continue;
                    if (!grid.isWalkable(x, y)) continue;
                    if (occupied(sim, x, y)) continue;
                    world.setCellPos(unit, x, y);
                    return;
                }
            }
        }
    }

    /** Whether {@code (x, y)} is inside the square footprint of half-extent {@code half}. */
    private static boolean within(int x, int y, int centerX, int centerY, int half) {
        return Math.abs(x - centerX) <= half && Math.abs(y - centerY) <= half;
    }

    /** Whether any live unit is standing in {@code (x, y)}. */
    private static boolean occupied(BattleControl sim, int x, int y) {
        World world = sim.world();
        for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
            long u = sim.liveUnitAt(i);
            if (world.cellX(u) == x && world.cellY(u) == y) return true;
        }
        return false;
    }

    /** Stands an airframe on its hardstand, carrying whatever hull the berth is holding. */
    private void place(BattleControl sim, AirfieldService service,
                       AirfieldService.Berth berth) {
        berth.airframeId = sim.spawn(BasedAircraft.create(
                "af" + (nextAirframeId++), faction, berth.airframe,
                berth.centerX, berth.centerY, berth.hullHp));
    }
}
