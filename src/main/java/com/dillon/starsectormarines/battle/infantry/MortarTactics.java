package com.dillon.starsectormarines.battle.infantry;

import com.dillon.starsectormarines.battle.command.ObjectiveAssignment.FireMission;
import com.dillon.starsectormarines.battle.deployable.FieldMortarService;
import com.dillon.starsectormarines.battle.deployable.MortarSections;
import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.sim.BattleControl;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.sim.World;
import com.dillon.starsectormarines.battle.squad.BelievedContact;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.squad.SquadFormUpSystem;
import com.dillon.starsectormarines.battle.turret.StructureDef;
import com.dillon.starsectormarines.battle.turret.TurretCatalogRegistry;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.weapon.WeaponDef;
import com.dillon.starsectormarines.marine.DeployableEmplacementSpec;
import com.dillon.starsectormarines.marine.SpecialAiPolicy;
import com.dillon.starsectormarines.marine.SpecialEquipmentDef;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Carrier-side executor for the {@link com.dillon.starsectormarines.marine.SpecialAiPolicy#CREW_SERVED_FIRE_SUPPORT
 * crew-served fire support} policy: when a gunner sets his tube down, and what he does while it is
 * down.
 *
 * <p>The decision is the section's, not the gunner's alone, and it is made from what the section
 * believes. A tube is set down when all of these hold:
 * <ul>
 *   <li>the section has <b>halted</b> — the gunner and everyone in his squad within reach are
 *       settled — so setting up never diverts a bound in progress;</li>
 *   <li>the squad <b>believes</b> in worthwhile targets inside the tube's band: beyond its dead
 *       zone, inside its reach. A hardened target (a nest, a hub, a machine) is worth a tube on
 *       its own; soft ones are worth it in numbers;</li>
 *   <li>nobody the squad believes in is close enough to be on top of the tube;</li>
 *   <li>the squad is not under fire this second.</li>
 * </ul>
 *
 * <p>Like every other special-equipment executor it authors no path and never retargets its
 * carrier: the gunner sets down where he already stands. While the tube stands he is frozen at it
 * (the channel's short-circuit), which is the cost of the tube's reach: he cannot shoot back with
 * his rifle until it is lifted. What the tube shoots, and when it is lifted, belongs to
 * {@link FieldMortarService}.
 */
public final class MortarTactics {

    /** Smallest believed-target weight that justifies a tube: one hardened target, or two soft ones. UNTUNED. */
    static final int MIN_WORTH = 2;
    /** Weight of a hardened believed target (nest, hub, machine). */
    static final int HARDENED_WEIGHT = 3;
    /** Weight of a soft believed target. */
    static final int SOFT_WEIGHT = 1;
    /** Cells inside the tube's maximum range a target must be, so a fleeing one is not already out of reach. */
    static final float RANGE_MARGIN_CELLS = 2f;
    /** Cells past the dead zone a target must be, so a closing one is not already inside it. */
    static final float DEAD_ZONE_MARGIN_CELLS = 3f;
    /** Lowest belief confidence that counts as knowing where somebody is. */
    static final float MIN_CONFIDENCE = 0.3f;
    /** Seconds without taking fire before a section sets up. UNTUNED. */
    static final float QUIET_SECONDS = 3f;
    /** Cells from the gunner within which a squadmate must have settled for the section to count as halted. */
    static final float HALT_REACH_CELLS = 10f;

    /** Ticks between scans for a squad to open a section on. */
    static final int SECTION_SCAN_TICKS = 10;

    private MortarTactics() {}

    /**
     * Begins the set-up channel when the section has halted with something worth a tube in its band.
     * Returns {@code true} when a channel started, so the caller short-circuits the rest of its tick.
     */
    public static boolean tryCommitDeploy(long unit, SpecialEquipmentDef special, BattleControl sim) {
        Squad squad = sim.squadOf(unit);
        if (squad == null) return false;
        // A section told to fire a mission is ordered to set up where it stands: a squadmate still shuffling for a place
        // in the line does not hold the tube back, which the gunner's own settling does.
        boolean onMission = sim.fieldMortars().missionOf(squad.id) != null;
        if (!sim.movement().settled(unit) || (!onMission && !sectionHalted(unit, squad, sim))) return false;
        return commit(unit, special, squad, sim);
    }

    /**
     * The same commitment for a gunner whose squad has opened a {@link MortarSections section}: the squad is leashed to
     * him, so he has only to be standing, and his squadmates' settling is not asked of him.
     */
    static boolean tryCommitForSection(long unit, SpecialEquipmentDef special, Squad squad, BattleControl sim) {
        if (!sim.movement().settled(unit)) return false;
        return commit(unit, special, squad, sim);
    }

    private static boolean commit(long unit, SpecialEquipmentDef special, Squad squad, BattleControl sim) {
        FieldMortarService mortars = sim.fieldMortars();
        if (!worthASetUp(unit, special, squad, sim)) return false;
        DeployableEmplacementSpec spec = special.deployableEmplacementSpec();
        World world = sim.world();
        Faction faction = sim.identity().faction(unit);
        if (!mortars.beginSetup(unit, faction, world.cellX(unit), world.cellY(unit), squad.id, spec, special)) {
            return false;
        }
        world.setSecondaryActionTimer(unit, spec.deployDuration());
        world.setSecondaryFired(unit, false);
        world.setSecondaryAimTargetId(unit, 0L);
        return true;
    }

    /**
     * Holds the gunner at his tube for as long as it stands. The channel's timer is kept alive so the
     * committed-aim reflex keeps short-circuiting his tick; when the tube is gone (lifted, shot out
     * or never registered) the channel is dropped and his ordinary behaviour resumes the same tick.
     */
    public static boolean tickServing(long unit, BattleControl sim) {
        World world = sim.world();
        if (sim.fieldMortars().tubeOfCarrier(unit) == null) {
            world.setSecondaryActionTimer(unit, 0f);
            world.setSecondaryAimTargetId(unit, 0L);
            return false;
        }
        world.setSecondaryActionTimer(unit, 1f);
        return true;
    }

    /**
     * Whether a gunner standing where he is should set a tube down: every gate of {@link #tryCommitDeploy} except the ones
     * about movement. The tube is not already down, the squad has been quiet, there are rounds, something worth a tube is in
     * the band (believed, or on an assigned mission) and nobody is on top of him.
     */
    static boolean worthASetUp(long unit, SpecialEquipmentDef special, Squad squad, BattleControl sim) {
        FieldMortarService mortars = sim.fieldMortars();
        if (mortars.tubeOfCarrier(unit) != null) return false;
        if (squad.timeSinceUnderFire < QUIET_SECONDS) return false;
        StructureDef structure = TurretCatalogRegistry.requireStructure(special.deployableEmplacementSpec().structureId());
        if (mortars.stashFor(unit, structure) <= 0) return false;
        Faction faction = sim.identity().faction(unit);
        if (worth(unit, squad, faction, structure.mount.weapon, sim) + missionWorth(unit, squad, faction,
                structure.mount.weapon, sim) < MIN_WORTH) return false;
        return !hostileOnTopOfUs(unit, squad, faction, sim);
    }

    /**
     * The serial scan that opens {@link MortarSections sections}: for each squad whose march has brought something worth a
     * tube into the band of its gunner's tube (or whose assigned fire mission has a target there), crew the tube and leash
     * the rest. Runs every {@link #SECTION_SCAN_TICKS} ticks, and only for a gunner who is moving. A squad under a player's order, in the middle of forming up, or
     * with a marine under direct control is never leashed: a section is the squad's own habit, not an order.
     */
    public static void openSections(BattleControl sim) {
        if (!MortarSections.isEnabled() || sim.getSimTickIndex() % SECTION_SCAN_TICKS != 0) return;
        MortarSections sections = sim.fieldMortars().sections();
        World world = sim.world();
        for (Squad squad : sim.getSquads()) {
            if (squad.aliveMembers <= 0 || !sections.mayOpen(squad.id)) continue;
            if (squad.underPlayerOrder() || squad.controlledMemberId() != 0L || SquadFormUpSystem.holdingAtLandingZone(squad)) {
                continue;
            }
            long gunner = gunnerOf(squad, sim);
            if (gunner == 0L) continue;
            // A gunner already standing still belongs to a squad that is halted of its own accord; the ordinary set-up
            // serves it and nothing needs leashing. A section is for the squad whose march it interrupts.
            if (sim.movement().settled(gunner)) continue;
            if (world.secondaryCooldownTimer(gunner) > 0f || world.secondaryActionTimer(gunner) > 0f) continue;
            SpecialEquipmentDef special = world.specialEquipment(gunner);
            if (!special.hasAvailableUse(world.secondaryAmmo(gunner))) continue;
            if (!worthASetUp(gunner, special, squad, sim)) continue;
            sections.open(squad.id, crewFor(gunner, squad, sim), world.x(gunner), world.y(gunner));
        }
    }

    /** The living squadmate who carries a crew-served tube not yet set down, or 0. */
    private static long gunnerOf(Squad squad, BattleControl sim) {
        World world = sim.world();
        for (int i = 0, n = sim.squadMemberCount(squad.id); i < n; i++) {
            long member = sim.squadMemberAt(squad.id, i);
            if (!world.isAlive(member) || !world.hasSecondaryWeapon(member)) continue;
            if (world.specialEquipment(member).aiPolicy() != SpecialAiPolicy.CREW_SERVED_FIRE_SUPPORT) continue;
            if (sim.fieldMortars().tubeOfCarrier(member) != null) continue;
            return member;
        }
        return 0L;
    }

    /**
     * The tube's crew: the gunner, then his fire team's other members, then the nearest remaining squadmates, to
     * {@link MortarSections#CREW_SIZE}. Rejoining stragglers are left out: they are still on their way to the squad.
     */
    static long[] crewFor(long gunner, Squad squad, BattleControl sim) {
        World world = sim.world();
        int team = sim.squad().fireTeamIndex(gunner);
        Map<Long, Float> keys = new HashMap<>();
        for (int i = 0, n = sim.squadMemberCount(squad.id); i < n; i++) {
            long member = sim.squadMemberAt(squad.id, i);
            if (member == gunner || !world.isAlive(member) || squad.isRejoining(member)) continue;
            float dx = world.x(member) - world.x(gunner);
            float dy = world.y(member) - world.y(gunner);
            keys.put(member, (sim.squad().fireTeamIndex(member) == team ? 0f : 1_000_000f) + dx * dx + dy * dy);
        }
        List<Long> order = new ArrayList<>(keys.keySet());
        order.sort(Comparator.<Long>comparingDouble(keys::get).thenComparingLong(Long::longValue));
        int take = Math.min(MortarSections.CREW_SIZE - 1, order.size());
        long[] crew = new long[take + 1];
        crew[0] = gunner;
        for (int c = 1; c <= take; c++) crew[c] = order.get(c - 1);
        return crew;
    }

    /**
     * One marine's part in his squad's section, run as a reflex. The gunner, while the tube is not yet committed, halts
     * and sets it down. Everybody else stands near the tube: the crew close, the rest of the squad inside the leash,
     * walking back to it if they are outside, firing at what is already in range with a clear shot and starting nothing.
     * Returns whether the marine spent the tick. A marine whose fire team has broken is not held: the morale law comes first.
     */
    public static boolean serveSection(long unit, Squad squad, BattleControl sim) {
        if (squad == null || !MortarSections.isEnabled()) return false;
        MortarSections.Section section = sim.fieldMortars().sections().sectionOf(squad.id);
        if (section == null || squad.isRejoining(unit)) return false;
        if (squad.fireTeamBroken(sim.squad().fireTeamIndex(unit))) return false;
        World world = sim.world();
        if (unit == section.gunner() && section.stage() == MortarSections.Stage.OPENING
                && sim.fieldMortars().tubeOfCarrier(unit) == null) {
            if (!sim.movement().settled(unit)) sim.clearPath(unit);
            if (tryCommitForSection(unit, world.specialEquipment(unit), squad, sim)) return true;
        }
        float dx = section.anchorX() - world.x(unit);
        float dy = section.anchorY() - world.y(unit);
        float radius = section.holdRadius(unit);
        if (dx * dx + dy * dy > radius * radius) {
            if (sim.movement().mayRepath(unit)) {
                sim.setPath(unit, GridPathfinder.findPath(sim.getGrid(), world.cellX(unit), world.cellY(unit),
                        (int) Math.floor(section.anchorX()), (int) Math.floor(section.anchorY()), sim.getOccupancyMap()));
            }
            sim.advanceMovement(unit);
            return true;
        }
        if (!sim.movement().settled(unit)) sim.clearPath(unit);
        InfantryUnitPrep.tryOpportunityPrimary(unit, sim);
        return true;
    }

    /** The believed targets worth shooting from where the gunner stands: hardened ones weigh more than soft ones. */
    static int worth(long unit, Squad squad, Faction faction, WeaponDef weapon, BattleView sim) {
        float near = weapon.minRange + DEAD_ZONE_MARGIN_CELLS;
        float far = weapon.range - RANGE_MARGIN_CELLS;
        World world = sim.world();
        int total = 0;
        for (BelievedContact contact : squad.believedContacts()) {
            if (contact.confidence() < MIN_CONFIDENCE) continue;
            long id = sim.resolveUnit(contact.unitId());
            if (id == 0L || !faction.hostileTo(sim.identity().faction(id))) continue;
            float dx = world.x(id) - world.x(unit);
            float dy = world.y(id) - world.y(unit);
            float d = (float) Math.sqrt(dx * dx + dy * dy);
            if (d < near || d > far) continue;
            total += sim.isHardenedTarget(id) ? HARDENED_WEIGHT : SOFT_WEIGHT;
        }
        return total;
    }

    /**
     * What an assigned fire mission is worth from where the gunner stands: a body the side knows to be on the ground the
     * squad was told to fire on, in the tube's band, justifies a tube on its own. Zero with no mission or nothing known.
     */
    static int missionWorth(long unit, Squad squad, Faction faction, WeaponDef weapon, BattleControl sim) {
        FireMission mission = sim.fieldMortars().missionOf(squad.id);
        if (mission == null) return 0;
        World world = sim.world();
        if (sim.fieldMortars().underHostileGuns(faction, world.x(unit), world.y(unit), sim)) return 0;
        long target = sim.fieldMortars().pickMissionTarget(mission, faction, world.x(unit), world.y(unit),
                weapon.minRange + DEAD_ZONE_MARGIN_CELLS, weapon.range - RANGE_MARGIN_CELLS, sim);
        return target == 0L ? 0 : HARDENED_WEIGHT;
    }

    private static boolean hostileOnTopOfUs(long unit, Squad squad, Faction faction, BattleView sim) {
        World world = sim.world();
        float limit = FieldMortarService.DANGER_CELLS + 2f;
        float limitSq = limit * limit;
        for (BelievedContact contact : squad.believedContacts()) {
            long id = sim.resolveUnit(contact.unitId());
            if (id == 0L || !faction.hostileTo(sim.identity().faction(id))) continue;
            if (sim.identity().type(id).isTurret()) continue;
            float dx = world.x(id) - world.x(unit);
            float dy = world.y(id) - world.y(unit);
            if (dx * dx + dy * dy <= limitSq) return true;
        }
        return false;
    }

    /** Whether every living squadmate near the gunner has stopped moving. */
    private static boolean sectionHalted(long unit, Squad squad, BattleView sim) {
        World world = sim.world();
        float reachSq = HALT_REACH_CELLS * HALT_REACH_CELLS;
        for (int i = 0, n = sim.squadMemberCount(squad.id); i < n; i++) {
            long member = sim.squadMemberAt(squad.id, i);
            if (member == unit || !world.isAlive(member)) continue;
            float dx = world.x(member) - world.x(unit);
            float dy = world.y(member) - world.y(unit);
            if (dx * dx + dy * dy > reachSq) continue;
            if (!sim.movement().settled(member)) return false;
        }
        return true;
    }
}
