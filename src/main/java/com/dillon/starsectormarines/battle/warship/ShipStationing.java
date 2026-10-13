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
     * Where a called ship breaks out of the cloud deck: on the line from its side's edge
     * ({@link #place}) to its station, {@link #CALL_SHORT_CELLS} plus half the hull short of the
     * station and facing it, so the descent happens over the battle and the ship glides the last
     * stretch onto its station rather than crossing the map at hover speed. A station closer to
     * the edge than that keeps the edge start. The point is kept a half hull inside the map.
     */
    public static Placement callIn(Faction side, TraversalAxis axis, int gridW, int gridH, float lengthCells,
                                   float stationX, float stationY) {
        Placement edge = place(side, axis, gridW, gridH, lengthCells);
        float dx = stationX - edge.x();
        float dy = stationY - edge.y();
        float len = (float) Math.hypot(dx, dy);
        float shortBy = CALL_SHORT_CELLS + lengthCells / 2f;
        if (len <= shortBy) return new Placement(edge.x(), edge.y(), edge.facingDegrees(), stationX, stationY);
        float t = (len - shortBy) / len;
        float half = lengthCells / 2f;
        float x = clamp(edge.x() + dx * t, half, gridW - half);
        float y = clamp(edge.y() + dy * t, half, gridH - half);
        return new Placement(x, y, AirBody.facingToward(stationX - x, stationY - y), stationX, stationY);
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
