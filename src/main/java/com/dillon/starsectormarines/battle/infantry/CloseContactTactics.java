package com.dillon.starsectormarines.battle.infantry;

import com.dillon.starsectormarines.battle.contact.CloseContactService;
import com.dillon.starsectormarines.battle.nav.Direction;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleControl;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.sim.World;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.LongBucket;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.marine.SpecialAiPolicy;
import com.dillon.starsectormarines.marine.SpecialEquipmentDef;

/**
 * The typed executor behind {@code SpecialActivation.CLOSE_CONTACT}. It
 * replaces the travelling shot of an ordinary weapon-like special with an
 * adjacent contact test, and it is the only path by which a marine ever
 * attacks at contact: a marine carrying neither a breaching tool nor a blade
 * gains no contact attack of any kind.
 *
 * <p>Two AI policies share the channel and differ only in what counts as a
 * legal contact:
 * <ul>
 *   <li>{@link SpecialAiPolicy#CONTACT_BREACH_CHANNEL} — a hardened hostile
 *       already in contact, or an authored breach point the carrier already
 *       stands beside. The channel is long and visibly interruptible, and its
 *       payload is bounded contact work with no area blast.</li>
 *   <li>{@link SpecialAiPolicy#CONTACT_REACTION_STRIKE} — one living infantry
 *       contact. It cannot select a turret, drone hub, mech, convoy vehicle,
 *       drone, non-combatant, or a wall, and it has no reach through a blocked
 *       edge.</li>
 * </ul>
 *
 * <p>Neither policy authors movement. A carrier uses its tool when ordinary
 * squad movement has already put it in contact; nothing here sets a path,
 * clears one, or retargets the carrier's pursuit, so a contact opportunity can
 * never pull a marine off a firing line or across its maneuver leash.
 *
 * <p>Both reserve their contact for the length of the commitment through
 * {@link CloseContactService}, so several carriers standing on the same
 * casualty do not each spend a payload on it. Death, separation, a lost line
 * of contact, or a higher-priority survival response cancels the commitment
 * before the payload applies, at no cost to the carrier.
 */
public final class CloseContactTactics {

    private CloseContactTactics() {}

    /**
     * Commits the carrier to one legal adjacent contact, returning {@code true}
     * when a channel started (the caller then short-circuits its tick, matching
     * the {@link InfantryUnitPrep#tryOpportunitySpecial} convention).
     */
    public static boolean tryCommit(long unit, SpecialEquipmentDef special, BattleControl sim) {
        if (survivalOverrides(unit, sim)) return false;
        long contact = selectContact(unit, special, sim);
        if (contact == 0L) return false;
        if (!sim.closeContact().tryReserve(unit, contact)) return false;
        World world = sim.world();
        world.setSecondaryActionTimer(unit, special.closeContactSpec().channelSeconds());
        world.setSecondaryFired(unit, false);
        world.setSecondaryAimTargetId(unit, contact > 0L ? contact : 0L);
        return true;
    }

    /**
     * Advances one committed channel by a tick. Legality is re-checked every
     * tick and once more inside the payload seam, so an interruption that
     * arrives on the last tick still cancels rather than landing.
     */
    public static boolean tickChannel(long unit, SpecialEquipmentDef special, BattleControl sim) {
        World world = sim.world();
        world.setSecondaryActionTimer(unit,
                world.secondaryActionTimer(unit) - BattleSimulation.TICK_DT);
        long contact = sim.closeContact().reservedContactOf(unit);
        if (contact == 0L || !isLegalContact(unit, special, contact, sim)
                || survivalOverrides(unit, sim)) {
            cancel(unit, sim);
            return true;
        }
        if (world.secondaryActionTimer(unit) > 0f) return true;

        boolean applied = contact > 0L
                ? sim.applyContactStrike(unit, contact)
                : sim.applyContactBreach(unit,
                        CloseContactService.breachCellX(contact),
                        CloseContactService.breachCellY(contact));
        world.setSecondaryFired(unit, applied);
        if (applied) {
            world.setSecondaryCooldownTimer(unit, special.closeContactSpec().cooldownSeconds());
        }
        sim.closeContact().releaseReservation(unit);
        world.setSecondaryActionTimer(unit, 0f);
        world.setSecondaryAimTargetId(unit, 0L);
        return true;
    }

    /** Drops a commitment without spending the carrier's cooldown. */
    public static void cancel(long unit, BattleControl sim) {
        sim.closeContact().releaseReservation(unit);
        World world = sim.world();
        world.setSecondaryActionTimer(unit, 0f);
        world.setSecondaryFired(unit, false);
        world.setSecondaryAimTargetId(unit, 0L);
    }

    /**
     * Whether the carrier may still hold the contact it committed to. Shared by
     * the per-tick channel guard and by the payload seams, which is what makes
     * "validated before commitment and again before the payload" one rule
     * rather than two drifting copies.
     */
    public static boolean isLegalContact(long unit, SpecialEquipmentDef special,
                                         long contact, BattleView sim) {
        if (contact == 0L || sim.resolveUnit(unit) == 0L) return false;
        if (contact > 0L) return isLegalUnitContact(unit, special, contact, sim);
        return isLegalBreachPoint(unit, special,
                CloseContactService.breachCellX(contact),
                CloseContactService.breachCellY(contact), sim);
    }

    /** Whether an adjacent live actor is a legal contact for this carrier's tool. */
    public static boolean isLegalUnitContact(long unit, SpecialEquipmentDef special,
                                             long target, BattleView sim) {
        if (target <= 0L || sim.resolveUnit(target) == 0L) return false;
        if (target == unit) return false;
        if (!inContactReach(unit, target, special, sim)) return false;
        if (sim.identity().faction(target) == sim.identity().faction(unit)) return false;
        return switch (special.aiPolicy()) {
            case CONTACT_BREACH_CHANNEL -> sim.isHardenedTarget(target);
            case CONTACT_REACTION_STRIKE -> isLivingInfantry(target, sim);
            default -> false;
        };
    }

    /** Whether this cell is an authored breach point the carrier may still cut. */
    public static boolean isLegalBreachPoint(long unit, SpecialEquipmentDef special,
                                             int cellX, int cellY, BattleView sim) {
        if (special.aiPolicy() != SpecialAiPolicy.CONTACT_BREACH_CHANNEL) return false;
        if (!sim.closeContact().isBreachPoint(cellX, cellY)) return false;
        NavigationGrid grid = sim.getGrid();
        if (!grid.inBounds(cellX, cellY) || grid.isWalkable(cellX, cellY)) return false;
        int fromX = sim.world().cellX(unit);
        int fromY = sim.world().cellY(unit);
        return isCardinalNeighbor(fromX, fromY, cellX, cellY);
    }

    /**
     * The contact this carrier would commit to this tick, or {@code 0L}. Unit
     * contacts win over breach work: a live hardened hostile inside arm's reach
     * is the more urgent piece of hard material.
     */
    private static long selectContact(long unit, SpecialEquipmentDef special, BattleControl sim) {
        float range = special.closeContactSpec().contactRange();
        long best = 0L;
        float bestDistanceSq = Float.MAX_VALUE;
        LongBucket scratch = new LongBucket();
        sim.getUnitIndex().gather(sim.world().x(unit), sim.world().y(unit), range, scratch);
        for (int i = 0; i < scratch.size; i++) {
            long candidate = scratch.ids[i];
            if (!isLegalUnitContact(unit, special, candidate, sim)) continue;
            if (sim.closeContact().isReserved(candidate)) continue;
            float dx = sim.world().x(candidate) - sim.world().x(unit);
            float dy = sim.world().y(candidate) - sim.world().y(unit);
            float distanceSq = dx * dx + dy * dy;
            if (distanceSq >= bestDistanceSq) continue;
            best = candidate;
            bestDistanceSq = distanceSq;
        }
        if (best != 0L) return best;
        return selectBreachPoint(unit, special, sim);
    }

    private static long selectBreachPoint(long unit, SpecialEquipmentDef special,
                                          BattleControl sim) {
        if (special.aiPolicy() != SpecialAiPolicy.CONTACT_BREACH_CHANNEL) return 0L;
        if (sim.closeContact().breachPointCount() == 0) return 0L;
        int fromX = sim.world().cellX(unit);
        int fromY = sim.world().cellY(unit);
        for (Direction direction : Direction.CARDINALS) {
            int cellX = fromX + direction.dx;
            int cellY = fromY + direction.dy;
            if (!isLegalBreachPoint(unit, special, cellX, cellY, sim)) continue;
            long key = CloseContactService.breachPointKey(cellX, cellY);
            if (sim.closeContact().isReserved(key)) continue;
            return key;
        }
        return 0L;
    }

    /**
     * Honest physical reach: inside the tool's contact range, with a clear line
     * of contact and a crossable cell boundary. The traversal test is what
     * denies reach through a blocked edge — two cells may both be standable
     * while the thin barrier between them is shut.
     */
    private static boolean inContactReach(long unit, long target,
                                          SpecialEquipmentDef special, BattleView sim) {
        float range = special.closeContactSpec().contactRange();
        float dx = sim.world().x(target) - sim.world().x(unit);
        float dy = sim.world().y(target) - sim.world().y(unit);
        if (dx * dx + dy * dy > range * range) return false;
        int fromX = sim.world().cellX(unit);
        int fromY = sim.world().cellY(unit);
        int toX = sim.world().cellX(target);
        int toY = sim.world().cellY(target);
        if (!sim.getGrid().canTraverseCellStep(fromX, fromY, toX, toY)) return false;
        return sim.getTacticalScoring().hasClearShot(unit, target);
    }

    /**
     * A living person a blade can reach. Hardened classification is consulted
     * first because a convoy vehicle is a combat target that carries no
     * infantry identity to read.
     */
    private static boolean isLivingInfantry(long target, BattleView sim) {
        if (sim.isHardenedTarget(target) || !sim.identity().has(target)) return false;
        UnitType type = sim.identity().type(target);
        if (!type.combatant || type.isDrone()) return false;
        return type != UnitType.RANGE_TARGET;
    }

    /**
     * Whether a higher-priority survival response owns the carrier this tick.
     * A broken squad is already being pulled out of contact by the SURVIVAL
     * goal bucket, and a friendly demolition pack about to go off is a reason
     * to move rather than to keep working.
     */
    private static boolean survivalOverrides(long unit, BattleView sim) {
        Squad squad = sim.squadOf(unit);
        if (squad != null && squad.moraleBroken) return true;
        return sim.satchelCharges().nearestFriendlyHazard(
                sim.identity().faction(unit), sim.world().x(unit), sim.world().y(unit),
                0.9f) != null;
    }

    private static boolean isCardinalNeighbor(int fromX, int fromY, int toX, int toY) {
        return Math.abs(fromX - toX) + Math.abs(fromY - toY) == 1;
    }
}
