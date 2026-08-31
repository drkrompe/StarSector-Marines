package com.dillon.starsectormarines.battle.fabrication;

import com.dillon.starsectormarines.battle.ambient.RoomSite;
import com.dillon.starsectormarines.battle.mech.MechMountSlot;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.mech.MechWeaponComponent;
import com.dillon.starsectormarines.battle.mech.components.MechLoadoutComponent;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.setup.GroundRosterProfile;
import com.dillon.starsectormarines.battle.setup.GroundRosterRegistry;
import com.dillon.starsectormarines.battle.setup.StructureWatch;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.gen.Affordance;
import com.dillon.starsectormarines.battle.world.gen.FixtureTask;
import com.dillon.starsectormarines.battle.world.gen.Gantry;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A motor pool turns out the machines of the faction whose motor pool it is.
 *
 * <p>Not the catalog's. A garrison's sheds are its own tooling, jigs and racks
 * of its own parts, so what walks out of one is what was already patrolling past
 * it — the same chassis the lance is drawn from, wearing the same guns. A shed
 * that built the neutral production fit would put a machine on the map that no
 * other machine on the map resembles, and would make every faction's motor pool
 * the same building with a different roof.
 *
 * <p>The two halves are separately losable and are asserted separately: which
 * chassis the shed is tooled for, and what it hangs on the one it finishes.
 */
class AShedBuildsItsOwnFactionsMachinesTest {

    private static final int W = 40;
    private static final int H = 24;
    private static final int BAY_X = 6;
    private static final int BAY_Y = 6;
    private static final int BAY_W = 20;
    private static final int BAY_H = 12;

    /**
     * The shed builds its faction's own cycle, in its faction's own order.
     *
     * <p>Two factions rather than one, because a single list proves only that
     * <em>a</em> list was read. The Path fields Hounds and nothing else;
     * Tri-Tachyon leads with the Sirocco. Both drop the Bulwark, which is a yard
     * job wherever it appears — so the weight rule and the doctrine rule are
     * visible as separate filters rather than as one lucky coincidence.
     */
    @Test
    void aShedIsTooledForTheFactionWhoseShedItIs() {
        assertEquals(List.of(MechVariant.HOUND), tooling("luddic_path"),
                "a Luddic Path motor pool is building something the Path does not field");
        assertEquals(List.of(MechVariant.SIROCCO, MechVariant.HOUND), tooling("tritachyon"),
                "a Tri-Tachyon motor pool is not building the Tri-Tachyon cycle in order");

        for (MechVariant chassis : tooling("hegemony")) {
            assertTrue(chassis.maxStructure <= FabricationService.FIELD_SHED_STRUCTURE_LIMIT,
                    "a field shed is tooled for the " + chassis.displayName
                            + ", which is a yard's work");
        }
    }

    /**
     * A shed with no faction behind it still builds.
     *
     * <p>The control for the filter above. A doctrine read that came back empty
     * would leave a bay with nothing on its stocks for the whole battle, which
     * looks exactly like a bay whose crew have all been killed — the one reading
     * this feature must never be ambiguous about.
     */
    @Test
    void aShedWithNoDoctrineBehindItBuildsTheLightCatalogue() {
        List<MechVariant> neutral = shed(null, Faction.DEFENDER).works.buildable();

        assertFalse(neutral.isEmpty(), "a shed with no doctrine behind it builds nothing at all");
        for (MechVariant chassis : neutral) {
            assertTrue(chassis.maxStructure <= FabricationService.FIELD_SHED_STRUCTURE_LIMIT,
                    "the fallback offers the " + chassis.displayName + " to a field shed");
        }
        assertTrue(neutral.contains(MechVariant.HOUND) && neutral.contains(MechVariant.SIROCCO),
                "the fallback is not the light catalogue but " + neutral);
    }

    /**
     * What drives out is wearing the faction's guns, not the catalog's.
     *
     * <p>Asserted against the neutral fit rather than for a particular weapon
     * alone, so the test fails for the thing it is about — a machine built here
     * being distinguishable from a machine built anywhere else — rather than for
     * a doctrine author changing which cannon the Path favours.
     */
    @Test
    void whatDrivesOutCarriesTheFactionsOwnFit() {
        Shed shed = shed(GroundRosterRegistry.resolve("luddic_path"), Faction.DEFENDER);
        MechLoadoutComponent built = finishOne(shed);

        assertEquals(MechVariant.HOUND, built.variant);
        assertEquals(MechWeaponComponent.DEMOLITION_CANNON,
                built.mount(MechMountSlot.ARMS).component,
                "the Path's shed hung the catalogue's arms on its own machine");
        assertNotEquals(MechVariant.HOUND.createLoadout(MechVariant.HOUND.defaultRole)
                        .mount(MechMountSlot.ARMS).component,
                built.mount(MechMountSlot.ARMS).component,
                "the faction fit and the neutral fit are the same weapon, so this"
                        + " test cannot tell them apart");
    }

    /**
     * Capturing a shed does not re-tool it.
     *
     * <p>The stock is the shed's, not the crew's. Marines who take a motor pool
     * and finish the machine on its stocks finish <em>that</em> machine: it flies
     * their flag, because they built it, and it is wearing the racks it was built
     * out of. Anything else would need a marine mech doctrine that does not exist
     * in data, and would quietly make a captured shed produce hardware from
     * nowhere.
     */
    @Test
    void takingTheShedDoesNotRestockIt() {
        Shed shed = shed(GroundRosterRegistry.resolve("luddic_path"), Faction.MARINE);
        MechLoadoutComponent built = finishOne(shed);

        assertEquals(Faction.MARINE, shed.sim.identity().faction(shed.lastMachine),
                "the machine the marines built came out fighting for the defender");
        assertEquals(MechWeaponComponent.DEMOLITION_CANNON,
                built.mount(MechMountSlot.ARMS).component,
                "a captured Path shed re-tooled itself for whoever walked in");
    }

    /** One shed on a bare map, tooled for a doctrine and worked by a side. */
    private static final class Shed {
        BattleSimulation sim;
        FabricationService works;
        long lastMachine;
    }

    /** What a shed on this faction's map is tooled for. */
    private static List<MechVariant> tooling(String factionId) {
        return shed(GroundRosterRegistry.resolve(factionId), Faction.DEFENDER)
                .works.buildable();
    }

    /**
     * Run until this shed has laid a keel, finish that machine outright, and
     * answer with the loadout the thing that drove out is carrying.
     *
     * <p>The structure is set to whole rather than welded up, because how long a
     * machine takes is a tuning figure and this is about what comes off the
     * stocks when it is done.
     */
    private static MechLoadoutComponent finishOne(Shed shed) {
        run(shed, 2f);
        FabricationService.Works bay = shed.works.bays().get(0);
        assertTrue(bay.hasFrame(), "the crew laid nothing to finish");
        shed.sim.world().setHp(bay.frameId, shed.sim.world().maxHp(bay.frameId));
        run(shed, BattleSimulation.TICK_DT);

        shed.lastMachine = 0L;
        for (int index = 0; index < shed.sim.getRoster().liveCount(); index++) {
            long id = shed.sim.getRoster().get(index);
            if (shed.sim.identity().type(id) == UnitType.HEAVY_MECH) shed.lastMachine = id;
        }
        assertTrue(shed.lastMachine != 0L, "nothing drove out of the bay");
        MechLoadoutComponent loadout = shed.sim.world().mechLoadout(shed.lastMachine);
        assertNotNull(loadout, "the machine that drove out is carrying no weapons at all");
        return loadout;
    }

    private static Shed shed(GroundRosterProfile doctrine, Faction crew) {
        NavigationGrid grid = new NavigationGrid(W, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) grid.setWalkableFloor(x, y);
        }
        CellTopology topology = new CellTopology(W, H);
        for (int dx = 0; dx < BAY_W; dx++) {
            for (int dy = 0; dy < BAY_H; dy++) {
                topology.setRoomPurpose(BAY_X + dx, BAY_Y + dy, RoomPurpose.VEHICLE_BAY);
            }
        }

        BattleSimulation sim = new BattleSimulation(grid, topology);
        // Only one side is present, so the terminal check would end the battle
        // before the first tick and stop the clock.
        sim.setMissionCompletionEnabled(false);

        List<Gantry> berths = List.of(
                new Gantry(BAY_X + 4, BAY_Y + 3, 1, 2, Gantry.Facing.NORTH),
                new Gantry(BAY_X + 12, BAY_Y + 3, 1, 2, Gantry.Facing.NORTH));
        List<FixtureTask> authored = work(berths);
        List<RoomSite> rooms = RoomSite.findAll(topology, W, H);

        Shed shed = new Shed();
        shed.sim = sim;
        shed.works = new FabricationService(berths, authored, rooms, doctrine);
        sim.setFabrication(shed.works);
        StructureWatch.man(sim, crew, rooms, authored, shed.works.berthed(),
                EnumSet.of(RoomPurpose.VEHICLE_BAY), 2, null);
        return shed;
    }

    /** Servicing at each berth, and the stores and readouts the rotation also visits. */
    private static List<FixtureTask> work(List<Gantry> berths) {
        List<FixtureTask> authored = new ArrayList<>();
        for (int berth = 0; berth < berths.size(); berth++) {
            Gantry gantry = berths.get(berth);
            authored.add(FixtureTask.servingBerth(gantry.left() - 1, gantry.centerY,
                    berth, gantry.centerX, gantry.centerY));
            authored.add(FixtureTask.servingBerth(gantry.right() + 1, gantry.centerY,
                    berth, gantry.centerX, gantry.centerY));
            authored.add(FixtureTask.at(gantry.centerX, gantry.top() + 2,
                    Affordance.STOW, gantry.centerX, gantry.top() + 3));
            authored.add(FixtureTask.at(gantry.centerX + 1, gantry.top() + 2,
                    Affordance.READOUT, gantry.centerX + 1, gantry.top() + 3));
        }
        return List.copyOf(authored);
    }

    private static void run(Shed shed, float seconds) {
        int ticks = Math.round(seconds / BattleSimulation.TICK_DT);
        for (int tick = 0; tick < ticks; tick++) {
            shed.sim.advance(BattleSimulation.TICK_DT);
        }
    }
}
