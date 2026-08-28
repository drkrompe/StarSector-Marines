package com.dillon.starsectormarines.battle.infantry;

import com.dillon.starsectormarines.battle.combat.Projectile;
import com.dillon.starsectormarines.battle.deployable.DeployedEmplacement;
import com.dillon.starsectormarines.battle.sim.BattleControl;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.World;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.marine.DeployableEmplacementSpec;
import com.dillon.starsectormarines.marine.SpecialEquipmentDef;

import java.util.List;

/**
 * Carrier-side executor for the {@code utility-deployable} activation: when a
 * marine decides to set an emplacement down, and what completing that channel
 * does.
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
     * Advances an in-progress placement channel. On completion the carrier
     * spends one carried emplacement and queues the placement on its own cell;
     * the service mints the entity at the next serial pass. Returns
     * {@code true} for as long as the carrier is committed.
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
            sim.pointDefense().queuePlacement(unit, sim.identity().faction(unit),
                    world.cellX(unit), world.cellY(unit),
                    special.deployableEmplacementSpec());
            world.setSecondaryFired(unit, true);
        }
        world.setSecondaryActionTimer(unit, 0f);
        world.setSecondaryAimTargetId(unit, 0L);
        return true;
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
