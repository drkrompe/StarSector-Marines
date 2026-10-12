package com.dillon.starsectormarines.battle.warship;

import com.dillon.starsectormarines.DebugOnly;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.gen.TraversalAxis;

import java.util.Locale;

/**
 * The briefing's WARSHIP DEBUG option: a capital ship put over a real mission so
 * its attacks and impacts can be judged against units on the ground.
 *
 * <p>Nothing here is production play. The briefing builds a {@link Spec} only for
 * a debug-generated mission, {@link Spec#OFF} is the default, and
 * {@link #install} is a no-op for it. Placement is a pure function of the map's
 * size, the side and the hull's length, so it is testable without the game.
 */
@DebugOnly
public final class DebugWarship {

    /** How far in from its starting point the ship hovers, in cells. */
    public static final float STATION_INSET_CELLS = 20f;
    /** Area radius used by the director and by manual fire orders, in cells. */
    public static final float AREA_RADIUS_CELLS = 10f;

    /**
     * Flux scale the debug toggle applies: the brawler's share (see
     * {@link Warship#BRAWLER_FLUX_SCALE}), the same a directed ship gets by default.
     */
    public static final float FLUX_SCALE = Warship.BRAWLER_FLUX_SCALE;

    private DebugWarship() {}

    /** Which hull the briefing picked; {@link #OFF} adds nothing. */
    public enum Hull {
        OFF("Off", null, null),
        ONSLAUGHT("Onslaught", "onslaught", "onslaught_Standard"),
        PARAGON("Paragon", "paragon", "paragon_Elite");

        public final String label;
        final String hullId;
        final String variantId;

        Hull(String label, String hullId, String variantId) {
            this.label = label;
            this.hullId = hullId;
            this.variantId = variantId;
        }

        /** The next hull in the briefing's cycle, wrapping; {@code step} may be negative. */
        public Hull cycle(int step) {
            Hull[] all = values();
            return all[Math.floorMod(ordinal() + step, all.length)];
        }
    }

    /** Who steers the ship. */
    public enum Mode {
        DIRECTOR("Director"),
        MANUAL("Manual");

        public final String label;

        Mode(String label) {
            this.label = label;
        }
    }

    /** The briefing's whole choice; {@code flux} puts the ship's guns on its hull's flux. */
    public record Spec(Hull hull, Faction side, Mode mode, boolean flux) {
        public static final Spec OFF = new Spec(Hull.OFF, Faction.MARINE, Mode.DIRECTOR, true);

        public Spec(Hull hull, Faction side, Mode mode) {
            this(hull, side, mode, true);
        }

        public Spec {
            if (hull == null) hull = Hull.OFF;
            if (side == null) side = Faction.MARINE;
            if (mode == null) mode = Mode.DIRECTOR;
        }

        public boolean active() {
            return hull != Hull.OFF;
        }

        public Spec withHull(Hull next) {
            return new Spec(next, side, mode, flux);
        }

        /** Marine (friendly to the player) and Defender (hostile) swap. */
        public Spec withOtherSide() {
            return new Spec(hull, side == Faction.MARINE ? Faction.DEFENDER : Faction.MARINE, mode, flux);
        }

        public Spec withOtherMode() {
            return new Spec(hull, side, mode == Mode.DIRECTOR ? Mode.MANUAL : Mode.DIRECTOR, flux);
        }

        public Spec withOtherFlux() {
            return new Spec(hull, side, mode, !flux);
        }
    }

    /** Where the ship starts, which way it faces, and the ground it hovers over. */
    public record Placement(float x, float y, float facingDegrees, float stationX, float stationY) {}

    /**
     * The centre of {@code side}'s edge of the map, inset by half the hull's
     * length, facing the battle, with a station {@link #STATION_INSET_CELLS}
     * further in. Marines land on the west (or south) edge and defenders hold
     * the opposite one, as {@code MapEntry} has it for everything that arrives.
     * A heading of zero is north and a quarter-turn clockwise is minus ninety,
     * the convention every warship scene uses.
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

    /** The map's axis when the simulation knows it, else the longer dimension. */
    public static TraversalAxis axisOf(BattleSimulation sim) {
        if (sim.getWorksCrews() != null && sim.getWorksCrews().axis() != null) {
            return sim.getWorksCrews().axis();
        }
        return sim.getGrid().getWidth() >= sim.getGrid().getHeight()
                ? TraversalAxis.WEST_TO_EAST : TraversalAxis.SOUTH_TO_NORTH;
    }

    /**
     * Fits and places the picked ship and puts it over the battle. Director mode
     * lets the ship pick the densest hostile ground itself; manual holds fire
     * until {@link #fireAt} is called.
     *
     * @return the ship, or null for {@link Spec#OFF}
     */
    public static Warship install(BattleSimulation sim, Spec spec) throws Exception {
        if (spec == null || !spec.active()) return null;
        Warship ship = Warship.fit(spec.hull().hullId, spec.hull().variantId, spec.side(), 0f, 0f, 0f);
        Placement p = place(spec.side(), axisOf(sim), sim.getGrid().getWidth(), sim.getGrid().getHeight(),
                ship.lengthCells);
        ship.x = p.x();
        ship.y = p.y();
        ship.facingDegrees = p.facingDegrees();
        ship.desiredHeading = p.facingDegrees();
        ship.stationTo(p.stationX(), p.stationY());
        if (spec.mode() == Mode.DIRECTOR) {
            ship.direct(AREA_RADIUS_CELLS, true);
            ship.useDoctrine();
        } else {
            ship.order(WarshipOrder.HoldFire.INSTANCE);
        }
        if (spec.flux()) {
            ship.fluxScale(FLUX_SCALE);
            ship.limitFlux();
        }
        sim.addWarship(ship);
        return ship;
    }

    /** Takes targeting from any director and fires on the cell, bringing the guns to bear. */
    public static void fireAt(Warship ship, float cellX, float cellY) {
        ship.stopDirecting();
        ship.order(new WarshipOrder.FireOnArea(cellX, cellY, AREA_RADIUS_CELLS, true));
    }

    /** Puts the ship on, or takes it off, its hull's flux; the pool starts empty either way. */
    public static void toggleFlux(Warship ship) {
        if (ship.fluxLimited()) {
            ship.unlimitedFlux();
        } else {
            ship.fluxScale(FLUX_SCALE);
            ship.limitFlux();
        }
    }

    /** Steps the ship's missiles to the next preset (faithful, flavoured, cinematic). */
    public static void cycleMissilePreset(Warship ship) {
        ship.setMissilePreset(ship.missilePreset().next());
    }

    /** Takes targeting from any director and holds fire. */
    public static void holdFire(Warship ship) {
        ship.stopDirecting();
        ship.order(WarshipOrder.HoldFire.INSTANCE);
    }

    /** One line for the on-screen readout: hull, order, heading, and what has been fired. */
    public static String statusLine(Warship ship) {
        int rounds = 0;
        int lances = 0;
        int intercepts = 0;
        for (Warship.Mount m : ship.mounts()) {
            intercepts += m.intercepts;
            if (m.pointDefence()) continue;
            if (m.weapon.beam()) lances += m.shotsFired;
            else rounds += m.shotsFired;
        }
        return String.format(Locale.ROOT,
                "WARSHIP %s [%s]  hull %d%%  order %s  heading %d  rounds %d  lances %d  PD kills %d  %s  %s  %s",
                ship.hullId, ship.faction == Faction.MARINE ? "friendly" : "hostile",
                Math.round(ship.hullFraction() * 100f), describe(ship), Math.round(headingOf(ship)),
                rounds, lances, intercepts, fluxReadout(ship), shieldReadout(ship), postureReadout(ship));
    }

    /**
     * {@code posture bombard (no stance, no air threat)}: what the ship is doing and why.
     * A manual order outranks the posture and the line says so; a ship that is
     * holding fire for friendlies says that too.
     */
    static String postureReadout(Warship ship) {
        String base = String.format(Locale.ROOT, "posture %s %ds (%s)", ship.posture().label(),
                Math.round(ship.postureSeconds()), ship.postureReason());
        if (ship.manualOrder()) base = "posture manual (" + base.substring("posture ".length()) + ")";
        if (ship.holdingForFriendlies()) base += " held for friendlies";
        return base;
    }

    /** {@code flux off}, or flux over capacity with the state, and the seconds left when overloaded. */
    static String fluxReadout(Warship ship) {
        if (!ship.fluxLimited()) return "flux off";
        String base = String.format(Locale.ROOT, "flux %d/%d %s", Math.round(ship.flux()),
                Math.round(ship.fluxCapacity()), ship.fluxState().toUpperCase(Locale.ROOT));
        if (!ship.overloaded()) return base;
        return base + String.format(Locale.ROOT, " %.1fs", ship.overloadSecondsRemaining());
    }

    /**
     * {@code shield none}, or the shield's type and state: {@code shield OMNI up 95%}.
     * The percentage is how far the bubble has risen, and a shield that has taken
     * hits says how many and how much flux they made.
     */
    static String shieldReadout(Warship ship) {
        if (ship.shieldType().equals("none")) return "shield none";
        String base = String.format(Locale.ROOT, "shield %s %s", ship.shieldType().toUpperCase(Locale.ROOT),
                ship.shieldState().toUpperCase(Locale.ROOT));
        if (!ship.hasShield()) return base;
        base += String.format(Locale.ROOT, " %d%%", Math.round(ship.shieldLevel() * 100f));
        if (ship.shieldHits() == 0) return base;
        return base + String.format(Locale.ROOT, " hits %d (+%d flux)", ship.shieldHits(),
                Math.round(ship.shieldAbsorbedFlux()));
    }

    /** The hull's facing wrapped to [0, 360), zero being north. */
    static float headingOf(Warship ship) {
        return ((ship.facingDegrees % 360f) + 360f) % 360f;
    }

    private static String describe(Warship ship) {
        String source = ship.isDirected() ? "director " : "";
        if (ship.areas().isEmpty()) return source + "HoldFire";
        StringBuilder out = new StringBuilder(source);
        for (WarshipOrder.FireOnArea area : ship.areas()) {
            if (out.length() > source.length()) out.append(" + ");
            out.append(String.format(Locale.ROOT, "FireOnArea(%.0f,%.0f r%.0f)", area.x(), area.y(), area.radius()));
        }
        return out.toString();
    }
}
