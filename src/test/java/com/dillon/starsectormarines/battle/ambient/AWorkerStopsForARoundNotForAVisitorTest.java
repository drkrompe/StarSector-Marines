package com.dillon.starsectormarines.battle.ambient;

import com.dillon.starsectormarines.battle.command.CommandAuthority;
import com.dillon.starsectormarines.battle.command.CommandDirective;
import com.dillon.starsectormarines.battle.fabrication.FabricationService;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.setup.StructureWatch;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A works crew keeps working with the enemy in the room, and stops when one of
 * them is hit.
 *
 * <p>Proximity is the wrong question for an armed trade with a job. A
 * technician who downed tools because somebody hostile came within fourteen
 * cells would spend the battle standing about in the one part of the map an
 * attack passes through, and the facility they were posted to would stop
 * producing the moment anybody looked at it — a garrison's whole rear going
 * idle for the sight of a scout.
 *
 * <p>Both halves are the test, and the first is the one that would be missed.
 * A crew that stops for a visitor still looks busy most of the time and still
 * produces on a quiet map; what it does is quietly hand an attacker the
 * facility for the price of standing near it.
 */
class AWorkerStopsForARoundNotForAVisitorTest {

    private static final int W = 40;
    private static final int H = 24;
    private static final int BAY_X = 6;
    private static final int BAY_Y = 6;
    private static final int BAY_W = 20;
    private static final int BAY_H = 12;

    /** An enemy standing in the bay does not stop the work. */
    @Test
    void aVisitorInTheRoomDoesNotStopTheWork() {
        Works works = works();
        run(works, 30f);
        float beforeVisitor = built(works);
        assertTrue(beforeVisitor > 0f, "the crew was not working to begin with");

        // Close enough that a proximity rule would have downed tools at once.
        stranger(works, BAY_X + 5, BAY_Y + 5);
        run(works, 30f);

        assertTrue(built(works) > beforeVisitor,
                "the crew stopped working because somebody hostile walked in");
        for (long hand : works.crew) {
            assertFalse(works.sim.ambientTasks().isStoodDown(hand),
                    "a technician downed tools for a visitor who has not fired");
        }
    }

    /** A round that finds one of them does. */
    @Test
    void aRoundThatFindsThemDoes() {
        Works works = works();
        run(works, 30f);

        long hand = works.crew.get(0);
        assertFalse(works.sim.ambientTasks().isStoodDown(hand));
        works.sim.applyDamage(hand, 3f, 1f);
        run(works, BattleSimulation.TICK_DT * 2);

        assertTrue(works.sim.getRoster().isLive(hand), "the test shot them dead instead");
        assertTrue(works.sim.ambientTasks().isStoodDown(hand),
                "a technician took a round and carried on welding");
    }

    /** And they go back to it once nothing else finds them. */
    @Test
    void andTheyGoBackToItOnceItStops() {
        Works works = works();
        run(works, 30f);

        long hand = works.crew.get(0);
        works.sim.applyDamage(hand, 3f, 1f);
        run(works, BattleSimulation.TICK_DT * 2);
        assertTrue(works.sim.ambientTasks().isStoodDown(hand));

        run(works, 20f);

        assertFalse(works.sim.ambientTasks().isStoodDown(hand),
                "the technician never went back to work after one round");
    }

    /**
     * Once they have stopped, they are an ordinary armed unit and shoot back.
     *
     * <p>The reason a sidearm is worth issuing at all. A works crew that downed
     * tools and then stood there would be no better off than one that could not
     * be armed — the pistol has to reach the ordinary firing path, which it does
     * by their being released to their own behaviour rather than by anything
     * here knowing how to shoot.
     */
    @Test
    void andThenTheyShootBack() {
        Works works = works();
        run(works, 30f);

        long hand = works.crew.get(0);
        stranger(works, works.sim.world().cellX(hand) + 3, works.sim.world().cellY(hand));
        works.sim.applyDamage(hand, 3f, 1f);
        run(works, 20f);

        assertTrue(works.sim.getRoster().isLive(hand), "the technician did not survive to fire");
        assertTrue(works.sim.telemetry().roundsFired(hand) > 0,
                "a technician who was shot at stood there with a loaded sidearm");
    }

    /** A technician has something to shoot back with. */
    @Test
    void aTechnicianCarriesASidearm() {
        Works works = works();

        for (long hand : works.crew) {
            UnitType type = works.sim.identity().type(hand);
            assertTrue(type.combatant,
                    "the works crew turned out as " + type + ", which cannot fire at all");
            assertTrue(works.sim.world().hasCombat(hand), "no weapon on a works hand");
        }
        assertTrue(UnitType.TECHNICIAN.attackRange < UnitType.MILITIA.attackRange,
                "a sidearm out-ranges a militia rifle");
        assertTrue(UnitType.TECHNICIAN.attackDamage < UnitType.MILITIA.attackDamage,
                "a sidearm hits harder than a militia rifle");
    }

    /**
     * The crew is a squad, and it is nobody's to send anywhere.
     *
     * <p>Two facts a sidearm needs and one it does not. Target acquisition runs
     * off the squad, so a technician in none is a person holding a pistol and
     * watching. And a squad left unowned is one mission command may take — the
     * commander would be right to, and the shed would stop producing because its
     * technicians had been sent to hold a road.
     */
    @Test
    void theCrewIsASquadThatNobodyElseCommands() {
        Works works = works();

        Set<Integer> watches = new HashSet<>();
        for (long hand : works.crew) {
            Squad crew = works.sim.squadOf(hand);
            assertNotNull(crew,
                    "a technician is in no squad, so they will never shoot at anything");
            watches.add(crew.id);
        }
        // One per trade rather than one per building: a bay's stores are the
        // storekeeper's watch and its gantries are the technicians', and they
        // walk different rotations.
        assertFalse(watches.isEmpty());

        for (int watch : watches) {
            CommandDirective directive = works.sim.getSquadCommandDirective(watch);
            assertNotNull(directive, "a works watch is unowned and can be tasked away");
            assertEquals(CommandAuthority.SCRIPTED, directive.authority(),
                    "a works watch is claimed at " + directive.authority()
                            + ", which mission command outranks or can take");
        }
    }

    /** One shed, its crew, and a battle to tick them in. */
    private record Works(BattleSimulation sim, FabricationService fabrication, List<Long> crew) { }

    private static Works works() {
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
        sim.setMissionCompletionEnabled(false);

        List<Gantry> berths = List.of(
                new Gantry(BAY_X + 4, BAY_Y + 3, 1, 2, Gantry.Facing.NORTH),
                new Gantry(BAY_X + 12, BAY_Y + 3, 1, 2, Gantry.Facing.NORTH));
        List<FixtureTask> authored = work(berths);
        List<RoomSite> rooms = RoomSite.findAll(topology, W, H);
        FabricationService fabrication = new FabricationService(berths, authored, rooms);
        sim.setFabrication(fabrication);

        List<Long> crew = StructureWatch.man(sim, Faction.DEFENDER, rooms, authored,
                fabrication.berthed(), EnumSet.of(RoomPurpose.VEHICLE_BAY), 2, null);
        assertFalse(crew.isEmpty(), "nobody was taken on");
        return new Works(sim, fabrication, crew);
    }

    /**
     * One hostile standing in the bay, armed and doing nothing.
     *
     * <p>Armed on purpose: the policy this replaces looked for a combatant in
     * range, so an unarmed intruder would have been ignored under both rules and
     * proved nothing.
     */
    private static void stranger(Works works, int x, int y) {
        works.sim.spawn(new EntitySpec("intruder", Faction.MARINE, UnitType.MARINE, x, y));
    }

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

    /** Structure the bay has put on a machine, standing and finished alike. */
    private static float built(Works works) {
        FabricationService.Works bay = works.fabrication.bays().get(0);
        float standing = bay.hasFrame() ? works.sim.world().hp(bay.frameId) : 0f;
        return standing + bay.completed * bay.chassis.maxStructure;
    }

    private static void run(Works works, float seconds) {
        int ticks = Math.max(1, Math.round(seconds / BattleSimulation.TICK_DT));
        for (int tick = 0; tick < ticks; tick++) {
            works.sim.advance(BattleSimulation.TICK_DT);
        }
    }
}
