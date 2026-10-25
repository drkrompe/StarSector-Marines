package com.dillon.starsectormarines.battle.breach;

import com.dillon.starsectormarines.battle.assault.MachineAssault;
import com.dillon.starsectormarines.battle.combat.PointFireAim;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.mech.MechMoveOrderService.ActiveOrder;
import com.dillon.starsectormarines.battle.mech.MechWeaponMount;
import com.dillon.starsectormarines.battle.mech.components.MechLoadoutComponent;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.World;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.LongBucket;
import com.dillon.starsectormarines.battle.weapon.WeaponDef;
import com.dillon.starsectormarines.marine.SatchelChargeSpec;
import com.dillon.starsectormarines.marine.SpecialActivation;
import com.dillon.starsectormarines.marine.SpecialAiPolicy;
import com.dillon.starsectormarines.marine.SpecialEquipmentDef;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Opens a wall when a squad has been told to (`phased-assault.md`, "Capabilities and roles"). Nothing in the game aimed
 * at a wall: a weapon fires at a body, and a round only damages the wall it happens to stop at. A commander's order can
 * now carry a wall cell ({@link ObjectiveAssignment#breaches()}), and this pass spends the squad's kit on it, each
 * piece by its own nature:
 *
 * <ul>
 *   <li>a <b>mech mount</b> that does wall damage, flat or arcing, turns the torso onto the cell and fires at it
 *       when the cell is in its reach and, for a flat round, in its line ({@code HeavyWeapons#fireMountAtPoint});</li>
 *   <li>a carried <b>launcher</b> fires its rocket at the cell, spending a round of its ammunition;</li>
 *   <li>a <b>cutter</b> in a marine's hands cuts the cell when he stands at it;</li>
 *   <li>a <b>satchel</b> in a marine's hands is set on the cell when he stands at it, and cuts every wall in its blast
 *       when its fuse runs out.</li>
 * </ul>
 *
 * <p>The cell an order names may instead be open ground with a hostile <b>structure</b> standing on it (a swarm
 * bore, an emplacement, a fort core). Then the order is to demolish that structure, and only a satchel serves: a
 * carrier within reach of the body sets it on the body through the ordinary charge service, as the contact-range
 * reflex would, with a wall's slack on the reach. Nothing else fires at a body
 * here; the ordinary firing passes already do.
 *
 * <p>Every gate the weapon has anywhere else still applies (cycle, magazine, flux, a barrel not through a wall, cooldown),
 * and a member that has a body to fight uses his weapons on it first: this pass runs after the ordinary firing passes
 * and takes what they left. It only ever fires at a wall cell the order named and that is still standing, so it can not
 * be used to shoot at anything else, and it names no hostile.
 *
 * <p>The same pass serves a mech's standing <b>assault</b> on a garrisonable ({@code MachineAssaults}): when the
 * assault named the wall sheltering a believed occupant this tick, and the chassis has no body to fight, its mounts
 * that do wall damage fire at that wall exactly as they would at an ordered breach.
 *
 * <p>Untuned simplification: a marine's launcher or tool is used without the aim or channel animation an ordinary
 * special goes through, on its ordinary cooldown.
 */
public final class WallBreachSystem {

    /**
     * A tool or charge reaches a wall cell this far beyond its stated contact range. UNTUNED: a squad arrives within its
     * arrival footprint of the place it was sent, not every member at the wall, so the carrier closes the last few cells
     * as part of the work rather than being walked there on his own path.
     */
    private static final float CONTACT_SLACK = 4f;

    private Set<Long> aiming = new HashSet<>();
    private int roundsAtWalls;

    /** Rounds, charges and cuts spent on ordered walls so far in this battle. */
    public int roundsAtWalls() {
        return roundsAtWalls;
    }

    /** One pass over every squad whose order names a wall. */
    public void tick(BattleSimulation sim) {
        NavigationGrid grid = sim.getGrid();
        World world = sim.world();
        Set<Long> aimedNow = new HashSet<>();
        for (Squad squad : sim.getSquads()) {
            ObjectiveAssignment order = squad.assignmentForExecution();
            if (order == null || !order.breaches()) continue;
            int cellX = order.breachCellX();
            int cellY = order.breachCellY();
            if (!grid.inBounds(cellX, cellY)) continue;
            long structure = 0L;
            if (grid.isWalkable(cellX, cellY)) {
                structure = hostileStructureAt(sim, squad.faction, cellX, cellY);
                if (structure == 0L) continue;
            }
            int count = sim.squadMemberCount(squad.id);
            for (int i = 0; i < count; i++) {
                long member = sim.squadMemberAt(squad.id, i);
                if (member == 0L || !world.isAlive(member)) continue;
                if (structure != 0L) {
                    chargeStructure(sim, world, member, structure);
                } else if (world.hasMechLoadout(member)) {
                    if (breachWithMech(sim, grid, world, member, cellX, cellY)) aimedNow.add(member);
                } else {
                    breachWithKit(sim, grid, world, member, cellX, cellY);
                }
            }
        }
        List<Map.Entry<Long, ActiveOrder>> orders = sim.getMechMoveOrderService().hasActiveOrders()
                ? sim.getMechMoveOrderService().activeOrders() : List.of();
        for (Map.Entry<Long, ActiveOrder> entry : orders) {
            MachineAssault assault = entry.getValue().assault();
            long mech = entry.getKey();
            if (assault == null || !assault.hasWall() || aimedNow.contains(mech)
                    || !world.isAlive(mech) || !world.hasMechLoadout(mech) || world.targetId(mech) != 0L) continue;
            int cellX = assault.wallX();
            int cellY = assault.wallY();
            if (!grid.inBounds(cellX, cellY) || grid.isWalkable(cellX, cellY)) continue;
            if (breachWithMech(sim, grid, world, mech, cellX, cellY)) aimedNow.add(mech);
        }
        for (long member : aiming) {
            if (aimedNow.contains(member) || sim.resolveUnit(member) == 0L || !world.hasMechLoadout(member)) continue;
            world.mechLoadout(member).setBreachAim(null);
        }
        aiming = aimedNow;
    }

    /** A hardened hostile body standing on the cell, or 0. */
    private static long hostileStructureAt(BattleSimulation sim, Faction side, int cellX, int cellY) {
        LongBucket near = new LongBucket();
        sim.getUnitIndex().gather(cellX + 0.5f, cellY + 0.5f, 1f, near);
        for (int i = 0; i < near.size; i++) {
            long body = near.ids[i];
            if (sim.world().cellX(body) != cellX || sim.world().cellY(body) != cellY) continue;
            if (!side.hostileTo(sim.identity().faction(body)) || !sim.isHardenedTarget(body)) continue;
            return body;
        }
        return 0L;
    }

    /**
     * A satchel carrier at the named structure sets his charge on it, once no charge is already on it. "At" is the
     * charge's contact range plus the same slack a wall allows, for the same reason: the squad arrives within its
     * footprint of the place it was sent, and the carrier closes the last cells as part of the work.
     */
    private void chargeStructure(BattleSimulation sim, World world, long member, long structure) {
        if (!world.hasSecondaryWeapon(member) || world.hasMechLoadout(member)) return;
        SpecialEquipmentDef kit = world.specialEquipment(member);
        if (kit.activation() != SpecialActivation.UTILITY_SATCHEL
                || world.secondaryCooldownTimer(member) > 0f || world.secondaryActionTimer(member) > 0f
                || sim.satchelCharges().hasChargeForTarget(structure)) return;
        SatchelChargeSpec spec = kit.satchelChargeSpec();
        float distance = (float) Math.hypot(world.x(structure) - world.renderX(member),
                world.y(structure) - world.renderY(member));
        if (distance > spec.contactRange() + CONTACT_SLACK
                || !sim.satchelCharges().tryReserve(member, structure)) return;
        if (!sim.satchelCharges().plant(member, structure, sim.identity().faction(member),
                world.x(structure), world.y(structure), spec)) {
            sim.satchelCharges().releaseReservation(member);
        } else {
            world.setSecondaryCooldownTimer(member, spec.cooldownSeconds());
            sim.noteSecondaryUsed(member);
            roundsAtWalls++;
        }
    }

    /** Aims and fires every mount of the chassis that can reach the cell. Whether any could. */
    private boolean breachWithMech(BattleSimulation sim, NavigationGrid grid, World world, long member,
                                   int cellX, int cellY) {
        MechLoadoutComponent loadout = world.mechLoadout(member);
        float fromX = world.renderX(member);
        float fromY = world.renderY(member);
        float targetX = cellX + 0.5f;
        float targetY = cellY + 0.5f;
        float distance = (float) Math.hypot(targetX - fromX, targetY - fromY);
        PointFireAim aim = new PointFireAim(targetX, targetY);
        boolean reaches = false;
        for (MechWeaponMount mount : loadout.mounts()) {
            if (mount == null) continue;
            WeaponDef weapon = mount.weaponDef();
            if (weapon.wallDamage <= 0 || distance > weapon.range || distance < weapon.minRange) continue;
            boolean flat = weapon.arcHeight <= 0f && !weapon.indirectFire;
            if (flat && !lineReaches(grid, fromX, fromY, targetX, targetY, cellX, cellY)) continue;
            reaches = true;
            // Aim first, fire when the torso is on the cell: the same tick may do both once it is.
            loadout.setBreachAim(aim);
            if (sim.fireMechMountAtPoint(member, mount, aim)) roundsAtWalls++;
        }
        return reaches;
    }

    /** A marine's launcher, cutter or satchel on the cell, if it is in his reach. */
    private void breachWithKit(BattleSimulation sim, NavigationGrid grid, World world, long member,
                               int cellX, int cellY) {
        if (!world.hasSecondaryWeapon(member)) return;
        SpecialEquipmentDef kit = world.specialEquipment(member);
        if (world.secondaryCooldownTimer(member) > 0f || world.secondaryActionTimer(member) > 0f) return;
        float fromX = world.renderX(member);
        float fromY = world.renderY(member);
        float targetX = cellX + 0.5f;
        float targetY = cellY + 0.5f;
        float distance = (float) Math.hypot(targetX - fromX, targetY - fromY);
        switch (kit.activation()) {
            case DIRECT_EXPLOSIVE -> {
                WeaponDef weapon = kit.weaponDef();
                if (weapon.wallDamage <= 0 || world.secondaryAmmo(member) <= 0 || distance > weapon.range
                        || distance < weapon.minRange
                        || !lineReaches(grid, fromX, fromY, targetX, targetY, cellX, cellY)
                        || sim.getTacticalScoring().dangerClose(member, targetX, targetY)) return;
                if (sim.fireSecondaryAtPoint(member, targetX, targetY)) {
                    world.setSecondaryCooldownTimer(member, kit.cooldown());
                    roundsAtWalls++;
                }
            }
            case UTILITY_SATCHEL -> {
                SatchelChargeSpec spec = kit.satchelChargeSpec();
                if (spec.wallBreachDamage() <= 0 || distance > spec.contactRange() + CONTACT_SLACK
                        || sim.satchelCharges().hasChargeAtCell(cellX, cellY)) return;
                if (sim.satchelCharges().plantAtCell(member, cellX, cellY, sim.identity().faction(member), spec)) {
                    world.setSecondaryCooldownTimer(member, spec.cooldownSeconds());
                    sim.noteSecondaryUsed(member);
                    roundsAtWalls++;
                }
            }
            case CLOSE_CONTACT -> {
                if (kit.aiPolicy() != SpecialAiPolicy.CONTACT_BREACH_CHANNEL) return;
                int wallDamage = kit.wallDamage();
                if (wallDamage <= 0 || distance > kit.closeContactSpec().contactRange() + CONTACT_SLACK) return;
                sim.damageCell(cellX, cellY, wallDamage);
                sim.reportContactBreach(member, targetX, targetY);
                world.setSecondaryCooldownTimer(member, kit.closeContactSpec().cooldownSeconds());
                roundsAtWalls++;
            }
            default -> { }
        }
    }

    /**
     * Whether a flat round from here would stop at the ordered cell or one touching it: the first wall in its line is
     * the wall the order means, not another one the round would be spent on.
     */
    private static boolean lineReaches(NavigationGrid grid, float fromX, float fromY, float toX, float toY,
                                       int cellX, int cellY) {
        long packed = grid.firstWallOnLine(fromX, fromY, toX, toY);
        int hitX = (int) packed;
        int hitY = (int) (packed >>> 32);
        if (hitX == -1 && hitY == -1) return true;
        return Math.abs(hitX - cellX) <= 1 && Math.abs(hitY - cellY) <= 1;
    }
}
