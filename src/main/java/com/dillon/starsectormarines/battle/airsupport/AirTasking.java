package com.dillon.starsectormarines.battle.airsupport;

import com.dillon.starsectormarines.battle.air.AirOrdnance;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.World;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.LongBucket;
import com.dillon.starsectormarines.battle.unit.UnitType;

/**
 * Which airframe a target calls for.
 *
 * <p>A side that holds both kinds does not send them at random. What stands on the aim point decides, and the same
 * rule serves the AI director, a commander's request and the player's single air-strike power, so the three cannot
 * disagree about what a fighter is for:
 * <ul>
 *   <li><b>Works</b> (a gun nest, a turret, any structure): a bomber. A cannon round opens soft ground and does little
 *       to a hardened position; a stick of bombs is what takes one out.</li>
 *   <li><b>Armour</b> (a mech, or a vehicle plated beyond what the cannon penetrates): a bomber, for the same reason.</li>
 *   <li><b>Soft ground</b> (infantry in the open, a light vehicle): a fighter, the cheap airframe the side replaces
 *       quickly. Only a really massed formation ({@link #MASSED_INFANTRY}) is worth the dear one.</li>
 * </ul>
 * Whichever is preferred, the other is the fallback when the preferred kind is out of stock, so a side with only one
 * kind left still answers; the fighter on works is a poor trade and the caller's risk judgement is what keeps it honest.
 *
 * <p>Read off the world at the aim point, never off the side's idea of it: a caller that has not sighted the point
 * already refused to call (the operator has to see it), so what is classified here is ground the side is entitled to
 * know the character of.
 */
public final class AirTasking {

    /** Cells about the aim point whose hostile occupants decide what it is. */
    public static final float CLASSIFY_RADIUS_CELLS = 5f;
    /** Hostile combatants in the radius from which soft ground is massed enough to want a bomber. UNTUNED. */
    public static final int MASSED_INFANTRY = 12;

    /** What stands on an aim point, as far as the choice of airframe is concerned. */
    public enum Ground {
        /** Nothing hostile that matters: soft by default. */
        OPEN,
        /** A hardened position. */
        WORKS,
        /** A machine a cannon round cannot hurt much. */
        ARMOUR
    }

    /** What stands on a point: its character, and how many hostile combatants are about it. */
    public record Target(Ground ground, int combatants) {}

    private AirTasking() {}

    /** The target {@code side} would be directing air onto at {@code (x, y)}. */
    public static Target classify(BattleSimulation sim, Faction side, float x, float y) {
        World world = sim.world();
        LongBucket scratch = new LongBucket();
        sim.getUnitIndex().gather(x, y, CLASSIFY_RADIUS_CELLS, scratch);
        boolean works = false;
        boolean armour = false;
        int combatants = 0;
        float cannon = AirOrdnance.AUTOCANNON.penetration;
        for (int i = 0; i < scratch.size; i++) {
            long e = scratch.ids[i];
            if (!world.isAlive(e) || !side.hostileTo(sim.identity().faction(e))) continue;
            UnitType type = sim.identity().type(e);
            if (!type.combatant) continue;
            if (type.isTurret() || type == UnitType.DRONE_HUB_STRUCTURE) {
                works = true;
                continue;
            }
            if (type.isMech() || (type == UnitType.GROUND_VEHICLE && world.armorRating(e) > cannon)) {
                armour = true;
                continue;
            }
            if (type == UnitType.BASED_AIRCRAFT) continue;
            combatants++;
        }
        return new Target(works ? Ground.WORKS : armour ? Ground.ARMOUR : Ground.OPEN, combatants);
    }

    /** The kind a target calls for first, before any question of stock. */
    public static AirPoolKind preferred(Target target) {
        return switch (target.ground()) {
            case WORKS, ARMOUR -> AirPoolKind.BOMBER;
            case OPEN -> target.combatants() >= MASSED_INFANTRY ? AirPoolKind.BOMBER : AirPoolKind.FIGHTER;
        };
    }

    /**
     * The kind to send: the preferred one when it is in stock, else the other, else {@code null}.
     *
     * @param bombers  bombers in stock
     * @param fighters fighters in stock
     */
    public static AirPoolKind choose(Target target, int bombers, int fighters) {
        AirPoolKind first = preferred(target);
        int firstStock = first == AirPoolKind.BOMBER ? bombers : fighters;
        if (firstStock > 0) return first;
        AirPoolKind other = first == AirPoolKind.BOMBER ? AirPoolKind.FIGHTER : AirPoolKind.BOMBER;
        int otherStock = other == AirPoolKind.BOMBER ? bombers : fighters;
        return otherStock > 0 ? other : null;
    }

    /** The kind {@code side} would send at {@code (x, y)} from what its pool holds now; {@code null} when it holds nothing. */
    public static AirPoolKind kindAt(BattleSimulation sim, AirPoolService pool, Faction side, float x, float y) {
        return choose(classify(sim, side, x, y), pool.available(side, AirPoolKind.BOMBER),
                pool.available(side, AirPoolKind.FIGHTER));
    }
}
