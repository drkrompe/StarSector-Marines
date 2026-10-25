package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.appearance.LayeredArmorFamily;
import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.battle.power.CommandPower;
import com.dillon.starsectormarines.battle.power.MechSupport;
import com.dillon.starsectormarines.battle.power.VehicleSupport;
import com.dillon.starsectormarines.battle.setup.GroundRosterProfile;
import com.dillon.starsectormarines.battle.setup.InfantryLoadoutRolls;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.vehicle.VehicleDeploymentSpec;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import com.dillon.starsectormarines.engine.ecs.ArchetypeTable;
import com.dillon.starsectormarines.marine.MarineArmorCatalogDef;
import com.dillon.starsectormarines.marine.MarineArmorPattern;
import com.dillon.starsectormarines.ops.RiskLevel;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * What one battle draws, of the art that only some battles use ({@code battle-textures.md}):
 * the infantry armour families its bodies wear, whether the Rav Bloom's rigs are on the field,
 * which sides field mechs, and its vehicle lines.
 *
 * <p>Read from the simulation as it stands before the first tick, plus what its mission has
 * declared it can bring later: the ground roster's reinforcement kit, a swarm's strains, a mech
 * drop allowance, the command powers' payloads. What none of those declare is not in the set,
 * and is loaded the first time it is drawn — a hitch rather than a blank, and in the dev build a
 * log line saying the set missed it.
 *
 * <p>GL-free and pure, so it can be asked of a battle no screen is showing.
 */
public record BattleTextureSet(Set<LayeredArmorFamily> families, boolean ravRigs,
                               Set<Faction> mechSides, Set<VehicleType> vehicles) {

    public BattleTextureSet {
        families = Collections.unmodifiableSet(families.isEmpty()
                ? EnumSet.noneOf(LayeredArmorFamily.class) : EnumSet.copyOf(families));
        mechSides = Collections.unmodifiableSet(mechSides.isEmpty()
                ? EnumSet.noneOf(Faction.class) : EnumSet.copyOf(mechSides));
        vehicles = Collections.unmodifiableSet(vehicles.isEmpty()
                ? EnumSet.noneOf(VehicleType.class) : EnumSet.copyOf(vehicles));
    }

    /** The swarm's bodies: spawned by bores during the battle, so known only by the strains. */
    private static final Set<LayeredArmorFamily> SWARM_FAMILIES = EnumSet.of(
            LayeredArmorFamily.XENO, LayeredArmorFamily.XENO_BRUTE, LayeredArmorFamily.XENO_SPITTER);

    public static BattleTextureSet of(BattleSimulation sim) {
        Set<LayeredArmorFamily> families = EnumSet.noneOf(LayeredArmorFamily.class);
        Set<Faction> mechSides = EnumSet.noneOf(Faction.class);
        Set<VehicleType> vehicles = EnumSet.noneOf(VehicleType.class);
        boolean rav = addField(sim, families, mechSides, vehicles);

        // What can arrive later. Reinforcements wear the ground roster's issue, at whatever risk.
        GroundRosterProfile roster = sim.getGroundRoster();
        if (roster != null) {
            for (GroundRosterProfile.ForceTier tier : GroundRosterProfile.ForceTier.values()) {
                for (RiskLevel risk : RiskLevel.values()) {
                    for (MarineArmorCatalogDef armor : roster.issue(tier).armorPatterns(risk)) {
                        if (armor.appearanceFamily() != null) families.add(armor.appearanceFamily());
                    }
                }
            }
        }
        // A colony raises militia posts during the battle, armoured by the generic roll.
        if (sim.getColonyBreach() != null) {
            for (MarineArmorPattern armor : InfantryLoadoutRolls.defenderArmors(UnitType.MILITIA)) {
                families.add(armor.layeredFamily());
            }
        }
        if (sim.getReinforcementService() != null && sim.getReinforcementService().dispatchesConvoys()) {
            vehicles.add(VehicleType.HEAVY_APC);
        }
        if (swarmCanArrive(sim)) {
            families.addAll(SWARM_FAMILIES);
            rav = true;
        }
        for (Faction side : Faction.values()) {
            if (sim.mechDrops().lancesRemaining(side) > 0) mechSides.add(side);
        }
        if (sim.getCommandPowerService() != null) {
            for (CommandPower power : sim.getCommandPowerService().getAvailablePowers()) {
                if (power instanceof MechSupport support) {
                    if (!support.deployments().isEmpty()) mechSides.add(Faction.MARINE);
                    for (VehicleDeploymentSpec spec : support.vehicles()) vehicles.add(spec.type());
                } else if (power instanceof VehicleSupport support) {
                    for (VehicleDeploymentSpec spec : support.deployments()) vehicles.add(spec.type());
                }
            }
        }
        return new BattleTextureSet(families, rav, mechSides, vehicles);
    }

    /** Whether the swarm can come onto the field later: reinforcements configured, or bores open. */
    private static boolean swarmCanArrive(BattleSimulation sim) {
        return sim.isSwarmReinforcementConfigured() || !sim.swarmBores().bores().isEmpty();
    }

    /**
     * Whether Rav Bloom creep can spread in this battle, so its blight stages may be drawn: creep is
     * already down, a colony breach can erupt, or the swarm is on the field or can arrive.
     */
    public static boolean creepCanSpread(BattleSimulation sim) {
        if (sim.creepFieldIfPresent() != null || sim.getColonyBreach() != null || swarmCanArrive(sim)) return true;
        for (int i = 0; i < sim.liveUnitCount(); i++) {
            UnitType type = sim.identity().type(sim.liveUnitAt(i));
            if (type == UnitType.SWARM_RUNNER || type == UnitType.SWARM_BRUTE || type == UnitType.SWARM_MATRIARCH) {
                return true;
            }
        }
        return false;
    }

    /**
     * Only what stands on the field now, with nothing a mission could bring later: the set of a
     * scene that is not a battle, such as the company deck ({@code battle-textures.md}).
     */
    public static BattleTextureSet onField(BattleSimulation sim) {
        Set<LayeredArmorFamily> families = EnumSet.noneOf(LayeredArmorFamily.class);
        Set<Faction> mechSides = EnumSet.noneOf(Faction.class);
        Set<VehicleType> vehicles = EnumSet.noneOf(VehicleType.class);
        boolean rav = addField(sim, families, mechSides, vehicles);
        return new BattleTextureSet(families, rav, mechSides, vehicles);
    }

    /** Adds what is on the field; whether a Rav Bloom rig is among it. */
    private static boolean addField(BattleSimulation sim, Set<LayeredArmorFamily> families,
                                    Set<Faction> mechSides, Set<VehicleType> vehicles) {
        boolean rav = false;

        // Every layered body on the field, riders aboard vehicles included: they draw once they deboard.
        BattleComponents c = sim.getBattleComponents();
        for (ArchetypeTable table : sim.getEntityWorld().matched(c.layeredSprites)) {
            int[] body = table.ints(c.LAYERED_ANIMATION, BattleComponents.LAYERED_BODY_FAMILY).array();
            int[] head = table.ints(c.LAYERED_ANIMATION, BattleComponents.LAYERED_HEAD_FAMILY).array();
            for (int r = 0; r < table.rowCount(); r++) {
                addFamily(families, body[r]);
                addFamily(families, head[r]);
            }
        }
        for (int i = 0; i < sim.liveUnitCount(); i++) {
            long id = sim.liveUnitAt(i);
            UnitType type = sim.identity().type(id);
            if (type == null) continue;
            if (type.drawnAsMechLayers()) mechSides.add(sim.identity().faction(id));
            if (type.isSwarm()) rav = true;
        }
        for (long id : sim.getConvoyVehicleIds()) {
            VehicleType type = sim.convoy().vehicleType(id);
            if (type != null) vehicles.add(type);
        }
        return rav;
    }

    private static void addFamily(Set<LayeredArmorFamily> families, int ordinal) {
        if (ordinal < 0) return;
        LayeredArmorFamily family = LayeredArmorFamily.fromOrdinal(ordinal);
        if (family != null) families.add(family);
    }
}
