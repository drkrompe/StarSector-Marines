package com.dillon.starsectormarines.battle.infantry;

import com.dillon.starsectormarines.battle.combat.Projectile;
import com.dillon.starsectormarines.battle.deployable.DeployedEmplacement;
import com.dillon.starsectormarines.battle.decision.TacticalScoring;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleControl;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.World;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.marine.DeployableEmplacementSpec;
import com.dillon.starsectormarines.marine.SpecialEquipmentDef;

import java.util.List;

/**
 * Carrier-side executor for the {@code utility-deployable} activation: when a
 * marine decides to set something down, and what completing that channel does.
 *
 * <p>Two policies share this executor because they share the channel and
 * nothing else. An emplacement carrier places when hostile <em>ordnance</em> is
 * already in the air; a cover carrier places when hostile <em>fire</em> is
 * already coming from a direction the ground does not protect them from. Both
 * triggers are local, reactive, and free in a battle where the condition never
 * arises.
 *
 * <p>Like the other special-equipment executors, it authors no path, clears no
 * path, and never retargets its carrier — a marine sets a pod down where they
 * already are, so engagement AI can never abandon a firing line or cross its
 * maneuver leash to manufacture a placement opportunity.
 *
 * <h2>When a marine places one</h2>
 * The trigger is honest, local, and world-reactive: hostile ordnance the
 * emplacement could actually have engaged is <em>already in the air</em> and
 * headed for the ground the marine is standing on. That reads as "incoming —
 * get the pod down before the next salvo" rather than as prescience, and it
 * costs nothing when nobody on the other side is shooting missiles. A marine
 * standing inside a friendly emplacement's existing radius declines, so a squad
 * does not stack four pods on one spot.
 */
public final class DeployableTactics {

    /**
     * Extra reach, in cells, beyond the emplacement's own radius when deciding
     * whether an in-flight warhead counts as "aimed at where I am". A round
     * that lands just outside the bubble would still have been worth covering.
     */
    private static final float THREAT_MARGIN_CELLS = 3f;

    private DeployableTactics() {}

    /**
     * Begins the placement channel when hostile ordnance is inbound on the
     * carrier's own position and no friendly emplacement already covers it.
     * Returns {@code true} when a channel started, so the caller short-circuits
     * the rest of its tick.
     */
    public static boolean tryCommitPlacement(long unit, SpecialEquipmentDef special,
                                             BattleControl sim) {
        DeployableEmplacementSpec spec = special.deployableEmplacementSpec();
        World world = sim.world();
        Faction faction = sim.identity().faction(unit);
        float x = world.x(unit);
        float y = world.y(unit);
        if (sim.pointDefense().isCovered(faction, x, y)) return false;
        float reach = DeployedEmplacement.requireBounded(spec.structureId())
                .mount.weapon.range + THREAT_MARGIN_CELLS;
        if (!hasInboundOrdnance(faction, x, y, reach, sim.snapshotActiveProjectiles())) {
            return false;
        }
        world.setSecondaryActionTimer(unit, spec.deployDuration());
        world.setSecondaryFired(unit, false);
        world.setSecondaryAimTargetId(unit, 0L);
        return true;
    }

    /**
     * Begins the placement channel when the carrier is standing still, taking
     * fire from a hostile it can locate, and the boundary between it and that
     * hostile offers nothing.
     *
     * <p>Every clause is load-bearing. <b>Standing still</b>, because a marine
     * mid-bound does not stop to build — like every other executor here this
     * one authors no path and never retargets its carrier, so the only way a
     * screen goes down is that the marine had already chosen to stay.
     * <b>A locatable hostile</b>, because a screen is directional and a
     * direction has to come from somewhere real. <b>Nothing there already</b>,
     * because the item exists to fix bare ground: a marine beside a wall, a
     * window, or a barricade a squadmate already built has the cover this
     * would have given them, and spending a second one buys nothing.
     */
    public static boolean tryCommitCoverPlacement(long unit, SpecialEquipmentDef special,
                                                  BattleControl sim) {
        World world = sim.world();
        long threat = threatToCoverAgainst(unit, sim);
        if (threat == 0L) return false;
        // A marine already fighting from a position plants when they have
        // stopped, because a screen raised mid-stride is a screen left behind.
        // The two onset moments are the exception and the reason they exist:
        // somebody who has just walked into a room, or who has just been taken
        // by a shooter at the far end of a lane, is in trouble precisely
        // because they are moving, and telling them to finish the walk first
        // is telling them to finish crossing the ground that is the problem.
        if (!sim.movement().settled(unit) && !isOnsetThreat(unit, threat, sim)) {
            return false;
        }
        int cellX = world.cellX(unit);
        int cellY = world.cellY(unit);
        int facing = NavigationGrid.facingFor(world.cellX(threat) - cellX,
                world.cellY(threat) - cellY);
        if (sim.deployedCover().isCovered(cellX, cellY, facing)) return false;
        sim.deployedCover().reserveFacing(unit, facing);
        world.setSecondaryActionTimer(unit, special.deployableCoverSpec().deployDuration());
        world.setSecondaryFired(unit, false);
        world.setSecondaryAimTargetId(unit, 0L);
        return true;
    }

    /**
     * The hostile whose fire the carrier is answering, in the order a marine
     * would rank them: the one it is shooting at, the one that last shot it,
     * the one that has just appeared at close quarters, and the one that has
     * taken it as a target.
     *
     * <p>The first two are contacts the marine already has, and on their own
     * they describe only a fight already under way — which is why a screen used
     * to be something that went up after the shooting started rather than at
     * the moment it would have done the most good. The last two are the onset
     * moments: they are how a marine who has just come through a doorway, or
     * who is being ranged on from the end of a hallway, gets to put something
     * between themselves and it.
     *
     * <p>Still never a search for a reason to build something. Each of the four
     * is a bearing that already exists in the world; none of them goes looking
     * for one.
     */
    private static long threatToCoverAgainst(long unit, BattleControl sim) {
        long engaged = sim.resolveUnit(sim.combat().fireTargetId(unit));
        if (engaged != 0L && sim.world().isAlive(engaged)) return engaged;
        long reflex = sim.resolveUnit(sim.combat().reflexTargetId(unit));
        if (reflex != 0L && sim.world().isAlive(reflex)) return reflex;
        long opening = sim.getTacticalScoring().closeContactOpening(
                unit, TacticalScoring.CLOSE_QUARTERS_CELLS);
        if (opening != 0L) return opening;
        return sim.getTacticalScoring().takenAsATarget(unit);
    }

    /**
     * Whether this threat is one of the two onset moments rather than a fight
     * already joined. Asked only to decide whether a moving marine may stop and
     * plant, so it re-derives rather than being threaded through: the answer is
     * wanted at exactly one place and the queries are bounded.
     */
    private static boolean isOnsetThreat(long unit, long threat, BattleControl sim) {
        long engaged = sim.resolveUnit(sim.combat().fireTargetId(unit));
        if (threat == engaged) return false;
        long reflex = sim.resolveUnit(sim.combat().reflexTargetId(unit));
        return threat != reflex;
    }

    /**
     * Advances an in-progress placement channel. On completion the carrier
     * spends one piece of carried hardware and queues the placement; the
     * service builds it at the next serial pass. Returns {@code true} for as
     * long as the carrier is committed.
     */
    public static boolean tickPlacement(long unit, SpecialEquipmentDef special,
                                        BattleControl sim) {
        World world = sim.world();
        world.setSecondaryActionTimer(unit,
                world.secondaryActionTimer(unit) - BattleSimulation.TICK_DT);
        if (world.secondaryActionTimer(unit) > 0f) return true;
        int ammo = world.secondaryAmmo(unit);
        if (ammo > 0) {
            world.setSecondaryAmmo(unit, ammo - 1);
            sim.telemetry().recordSecondaryUsed(unit);
            if (special.deployableCoverSpec() != null) {
                queueCoverPlacement(unit, special, sim);
            } else {
                sim.pointDefense().queuePlacement(unit, sim.identity().faction(unit),
                        world.cellX(unit), world.cellY(unit),
                        special.deployableEmplacementSpec());
            }
            world.setSecondaryFired(unit, true);
        }
        world.setSecondaryActionTimer(unit, 0f);
        world.setSecondaryAimTargetId(unit, 0L);
        return true;
    }

    /**
     * Hands the completed screen to its service, on the boundary reserved when
     * the carrier committed rather than one re-derived now. The reservation is
     * released either way, so an interrupted carrier leaves nothing behind.
     */
    private static void queueCoverPlacement(long unit, SpecialEquipmentDef special,
                                            BattleControl sim) {
        World world = sim.world();
        int facing = sim.deployedCover().reservedFacing(unit);
        sim.deployedCover().releaseFacing(unit);
        if (facing < 0) return;
        sim.deployedCover().queuePlacement(unit, sim.identity().faction(unit),
                world.cellX(unit), world.cellY(unit),
                NavigationGrid.directionForFacing(facing),
                special.deployableCoverSpec());
    }

    /**
     * True when a hostile round that a point-defence emplacement is allowed to
     * engage is currently in flight toward a point within {@code reach} of
     * {@code (x, y)}. Reads the round's authored endpoint, which is where the
     * warhead is actually going — not a belief about the shooter.
     */
    private static boolean hasInboundOrdnance(Faction faction, float x, float y, float reach,
                                              List<Projectile> inFlight) {
        float reachSq = reach * reach;
        for (int i = 0, n = inFlight.size(); i < n; i++) {
            Projectile p = inFlight.get(i);
            if (!p.pointDefenseTarget || p.intercepted) continue;
            if (p.shooterFaction == faction) continue;
            float dx = p.toX - x;
            float dy = p.toY - y;
            if (dx * dx + dy * dy <= reachSq) return true;
        }
        return false;
    }
}
