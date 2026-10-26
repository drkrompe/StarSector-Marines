package com.dillon.starsectormarines.battle.fort;

import com.dillon.starsectormarines.battle.battery.FortressBattery;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.World;
import com.dillon.starsectormarines.battle.turret.TurretAim.CellMask;
import com.dillon.starsectormarines.battle.unit.Faction;

/**
 * What a fort can see, and for whom: the radar on its fire-control bunker. The bunker is where a fort's sensors live
 * ({@code forts.md}); the dish stands on its roof, above the fort's own works, so the fort watches the ground round it
 * the way a tower does and not through the bunker's walls.
 *
 * <p>Written for sides. The radar observes for whoever holds the bunker, so a fort taken by the other side watches for
 * that side. It is silent while the bunker is contested (nobody holds the net) and for good once the bunker is
 * wrecked. Without the fort's power the dish does not turn and the fort is left with what its watchkeepers see by
 * eye ({@link #EYE_RANGE_CELLS}); the same plant that gives the guns full traverse gives the radar its reach.
 *
 * <p><b>Fair knowledge.</b> The fort sees a point only where a line of sight to it is open over the real ground, inside
 * the range it has now. Nothing here reads a hostile it has no line to. Only the fort's own works are looked over;
 * anything else on the ground (a city wall, a building, a hill) blocks the line as it blocks a rifleman's.
 */
public final class FortSensors {

    /** Reach of the radar on the bunker roof, in cells. UNTUNED: a first pass, longer than a rifleman's eye. */
    public static final float RADAR_RANGE_CELLS = 48f;
    /** Reach of the fort's watchkeepers by eye when the plant is not theirs to draw on, in cells. UNTUNED. */
    public static final float EYE_RANGE_CELLS = 16f;
    /** How far a call on the fort's radio may reach on ground the fort has in sight, which is its radar's reach. */
    public static final float RADIO_RANGE_CELLS = 140f;
    /** Seconds a call holds the fort's net before the airframe is committed. UNTUNED: a console is quicker than a handset. */
    public static final float CALL_SECONDS = 3f;
    /** Seconds before the net can place another call. UNTUNED. */
    public static final float COOLDOWN_SECONDS = 20f;

    private FortSensors() {}

    /** The bunker whose radar this is, or {@code null} for a fort with no fire-control bunker (a shield fort). */
    public static Fort.Place bunker(Fort fort) {
        return fort.place(FortAspect.FIRE_CONTROL);
    }

    /**
     * The side the fort observes for right now, or {@code null} when it observes for nobody: no bunker, the bunker
     * wrecked, the bunker contested, or the platform's guns gone with nothing to say whose they were.
     */
    public static Faction observer(Fort fort) {
        Fort.Place bunker = bunker(fort);
        FortressBattery battery = fort.battery();
        if (bunker == null || battery == null || bunker.wrecked() || !battery.fireControl().operational()) return null;
        return battery.owner();
    }

    /** Whether the fort's radar is watching on behalf of {@code side} (or a side friendly to it). */
    public static boolean observesFor(Fort fort, Faction side) {
        Faction observer = observer(fort);
        return observer != null && side.friendlyTo(observer);
    }

    /** How far the fort sees for the side that holds it: the radar's reach with power, the eye's without. */
    public static float range(Fort fort) {
        Faction observer = observer(fort);
        return observer == null ? 0f : fort.powered(observer) ? RADAR_RANGE_CELLS : EYE_RANGE_CELLS;
    }

    /**
     * Whether the fort has the ground cell {@code (tx, ty)} in sight now: it is inside the fort's reach and no ground other
     * than the fort's own works stands between the bunker and it. The fort's own works are looked over, not through: a
     * point is not seen merely because it lies beyond the wall of a neighbouring building.
     */
    public static boolean sees(BattleSimulation sim, Fort fort, int tx, int ty) {
        Fort.Place bunker = bunker(fort);
        if (bunker == null || observer(fort) == null) return false;
        World world = sim.world();
        long core = bunker.coreId();
        if (!world.isAlive(core)) return false;
        float range = range(fort);
        float dx = tx + 0.5f - world.x(core);
        float dy = ty + 0.5f - world.y(core);
        if (dx * dx + dy * dy > range * range) return false;
        FortFrame frame = fort.frame();
        return lineOpen(sim.getGrid(), world.cellX(core), world.cellY(core), tx, ty, frame == null ? null : frame::isWorks);
    }

    /** A Bresenham line from {@code (x0, y0)} to {@code (x1, y1)}, blocked by structural opacity outside {@code looked}. */
    public static boolean lineOpen(NavigationGrid grid, int x0, int y0, int x1, int y1, CellMask looked) {
        int dx = Math.abs(x1 - x0);
        int dy = Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int err = dx - dy;
        int x = x0;
        int y = y0;
        while (true) {
            boolean endpoint = x == x0 && y == y0 || x == x1 && y == y1;
            if (!endpoint && grid.blocksStructuralLineOfSight(x, y) && (looked == null || !looked.has(x, y))) return false;
            if (x == x1 && y == y1) return true;
            int e2 = err << 1;
            if (e2 > -dy) {
                err -= dy;
                x += sx;
            }
            if (e2 < dx) {
                err += dx;
                y += sy;
            }
        }
    }
}
