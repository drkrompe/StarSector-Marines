package com.dillon.starsectormarines.battle.warship;

import com.dillon.starsectormarines.battle.air.AirBody;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.gen.TraversalAxis;

/**
 * Where an overhead ship comes in from. One rule for every way a ship gets over a
 * battle (the Ship on Station power, the briefing's debug picker): the centre of
 * its side's edge of the map, inset by half the hull's length, facing the battle.
 * Pure functions of the map's size, the side and the hull's length, so they are
 * testable without the game.
 */
public final class ShipStationing {

    /** How far in from its starting point a default station lies, in cells. */
    public static final float STATION_INSET_CELLS = 20f;
    /** How far short of its station, beyond half its length, a called ship comes out of the clouds. */
    public static final float CALL_SHORT_CELLS = 12f;

    private ShipStationing() {}

    /** Where the ship starts, which way it faces, and a default station further in. */
    public record Placement(float x, float y, float facingDegrees, float stationX, float stationY) {}

    /**
     * Marines land on the west (or south) edge and defenders hold the opposite one, as
     * {@code MapEntry} has it for everything that arrives. A heading of zero is north and a
     * quarter-turn clockwise is minus ninety, the convention every warship scene uses.
     */
    public static Placement place(Faction side, TraversalAxis axis, int gridW, int gridH, float lengthCells) {
        boolean defender = side == Faction.DEFENDER;
        float half = lengthCells / 2f;
        float cx = gridW / 2f;
        float cy = gridH / 2f;
        if (axis == TraversalAxis.WEST_TO_EAST) {
            float x = defender ? gridW - half : half;
            float inward = defender ? -1f : 1f;
            return new Placement(x, cy, defender ? 90f : -90f, x + inward * STATION_INSET_CELLS, cy);
        }
        float y = defender ? gridH - half : half;
        float inward = defender ? -1f : 1f;
        return new Placement(cx, y, defender ? 180f : 0f, cx, y + inward * STATION_INSET_CELLS);
    }

    /**
     * Where a ship brought into a battle already under way breaks out of the cloud deck, and the
     * heading it comes down on. It descends at a distance and closes under its own handling: square
     * to its side's edge ({@link #place}), on its station's own line across the map, {@link
     * #CALL_SHORT_CELLS} plus a whole hull length short of the station, so the hull is clear of the
     * ground it was called to before it is down. Nearer the edge than that it comes in over the edge
     * itself, its centre on the edge line. It faces its station, so nothing is left to turn after the
     * descent. Across the axis the point is kept a half hull inside the map.
     */
    public static Placement callIn(Faction side, TraversalAxis axis, int gridW, int gridH, float lengthCells,
                                   float stationX, float stationY) {
        boolean defender = side == Faction.DEFENDER;
        float half = lengthCells / 2f;
        float glide = arrivalDistance(lengthCells);
        float x;
        float y;
        if (axis == TraversalAxis.WEST_TO_EAST) {
            y = clamp(stationY, half, gridH - half);
            x = defender ? Math.min(gridW, stationX + glide) : Math.max(0f, stationX - glide);
        } else {
            x = clamp(stationX, half, gridW - half);
            y = defender ? Math.min(gridH, stationY + glide) : Math.max(0f, stationY - glide);
        }
        float dx = stationX - x;
        float dy = stationY - y;
        float facing = Math.hypot(dx, dy) > 1e-3f ? AirBody.facingToward(dx, dy)
                : place(side, axis, gridW, gridH, lengthCells).facingDegrees();
        return new Placement(x, y, facing, stationX, stationY);
    }

    /** How far short of its station a called hull comes down: the glide and a whole hull length. */
    public static float arrivalDistance(float lengthCells) {
        return CALL_SHORT_CELLS + lengthCells;
    }

    private static float clamp(float v, float lo, float hi) {
        return hi < lo ? (lo + hi) / 2f : Math.max(lo, Math.min(hi, v));
    }

    /** The map's axis when the simulation knows it, else the longer dimension. */
    public static TraversalAxis axisOf(BattleSimulation sim) {
        if (sim.getWorksCrews() != null && sim.getWorksCrews().axis() != null) {
            return sim.getWorksCrews().axis();
        }
        return sim.getGrid().getWidth() >= sim.getGrid().getHeight()
                ? TraversalAxis.WEST_TO_EAST : TraversalAxis.SOUTH_TO_NORTH;
    }
}
