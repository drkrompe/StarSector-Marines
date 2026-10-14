package com.dillon.starsectormarines.battle.infantry;

import com.dillon.starsectormarines.battle.command.ObjectiveAssignment.FireMission;
import com.dillon.starsectormarines.battle.deployable.FieldMortarService;
import com.dillon.starsectormarines.battle.sim.BattleControl;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.sim.World;
import com.dillon.starsectormarines.battle.squad.BelievedContact;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.turret.StructureDef;
import com.dillon.starsectormarines.battle.turret.TurretCatalogRegistry;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.weapon.WeaponDef;
import com.dillon.starsectormarines.marine.DeployableEmplacementSpec;
import com.dillon.starsectormarines.marine.SpecialEquipmentDef;

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

    private MortarTactics() {}

    /**
     * Begins the set-up channel when the section has halted with something worth a tube in its band.
     * Returns {@code true} when a channel started, so the caller short-circuits the rest of its tick.
     */
    public static boolean tryCommitDeploy(long unit, SpecialEquipmentDef special, BattleControl sim) {
        FieldMortarService mortars = sim.fieldMortars();
        if (mortars.tubeOfCarrier(unit) != null) return false;
        Squad squad = sim.squadOf(unit);
        if (squad == null || squad.timeSinceUnderFire < QUIET_SECONDS) return false;
        // A section told to fire a mission is ordered to set up where it stands: a squadmate still shuffling for a place
        // in the line does not hold the tube back, which the gunner's own settling does.
        boolean onMission = mortars.missionOf(squad.id) != null;
        if (!sim.movement().settled(unit) || (!onMission && !sectionHalted(unit, squad, sim))) return false;
        DeployableEmplacementSpec spec = special.deployableEmplacementSpec();
        StructureDef structure = TurretCatalogRegistry.requireStructure(spec.structureId());
        if (mortars.stashFor(unit, structure) <= 0) return false;
        World world = sim.world();
        Faction faction = sim.identity().faction(unit);
        if (worth(unit, squad, faction, structure.mount.weapon, sim) + missionWorth(unit, squad, faction,
                structure.mount.weapon, sim) < MIN_WORTH) return false;
        if (hostileOnTopOfUs(unit, squad, faction, sim)) return false;
        if (!mortars.beginSetup(unit, faction, world.cellX(unit), world.cellY(unit), squad.id, spec)) {
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
