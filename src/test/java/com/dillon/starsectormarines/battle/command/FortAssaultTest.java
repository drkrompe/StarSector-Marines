package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.command.AssaultMuster.Body;
import com.dillon.starsectormarines.battle.command.AssaultMuster.Objective;
import com.dillon.starsectormarines.battle.command.AssaultMuster.Order;
import com.dillon.starsectormarines.battle.command.ConquestFrontSnapshot.AssignmentReason;
import com.dillon.starsectormarines.battle.command.DefenceStructureFact.Family;
import com.dillon.starsectormarines.battle.command.DefenceStructureFact.Kind;
import com.dillon.starsectormarines.battle.fort.FortAspect;
import com.dillon.starsectormarines.battle.fort.FortFrame;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.gen.fortress.AssaultApproach.Variant;
import com.dillon.starsectormarines.battle.world.gen.fortress.BatteryFort;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The stamp's assault plan as a unit: the plan placed on the map by the fort's frame, what a side believes of the nests
 * covering a way in, the readiness it adds, and the executor's step transitions, each on a hand-made picture over an
 * open field.
 */
class FortAssaultTest {

    private static final int SIZE = 320;
    private static final Faction M = Faction.MARINE;
    private static final Faction D = Faction.DEFENDER;

    private static CommandTopology openTopology() {
        NavigationGrid grid = new NavigationGrid(SIZE, SIZE);
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) grid.setWalkableFloor(x, y);
        }
        return CommandTopology.freeze(new BattleSimulation(grid, new CellTopology(SIZE, SIZE)));
    }

    private static FortFrame frame(int turns) {
        return new FortFrame(BatteryFort.PLAN, 100, 100, turns);
    }

    private static Objective objective(FortFrame frame) {
        int[] core = frame.cell(BatteryFort.PLATFORM[0], BatteryFort.PLATFORM[1]);
        DefenceStructureFact bunker = new DefenceStructureFact(Kind.FIRE_CONTROL, core[0], core[1], D, D, false, 5,
                core[0], core[1], 1, Family.BATTERY_FORT, new int[0], new int[0], frame);
        return new Objective(1, Family.BATTERY_FORT, core[0], core[1], List.of(bunker), 0, frame);
    }

    private static Body squad(int id, int[] at, boolean machine, boolean demolition) {
        return new Body(id, at[0], at[1], 8, machine, demolition, false, null);
    }

    // ------------------------------------------------------------------------------------------- the plan, placed

    @Test
    void thePlanIsPlacedOnTheMapByTheFortsFrameAtEveryQuarterTurn() {
        for (int turns = 0; turns < 4; turns++) {
            FortAssault.Works works = FortAssault.Works.of(frame(turns));
            assertEquals(3, works.ways.size(), "main gate, postern, breach at turn " + turns);
            assertEquals(4, works.nests.size());
            for (FortAssault.Way way : works.ways) {
                assertEquals(1, Math.abs(way.outX()) + Math.abs(way.outY()), "a cardinal step outward at turn " + turns);
                int depth = 4;
                assertEquals(way.mouthX() - way.outX() * depth, way.entryX(), "entry is inside the way in at turn " + turns);
                assertEquals(way.mouthY() - way.outY() * depth, way.entryY());
            }
        }
    }

    @Test
    void theMainGateFacesWhereTheFrameTurnsIt() {
        FortFrame turned = frame(1);
        FortAssault.Way main = FortAssault.Works.of(turned).ways.get(0);
        int[] across = turned.across();
        int[] down = turned.downward();
        // Local out of the main gate is (-1, 0): minus local x, in world.
        assertEquals(-across[0], main.outX());
        assertEquals(-across[1], main.outY());
        assertFalse(down[0] == main.outX() && down[1] == main.outY());
    }

    // ---------------------------------------------------------------------------------------------- nest beliefs

    @Test
    void aNestNeverSeenIsBelievedLiveAndOneSeenThenGoneUnderWitnessIsBelievedDead() {
        FortAssault.Works works = FortAssault.Works.of(frame(0));
        FortAssault.NestBelief belief = new FortAssault.NestBelief(works.nests.size());
        assertEquals(4, belief.liveCount());
        int[] nest0 = works.nests.get(0);
        List<int[]> witness = List.of(new int[] {nest0[0] - 15, nest0[1] - 10});
        belief.update(works, List.of(), witness);
        assertEquals(4, belief.liveCount(), "never seen alive: still believed there");
        belief.update(works, List.of(new int[] {nest0[0], nest0[1]}), witness);
        assertEquals(4, belief.liveCount(), "seen alive");
        belief.update(works, List.of(), List.of(new int[] {nest0[0] - 80, nest0[1]}));
        assertEquals(4, belief.liveCount(), "no one in sight of it: no reason to think it gone");
        belief.update(works, List.of(), witness);
        assertEquals(3, belief.liveCount(), "seen, now gone, with a squad in sight: dead");
        belief.update(works, List.of(new int[] {nest0[0], nest0[1]}), witness);
        assertEquals(3, belief.liveCount(), "a dead nest stays dead");
    }

    @Test
    void aNestNobodyEverSawAliveIsBelievedDeadOnceASquadHasStoodInClearViewOfItUnansweredLongEnough() {
        FortAssault.Works works = FortAssault.Works.of(frame(0));
        FortAssault.NestBelief belief = new FortAssault.NestBelief(works.nests.size());
        CommandTopology topology = openTopology();
        int[] nest0 = works.nests.get(0);
        List<int[]> squad = List.of(new int[] {nest0[0] - 10, nest0[1] - 8});
        // The garrison stands beside the nest: men are not the nest, and a nest is seen only by an emplacement on it.
        int tick = 1000;
        belief.update(works, List.of(), squad, topology, tick);
        assertEquals(4, belief.liveCount(), "one look is not enough");
        belief.update(works, List.of(), squad, topology, tick + FortAssault.NEST_UNSEEN_TICKS - 1);
        assertEquals(4, belief.liveCount(), "nor is a little less than the wait");
        belief.update(works, List.of(), squad, topology, tick + FortAssault.NEST_UNSEEN_TICKS);
        assertEquals(3, belief.liveCount(), "in clear view, unanswered, for the wait: the lane squads got there first");
        assertFalse(belief.live(0));
    }

    @Test
    void aNestOutOfViewOrSeenAliveIsNotBelievedDeadBySilence() {
        FortAssault.Works works = FortAssault.Works.of(frame(0));
        CommandTopology topology = openTopology();
        int[] nest0 = works.nests.get(0);

        FortAssault.NestBelief far = new FortAssault.NestBelief(works.nests.size());
        List<int[]> distant = List.of(new int[] {nest0[0] - 60, nest0[1]});
        far.update(works, List.of(), distant, topology, 0);
        far.update(works, List.of(), distant, topology, 10_000);
        assertEquals(4, far.liveCount(), "a squad too far to see it learns nothing");

        FortAssault.NestBelief answered = new FortAssault.NestBelief(works.nests.size());
        List<int[]> near = List.of(new int[] {nest0[0] - 10, nest0[1] - 8});
        List<int[]> emplacement = List.of(new int[] {nest0[0], nest0[1]});
        answered.update(works, emplacement, near, topology, 0);
        answered.update(works, emplacement, near, topology, 10_000);
        assertEquals(4, answered.liveCount(), "a nest with a gun still standing on it is live");
    }

    // ------------------------------------------------------------------------------------- readiness: the defence

    @Test
    void theNestsCoveringTheWayInAddToWhatTheCompanyIsBelievedToNeed() {
        FortAssault.Works works = FortAssault.Works.of(frame(0));
        FortAssault.NestBelief belief = new FortAssault.NestBelief(works.nests.size());
        int[] west = {works.ways.get(0).mouthX() - 60, works.ways.get(0).mouthY()};
        FortAssault.Defence all = FortAssault.assess(works, belief, west[0], west[1], false, false);
        assertEquals(Variant.MAIN_GATE, all.way().variant(), "the company that comes from the west takes the west gate");
        assertEquals(2, all.liveCovering());
        assertEquals(2, all.liveOther());
        assertEquals(2 * FortAssault.NEST_COVERING_STRENGTH + 2 * FortAssault.NEST_OTHER_STRENGTH
                + FortAssault.GATE_STRENGTH, all.strength(), 0.001f);
        assertTrue(AssaultMuster.required(Family.BATTERY_FORT, 0, 0, all.strength()) > 2 * AssaultMuster.FORT_BASE_STRENGTH * 0.9,
                "a fort with its nests up asks for roughly twice the bare baseline");
    }

    @Test
    void killingTheNestsOnOneGateMakesThatTheWayInAndLowersTheNeed() {
        FortAssault.Works works = FortAssault.Works.of(frame(0));
        FortAssault.NestBelief belief = new FortAssault.NestBelief(works.nests.size());
        FortAssault.Way main = works.ways.get(0);
        FortAssault.Way postern = works.ways.get(1);
        // Make both seen alive, then take away the main gate's two nests under a witness.
        List<int[]> alive = new ArrayList<>(works.nests);
        List<int[]> witness = new ArrayList<>();
        for (int[] nest : works.nests) witness.add(new int[] {nest[0] - 12, nest[1] - 12});
        belief.update(works, alive, witness);
        List<int[]> survivors = new ArrayList<>();
        for (int i = 0; i < works.nests.size(); i++) if (!main.covering().contains(i)) survivors.add(works.nests.get(i));
        belief.update(works, survivors, witness);
        assertEquals(0, belief.liveCovering(main));
        int[] east = {postern.mouthX() + 40, postern.mouthY() - 40};
        FortAssault.Defence chosen = FortAssault.assess(works, belief, east[0], east[1], false, false);
        assertEquals(Variant.MAIN_GATE, chosen.way().variant(), "the least covered gate wins even from the far side");
        assertEquals(0, chosen.liveCovering());
        assertEquals(2 * FortAssault.NEST_OTHER_STRENGTH + FortAssault.GATE_STRENGTH, chosen.strength(), 0.001f);
    }

    @Test
    void aBreachIsOnlyTakenByACompanyThatCanCutOneAndOnlyWhenItIsLessCoveredThanEveryGate() {
        FortAssault.Works works = FortAssault.Works.of(frame(0));
        FortAssault.NestBelief belief = new FortAssault.NestBelief(works.nests.size());
        int[] from = {works.ways.get(0).mouthX() - 60, works.ways.get(0).mouthY()};
        assertNotEquals(Variant.BREACH, FortAssault.assess(works, belief, from[0], from[1], false, true).way().variant());
        assertNotEquals(Variant.BREACH, FortAssault.assess(works, belief, from[0], from[1], true, false).way().variant());
        FortAssault.Defence both = FortAssault.assess(works, belief, from[0], from[1], true, true);
        if (both.way().variant() == Variant.BREACH) {
            for (FortAssault.Way way : works.ways) {
                if (way.variant() != Variant.BREACH) assertTrue(belief.liveCovering(both.way()) < belief.liveCovering(way));
            }
        }
    }

    private static void assertNotEquals(Object unexpected, Object actual) {
        assertFalse(unexpected.equals(actual), "was " + actual);
    }

    // ---------------------------------------------------------------------------------------- the executor's steps

    /** Everything a pulse needs, with positions the test moves by hand. */
    private static final class Picture {
        final CommandTopology topology = openTopology();
        final FortFrame frame = frame(0);
        final Objective objective = objective(frame);
        final FortAssault.Works works = FortAssault.Works.of(frame);
        final FortAssault.NestBelief belief = new FortAssault.NestBelief(works.nests.size());
        final Map<Integer, Body> bodies = new TreeMap<>();
        final Set<Integer> members = new TreeSet<>();
        final List<String> notes = new ArrayList<>();
        FortAssault.Execution execution;
        int[] start;

        Picture() {
            start = new int[] {works.ways.get(0).mouthX() - 70, works.ways.get(0).mouthY()};
            // Two mech lances and four infantry squads, one with satchels.
            add(1, true, false);
            add(2, true, false);
            add(3, false, false);
            add(4, false, false);
            add(5, false, true);
            add(6, false, false);
            execution = new FortAssault.Execution(works, belief, works.ways.get(0), 0);
        }

        void add(int id, boolean machine, boolean demolition) {
            bodies.put(id, squad(id, new int[] {start[0] - id, start[1] + id}, machine, demolition));
            members.add(id);
        }

        void put(int id, int x, int y) {
            Body b = bodies.get(id);
            bodies.put(id, new Body(id, x, y, b.alive(), b.machine(), b.demolition(), b.inContact(), b.incumbent()));
        }

        Map<Integer, Order> run(int tick) {
            return execution.run(tick, M, objective, members, bodies, topology, notes);
        }
    }

    @Test
    void aCommittedCompanyPositionsItsMachinesAsBaseOfFireAndStagesTheRestOutsideTheNests() {
        Picture p = new Picture();
        Map<Integer, Order> orders = p.run(0);
        assertEquals(FortAssault.Step.POSITION, p.execution.step());
        for (int id : List.of(1, 2)) {
            assertEquals(AssignmentReason.ASSAULT_BASE_OF_FIRE, orders.get(id).reason(), "machine squad " + id + " is the fire element");
            int[] at = {orders.get(id).assignment().targetCellX(), orders.get(id).assignment().targetCellY()};
            double nearest = Double.MAX_VALUE;
            for (int i : p.works.ways.get(0).covering()) {
                nearest = Math.min(nearest, Math.hypot(p.works.nests.get(i)[0] - at[0], p.works.nests.get(i)[1] - at[1]));
            }
            assertEquals(FortAssault.FIRE_STANDOFF_MACHINE, nearest, 3.0, "a mech stands past the nests' reach");
        }
        int fireInfantry = 0;
        for (int id : List.of(3, 4, 5, 6)) {
            if (orders.get(id).reason() == AssignmentReason.ASSAULT_BASE_OF_FIRE) fireInfantry++;
        }
        assertEquals(1, fireInfantry, "a third of four infantry squads, rounded, fires");
        assertFalse(orders.get(5).reason() == AssignmentReason.ASSAULT_BASE_OF_FIRE, "the satchels stay with the assault");
        Order staged = orders.get(6).reason() == AssignmentReason.ASSAULT_STAGE ? orders.get(6) : orders.get(4);
        assertEquals(AssignmentReason.ASSAULT_STAGE, staged.reason());
        for (int[] nest : p.works.nests) {
            assertTrue(Math.hypot(nest[0] - staged.assignment().targetCellX(), nest[1] - staged.assignment().targetCellY())
                    >= FortAssault.ASSEMBLY_STANDOFF - 1, "the assembly is outside the nests' reach");
        }
    }

    @Test
    void suppressionBeginsWhenTheFireElementIsInPositionAndEntryWaitsForTheNestsToDie() {
        Picture p = new Picture();
        Map<Integer, Order> orders = p.run(0);
        for (int id : p.members) {
            p.put(id, orders.get(id).assignment().targetCellX(), orders.get(id).assignment().targetCellY());
        }
        p.run(60);
        assertEquals(FortAssault.Step.SUPPRESS, p.execution.step(), "everyone is where they were sent");
        p.run(300);
        assertEquals(FortAssault.Step.SUPPRESS, p.execution.step(), "the covering nests are believed live: the assault waits");
        int[] cover0 = p.works.nests.get(p.works.ways.get(0).covering().get(0));
        int[] cover1 = p.works.nests.get(p.works.ways.get(0).covering().get(1));
        List<int[]> witnesses = List.of(new int[] {cover0[0] - 14, cover0[1] - 10}, new int[] {cover1[0] - 14, cover1[1] + 10});
        p.belief.update(p.works, List.of(cover0, cover1), witnesses);
        p.run(330);
        assertEquals(FortAssault.Step.SUPPRESS, p.execution.step(), "seen alive is not dead");
        p.belief.update(p.works, List.of(), witnesses);
        Map<Integer, Order> entry = p.run(360);
        assertEquals(FortAssault.Step.ENTRY, p.execution.step(), "both covering nests gone under a witness");
        FortAssault.Way way = p.works.ways.get(0);
        for (int id : List.of(5, 6, 4, 3)) {
            Order order = entry.get(id);
            assertEquals(AssignmentReason.ASSAULT_ENTRY, order.reason());
            assertEquals(way.entryX(), order.assignment().targetCellX());
            assertEquals(way.entryY(), order.assignment().targetCellY());
        }
        for (int id : List.of(1, 2)) {
            assertEquals(AssignmentReason.ASSAULT_ENTRY, entry.get(id).reason(), "the fire element follows once no nest is left to answer");
        }
    }

    @Test
    void suppressionIsBoundedSoACompanyNeverWaitsForeverOnANestItCannotKill() {
        Picture p = new Picture();
        Map<Integer, Order> orders = p.run(0);
        for (int id : p.members) {
            p.put(id, orders.get(id).assignment().targetCellX(), orders.get(id).assignment().targetCellY());
        }
        p.run(60);
        assertEquals(FortAssault.Step.SUPPRESS, p.execution.step());
        p.run(60 + FortAssault.SUPPRESS_MAX_TICKS - 1);
        assertEquals(FortAssault.Step.SUPPRESS, p.execution.step());
        p.run(60 + FortAssault.SUPPRESS_MAX_TICKS);
        assertEquals(FortAssault.Step.ENTRY, p.execution.step(), "the nests were never killed; it goes in on the clock");
        assertEquals(2, p.execution.liveCovering());
    }

    @Test
    void positioningIsBoundedToo() {
        Picture p = new Picture();
        p.run(0);
        p.run(FortAssault.POSITION_TIMEOUT_TICKS - 1);
        assertEquals(FortAssault.Step.POSITION, p.execution.step());
        p.run(FortAssault.POSITION_TIMEOUT_TICKS);
        assertEquals(FortAssault.Step.SUPPRESS, p.execution.step());
    }

    @Test
    void theAssaultElementGoesAtTheAspectOnceItIsThroughTheWayInAndHoldsItWhenItFalls() {
        Picture p = new Picture();
        p.run(0);
        p.run(FortAssault.POSITION_TIMEOUT_TICKS);
        p.run(FortAssault.POSITION_TIMEOUT_TICKS + FortAssault.SUPPRESS_MAX_TICKS);
        assertEquals(FortAssault.Step.ENTRY, p.execution.step());
        FortAssault.Way way = p.works.ways.get(0);
        int tick = FortAssault.POSITION_TIMEOUT_TICKS + FortAssault.SUPPRESS_MAX_TICKS;
        for (int id : List.of(3, 4, 5, 6)) p.put(id, way.entryX(), way.entryY());
        Map<Integer, Order> go = p.run(tick + 30);
        assertEquals(FortAssault.Step.OBJECTIVE, p.execution.step());
        Order demolition = go.get(5);
        assertNotNull(demolition);
        assertEquals(AssignmentReason.ASSAULT_COMMIT, demolition.reason());
        int[] aspect = {p.objective.cellX(), p.objective.cellY()};
        assertTrue(Math.hypot(demolition.assignment().targetCellX() - aspect[0], demolition.assignment().targetCellY() - aspect[1]) <= 6,
                "ordered to the aspect's doorstep");
        // The bunker falls to us: the fort's next pick is its power plant (no longer hostile? the remaining aspect).
        int[] plant = p.frame.cell(BatteryFort.coreCentre(FortAspect.POWER)[0],
                BatteryFort.coreCentre(FortAspect.POWER)[1]);
        DefenceStructureFact bunkerOurs = new DefenceStructureFact(Kind.FIRE_CONTROL, aspect[0], aspect[1], M, D, false, 5,
                aspect[0], aspect[1], 1, Family.BATTERY_FORT, new int[0], new int[0], p.frame);
        DefenceStructureFact plantHostile = new DefenceStructureFact(Kind.FORT_POWER, plant[0], plant[1], D, D, false, 5,
                aspect[0], aspect[1], 1, Family.BATTERY_FORT, new int[0], new int[0], p.frame);
        Objective next = new Objective(1, Family.BATTERY_FORT, aspect[0], aspect[1], List.of(bunkerOurs, plantHostile), 0, p.frame);
        Map<Integer, Order> hold = p.execution.run(tick + 60, M, next, p.members, p.bodies, p.topology, p.notes);
        assertEquals(FortAssault.Step.HOLD, p.execution.step(), "the aspect it was at is no longer work");
        assertEquals(AssignmentReason.ASSAULT_HOLD, hold.get(5).reason());
        assertEquals(aspect[0], hold.get(5).assignment().targetCellX());
        p.execution.run(tick + 60 + FortAssault.HOLD_TICKS, M, next, p.members, p.bodies, p.topology, p.notes);
        assertEquals(FortAssault.Step.OBJECTIVE, p.execution.step(), "the hold is served and the next pick follows");
    }

    @Test
    void noFireElementMeansTheAssaultAssemblesAndGoesInWithoutWaiting() {
        Picture p = new Picture();
        p.members.removeAll(List.of(1, 2, 3, 4));
        p.bodies.keySet().removeAll(List.of(1, 2, 3, 4));
        p.execution.run(0, M, p.objective, p.members, p.bodies, p.topology, p.notes);
        assertEquals(0, p.execution.fireSquads(), "two infantry squads are not enough to split");
        p.execution.run(10, M, p.objective, p.members, p.bodies, p.topology, p.notes);
        assertEquals(FortAssault.Step.SUPPRESS, p.execution.step());
    }

    @Test
    void anAspectNobodyCanReachDissolvesTheRun() {
        Picture p = new Picture();
        p.run(0);
        p.run(FortAssault.POSITION_TIMEOUT_TICKS);
        p.run(FortAssault.POSITION_TIMEOUT_TICKS + FortAssault.SUPPRESS_MAX_TICKS);
        FortAssault.Way way = p.works.ways.get(0);
        for (int id : List.of(3, 4, 5, 6)) p.put(id, way.entryX(), way.entryY());
        Objective nothing = new Objective(1, Family.BATTERY_FORT, 0, 0, List.of(), 0, p.frame);
        assertNull(p.execution.run(FortAssault.POSITION_TIMEOUT_TICKS + FortAssault.SUPPRESS_MAX_TICKS + 30, M, nothing,
                p.members, p.bodies, p.topology, p.notes), "no aspect, nothing to take");
    }
}
