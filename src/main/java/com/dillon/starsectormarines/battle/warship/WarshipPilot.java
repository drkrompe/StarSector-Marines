package com.dillon.starsectormarines.battle.warship;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;

import java.util.function.BiPredicate;

/**
 * The player's hands on a capital: how one tick of pilot input becomes the orders
 * the ship already understands, and which one-shot commands the number keys and
 * the shield key stand for.
 *
 * <p>Nothing here is a second ship model. Flying is {@link WarshipHelm}'s, laying
 * ground is {@link Warship#layArea}, and the commands call the setters the debug
 * panel and the missions call. A pilot cannot raise flux capacity, ordnance or
 * armour, and cannot lay ground the player has not seen.
 */
public final class WarshipPilot {

    /** The size of the ground a pilot lays, in cells: the same the director and the debug orders use. */
    public static final float AREA_RADIUS_CELLS = 10f;

    private WarshipPilot() {}

    /** The one-shot commands a pilot has beside flying and laying ground. */
    public enum Command {
        /** Lay nothing, drop the director's ground and stop the director: the ship holds fire. */
        HOLD_FIRE,
        /** Hand targeting to the ship's own director, or take it back. */
        TOGGLE_DIRECTOR,
        /** Step the missiles through faithful, flavoured and cinematic. */
        CYCLE_MISSILE_PRESET,
        /** Step which batteries may fire at ground: all, heavy only, none. */
        CYCLE_GROUND_FIRE,
        /** Raise the shield, or lower it, leaving its automatic mode. */
        TOGGLE_SHIELD
    }

    /**
     * One tick of the pilot's hands: the travel axes, the cursor's ground point, and which
     * triggers are held. The axes are world axes with north positive.
     */
    public record Hands(float moveX, float moveY, float aimX, float aimY, boolean guns, boolean missiles) {
        public static final Hands NONE = new Hands(0f, 0f, Float.NaN, Float.NaN, false, false);

        public boolean aimKnown() {
            return Float.isFinite(aimX) && Float.isFinite(aimY);
        }
    }

    /**
     * The pilotable ship whose hull stands under {@code (x, y)}, or null. A hull is picked only
     * where no unit was, since it hovers over the ground its people fight on.
     */
    public static Warship pick(Iterable<Warship> ships, float x, float y, Faction player) {
        for (Warship ship : ships) {
            if (eligible(ship, player) && ship.distanceTo(x, y) <= 0f) return ship;
        }
        return null;
    }

    /** Whether a ship may be piloted by {@code player}: a live hull on that side that is not leaving. */
    public static boolean eligible(Warship ship, Faction player) {
        return ship != null && ship.faction == player && !ship.destroyed()
                && !ship.departing() && !ship.departed();
    }

    /**
     * Applies one tick of {@code hands}: flies the hull, and, while a trigger is held with
     * the cursor over ground the player has seen, lays that battery's area there. Ground
     * stays laid when the trigger is let go, and follows the cursor while it is held.
     *
     * @param revealed whether a cell is one the player has observed
     */
    public static void apply(Warship ship, Hands hands, int mapWidth, int mapHeight,
                             BiPredicate<Integer, Integer> revealed) {
        ship.pilotMove(hands.moveX(), hands.moveY());
        if (!hands.aimKnown()) return;
        int cx = (int) Math.floor(hands.aimX());
        int cy = (int) Math.floor(hands.aimY());
        if (cx < 0 || cy < 0 || cx >= mapWidth || cy >= mapHeight || !revealed.test(cx, cy)) return;
        if (hands.guns()) lay(ship, Warship.FireGroup.GUNS, hands.aimX(), hands.aimY());
        if (hands.missiles()) lay(ship, Warship.FireGroup.MISSILES, hands.aimX(), hands.aimY());
    }

    private static void lay(Warship ship, Warship.FireGroup group, float x, float y) {
        if (!ship.hasBattery(group)) return;
        WarshipOrder.FireOnArea before = ship.laidArea(group);
        if (before != null && before.x() == x && before.y() == y) return;
        ship.layArea(group, WarshipOrder.FireOnArea.saturating(x, y, AREA_RADIUS_CELLS, true));
    }

    /** Carries out a one-shot command; false when the ship cannot do it (no shield, no missiles). */
    public static boolean command(Warship ship, Command command) {
        switch (command) {
            case HOLD_FIRE -> {
                ship.clearLaidAreas();
                ship.pilotDirector(false);
                ship.order(WarshipOrder.HoldFire.INSTANCE);
                return true;
            }
            case TOGGLE_DIRECTOR -> {
                ship.pilotDirector(!ship.isDirected());
                return true;
            }
            case CYCLE_MISSILE_PRESET -> {
                if (!ship.hasBattery(Warship.FireGroup.MISSILES)) return false;
                ship.setMissilePreset(ship.missilePreset().next());
                return true;
            }
            case CYCLE_GROUND_FIRE -> {
                ship.cycleGroundFire();
                return true;
            }
            case TOGGLE_SHIELD -> {
                if (!ship.hasShield()) return false;
                ship.raiseShield(!ship.shieldUp());
                return true;
            }
        }
        return false;
    }

    /** Whether a round aimed at hostiles inside {@code area} would reach a friendly: the telegraph's warning colour. */
    public static boolean endangersFriendlies(BattleSimulation sim, Warship ship, WarshipOrder.FireOnArea area) {
        return area != null && WarshipSystem.endangersFriendlies(sim, ship, area,
                WarshipDirector.friendliesOf(sim, ship));
    }
}
