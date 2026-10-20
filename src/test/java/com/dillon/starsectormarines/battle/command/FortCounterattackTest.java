package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.command.DefenceStructureFact.Family;
import com.dillon.starsectormarines.battle.command.DefenceStructureFact.Kind;
import com.dillon.starsectormarines.battle.command.FortCounterattack.Body;
import com.dillon.starsectormarines.battle.command.FortCounterattack.Result;
import com.dillon.starsectormarines.battle.command.FortCounterattack.Stage;
import com.dillon.starsectormarines.battle.command.FortCounterattack.Status;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The counter-attack on a lost fort as a unit, on a hand-made picture: one battery fort, a keep, a pool of patrols. */
class FortCounterattackTest {

    private static final int SIZE = 200;
    private static final Faction D = Faction.DEFENDER;
    private static final Faction M = Faction.MARINE;
    private static final int[] KEEP = {100, 190};
    private static final int FORT_X = 100;
    private static final int FORT_Y = 40;
    private static final FortCounterattack.Alarms QUIET = id -> false;
    private static final FortCounterattack.Alarms ALARMED = id -> true;
    private static CommandTopology topology;

    @BeforeAll
    static void openGround() {
        NavigationGrid grid = new NavigationGrid(SIZE, SIZE);
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) grid.setWalkableFloor(x, y);
        }
        topology = CommandTopology.freeze(new BattleSimulation(grid, new CellTopology(SIZE, SIZE)));
    }

    /** A battery fort of the defender's: bunker, plant and magazine, each held by the side given. */
    private static List<DefenceStructureFact> fort(Faction bunker, Faction plant, Faction magazine) {
        return List.of(aspect(Kind.FIRE_CONTROL, FORT_X, FORT_Y, bunker),
                aspect(Kind.FORT_POWER, FORT_X + 6, FORT_Y, plant),
                aspect(Kind.FORT_MAGAZINE, FORT_X - 6, FORT_Y, magazine));
    }

    private static DefenceStructureFact aspect(Kind kind, int x, int y, Faction holder) {
        return new DefenceStructureFact(kind, x, y, holder, D, false, 4, x, y, 0, Family.BATTERY_FORT);
    }

    private static Body squad(int id, int x, int y) {
        return new Body(id, x, y, 6, 6, false, false, false, null);
    }

    /** Eight patrols spread along a row at {@code y}. */
    private static List<Body> pool(int y) {
        List<Body> out = new ArrayList<>();
        for (int i = 0; i < 8; i++) out.add(squad(i, 60 + 10 * i, y));
        return out;
    }

    private static List<int[]> hostilesAtFort(int n) {
        List<int[]> out = new ArrayList<>();
        for (int i = 0; i < n; i++) out.add(new int[]{FORT_X - 5 + i % 10, FORT_Y + 2 + i / 10});
        return out;
    }

    /** The members after a pulse, as the bodies the next pulse offers: each standing where {@code at} says. */
    private static List<Body> members(Result result, List<Body> all, int[] at) {
        List<Body> out = new ArrayList<>();
        for (Body body : all) {
            if (!result.orders().containsKey(body.squadId())) continue;
            out.add(at == null ? body : squad(body.squadId(), at[0], at[1]));
        }
        return out;
    }

    private static List<Body> without(List<Body> all, Set<Integer> ids) {
        List<Body> out = new ArrayList<>();
        for (Body body : all) if (!ids.contains(body.squadId())) out.add(body);
        return out;
    }

    // ------------------------------------------------------------------------------------------------- the facts

    @Test
    void aFortIsLosingWhenItsBunkerFallsAndLostWhenEveryAspectHas() {
        assertEquals(Status.HELD, FortCounterattack.status(D, fort(D, D, D)));
        assertEquals(Status.HELD, FortCounterattack.status(D, fort(D, M, M)), "the guns' command still ours");
        assertEquals(Status.LOSING, FortCounterattack.status(D, fort(M, D, D)));
        assertEquals(Status.LOST, FortCounterattack.status(D, fort(M, M, M)));
    }

    @Test
    void aTurnedFortsEnemyHeldAspectsAreWithheldFromSingleSquadWork() {
        List<DefenceStructureFact> facts = new ArrayList<>(fort(M, D, M));
        DefenceStructureFact generator = new DefenceStructureFact(Kind.SHIELD_GENERATOR, 20, 20, M, D, false, 4);
        facts.add(generator);
        List<DefenceStructureFact> work = FortCounterattack.withheld(D, facts);
        assertEquals(2, work.size(), "bunker and magazine withheld");
        assertTrue(work.contains(generator), "a bare structure is still single-squad work");
        assertEquals(Kind.FORT_POWER, work.get(0).kind(), "the plant we still hold can be stood to at");
        List<DefenceStructureFact> held = fort(D, D, M);
        assertEquals(held, FortCounterattack.withheld(D, held), "a fort that has not turned keeps its single retakes");
    }

    @Test
    void whatAFortTakesIsAFloorOrWhatIsBelievedInItAndGrowsWithFailure() {
        assertEquals(FortCounterattack.MIN_STRENGTH, FortCounterattack.required(0, 0));
        assertEquals(30, FortCounterattack.required(30, 0));
        assertTrue(FortCounterattack.required(30, 1) > 30);
        assertTrue(FortCounterattack.worth(0.75f * 30, 30));
        assertFalse(FortCounterattack.worth(0.7f * 30, 30));
    }

    // ------------------------------------------------------------------------------------------------- the reserve

    @Test
    void whileItsFortIsAlarmedTheReserveShareWaitsBehindItOnTheLineFromTheKeep() {
        FortCounterattack counter = new FortCounterattack();
        List<Body> free = pool(150);
        Result result = counter.plan(0, D, fort(D, D, D), ALARMED, KEEP, List.of(), List.of(), free, 2, 8, topology);
        assertEquals(Stage.RESERVE, counter.stage());
        assertEquals(2, result.recruited().size(), "the reserve law's share and no more");
        for (FortCounterattack.Order order : result.orders().values()) {
            assertEquals(AssignmentKind.DEFEND_AREA, order.assignment().kind());
            int y = order.assignment().targetCellY();
            assertTrue(y > FORT_Y + 60 && y < KEEP[1], "staged behind the fort toward the keep, at y " + y);
        }
        FortCounterattack quiet = new FortCounterattack();
        quiet.plan(0, D, fort(D, D, D), QUIET, KEEP, List.of(), List.of(), pool(150), 2, 8, topology);
        assertNull(quiet.stage(), "a quiet fort of ours holds nobody back");
    }

    // ------------------------------------------------------------------------------------------------- muster and launch

    @Test
    void aLostFortMustersInProportionStagesAndLaunchesEveryMemberOnOnePulse() {
        FortCounterattack counter = new FortCounterattack();
        List<Body> all = pool(150);
        List<int[]> seen = hostilesAtFort(15);
        Result first = counter.plan(0, D, fort(M, M, M), ALARMED, KEEP, seen, List.of(), all, 2, 8, topology);
        assertEquals(Stage.MUSTER, counter.stage(), "worth it: " + first.notes());
        int required = FortCounterattack.required(15, 0);
        assertTrue(6 * first.recruited().size() >= required, "recruited to what it believes the fort takes");
        assertTrue(first.recruited().size() < all.size(), "never the last free squad");
        int[] stage = {first.orders().values().iterator().next().assignment().targetCellX(),
                first.orders().values().iterator().next().assignment().targetCellY()};
        Set<Integer> group = new TreeSet<>(first.orders().keySet());

        // Still walking in: nobody is sent at the fort.
        Result walking = counter.plan(75, D, fort(M, M, M), ALARMED, KEEP, seen, members(first, all, null),
                without(all, group), 2, 8, topology);
        assertEquals(Stage.MUSTER, counter.stage());
        for (FortCounterattack.Order order : walking.orders().values()) assertEquals(Stage.MUSTER, order.stage());

        // Everyone at the staging cell: the whole group goes at once.
        Result launch = counter.plan(150, D, fort(M, M, M), ALARMED, KEEP, seen, members(first, all, stage),
                without(all, group), 2, 8, topology);
        assertEquals(Stage.LAUNCHED, counter.stage(), String.join("\n", launch.notes()));
        assertEquals(group, launch.orders().keySet());
        for (FortCounterattack.Order order : launch.orders().values()) {
            assertEquals(Stage.LAUNCHED, order.stage());
            assertTrue(Math.abs(order.assignment().targetCellX() - FORT_X) <= 8
                    && Math.abs(order.assignment().targetCellY() - FORT_Y) <= 8, "at the bunker");
        }
    }

    @Test
    void aFortHeldByMoreThanCanBeRaisedIsNotCounterAttacked() {
        FortCounterattack counter = new FortCounterattack();
        Result result = counter.plan(0, D, fort(M, M, M), ALARMED, KEEP, hostilesAtFort(80), List.of(), pool(150), 2, 8,
                topology);
        assertEquals(Stage.RESERVE, counter.stage(), "the reserve waits; it is not thrown at eighty: " + result.notes());
    }

    @Test
    void aBelievedHostileNearTheKeepDissolvesTheMuster() {
        FortCounterattack counter = new FortCounterattack();
        List<Body> all = pool(150);
        Result first = counter.plan(0, D, fort(M, M, M), ALARMED, KEEP, hostilesAtFort(6), List.of(), all, 2, 8, topology);
        assertEquals(Stage.MUSTER, counter.stage());
        List<int[]> atKeep = new ArrayList<>(hostilesAtFort(6));
        atKeep.add(new int[]{KEEP[0] + 5, KEEP[1]});
        Result next = counter.plan(75, D, fort(M, M, M), ALARMED, KEEP, atKeep, members(first, all, null),
                without(all, first.orders().keySet()), 2, 8, topology);
        assertNull(counter.stage());
        assertEquals(first.orders().keySet(), new TreeSet<>(next.released()));
    }

    // ------------------------------------------------------------------------------------------------- the end of it

    /** Mustered and launched against a fort held by six, standing at the staging cell; returns the launch pulse. */
    private static Result launched(FortCounterattack counter, List<Body> all, int tick) {
        Result first = counter.plan(tick, D, fort(M, M, M), ALARMED, KEEP, hostilesAtFort(6), List.of(), all, 2, 8,
                topology);
        FortCounterattack.Order any = first.orders().values().iterator().next();
        int[] stage = {any.assignment().targetCellX(), any.assignment().targetCellY()};
        Result launch = counter.plan(tick + 75, D, fort(M, M, M), ALARMED, KEEP, hostilesAtFort(6),
                members(first, all, stage), without(all, first.orders().keySet()), 2, 8, topology);
        assertEquals(Stage.LAUNCHED, counter.stage(), String.join("\n", launch.notes()));
        return launch;
    }

    private static List<Body> worn(Result result, int alive) {
        List<Body> out = new ArrayList<>();
        for (int id : result.orders().keySet()) out.add(new Body(id, FORT_X, FORT_Y + 10, alive, 6, false, false, true, null));
        return out;
    }

    @Test
    void aBrokenCounterAttackFallsBackAndAfterTheBoundTheFortIsGivenUp() {
        FortCounterattack counter = new FortCounterattack();
        List<Body> all = pool(150);
        Result launch = launched(counter, all, 0);
        Map<Integer, FortCounterattack.Order> group = launch.orders();
        Result broken = counter.plan(300, D, fort(M, M, M), ALARMED, KEEP, hostilesAtFort(6), worn(launch, 2),
                without(all, group.keySet()), 2, 8, topology);
        assertEquals(Stage.RESERVE, counter.stage(), "survivors fall back: " + broken.notes());
        assertEquals(1, counter.failures(0));
        for (FortCounterattack.Order order : broken.orders().values()) {
            assertEquals(AssignmentKind.DEFEND_AREA, order.assignment().kind(), "back to the staging cell");
        }
        // Left alone until the retry, then a second attempt that also breaks.
        Result waiting = counter.plan(400, D, fort(M, M, M), ALARMED, KEEP, hostilesAtFort(6), worn(launch, 2),
                without(all, group.keySet()), 2, 8, topology);
        assertEquals(Stage.RESERVE, counter.stage(), "no muster before the retry: " + waiting.notes());

        FortCounterattack second = new FortCounterattack();
        Result again = launched(second, all, 0);
        second.plan(300, D, fort(M, M, M), ALARMED, KEEP, hostilesAtFort(6), worn(again, 1),
                without(all, again.orders().keySet()), 2, 8, topology);
        assertEquals(1, second.failures(0));
        // A second launch from the same counter, past the retry: broken again, the fort is abandoned.
        int retry = 300 + FortCounterattack.RETRY_TICKS;
        List<Body> survivors = new ArrayList<>();
        for (int id : again.orders().keySet()) survivors.add(squad(id, 100, 150));
        Result muster = second.plan(retry, D, fort(M, M, M), ALARMED, KEEP, hostilesAtFort(2), survivors,
                without(all, again.orders().keySet()), 2, 8, topology);
        assertEquals(Stage.MUSTER, second.stage(), String.join("\n", muster.notes()));
        FortCounterattack.Order any = muster.orders().values().iterator().next();
        int[] stage = {any.assignment().targetCellX(), any.assignment().targetCellY()};
        Result relaunch = second.plan(retry + 75, D, fort(M, M, M), ALARMED, KEEP, hostilesAtFort(2),
                members(muster, concat(survivors, all), stage), without(all, muster.orders().keySet()), 2, 8, topology);
        assertEquals(Stage.LAUNCHED, second.stage(), String.join("\n", relaunch.notes()));
        Result end = second.plan(retry + 300, D, fort(M, M, M), ALARMED, KEEP, hostilesAtFort(2), worn(relaunch, 1),
                without(all, relaunch.orders().keySet()), 2, 8, topology);
        assertTrue(second.abandoned(0), String.join("\n", end.notes()));
        assertNull(second.stage());
        assertEquals(relaunch.orders().keySet(), new TreeSet<>(end.released()));
        Result after = second.plan(retry + 400, D, fort(M, M, M), ALARMED, KEEP, List.of(), List.of(), all, 2, 8, topology);
        assertNull(second.stage(), "an abandoned fort is never mustered for again: " + after.notes());
    }

    private static List<Body> concat(List<Body> a, List<Body> b) {
        List<Body> out = new ArrayList<>(a);
        Set<Integer> ids = new TreeSet<>();
        for (Body body : a) ids.add(body.squadId());
        for (Body body : b) if (!ids.contains(body.squadId())) out.add(body);
        return out;
    }

    @Test
    void aGroupTakingTheBunkerBackIsNotCalledOffInTheRoomButIsAfterTwiceTheBound() {
        FortCounterattack counter = new FortCounterattack();
        List<Body> all = pool(150);
        Result launch = launched(counter, all, 0);
        List<DefenceStructureFact> contested = List.of(
                new DefenceStructureFact(Kind.FIRE_CONTROL, FORT_X, FORT_Y, M, D, true, 4, FORT_X, FORT_Y, 0, Family.BATTERY_FORT),
                aspect(Kind.FORT_POWER, FORT_X + 6, FORT_Y, M), aspect(Kind.FORT_MAGAZINE, FORT_X - 6, FORT_Y, M));
        counter.plan(300, D, contested, ALARMED, KEEP, hostilesAtFort(6), worn(launch, 1),
                without(all, launch.orders().keySet()), 2, 8, topology);
        assertEquals(Stage.LAUNCHED, counter.stage(), "worn thin but in the room taking it");
        counter.plan(75 + 2 * FortCounterattack.ASSAULT_TICKS, D, contested, ALARMED, KEEP, hostilesAtFort(6),
                worn(launch, 1), without(all, launch.orders().keySet()), 2, 8, topology);
        assertEquals(1, counter.failures(0), "a room contested for twice the bound is not being won");
    }

    @Test
    void aTurnedFortsGroundIsWithinTheContactRadiusOfItsCentre() {
        List<DefenceStructureFact> facts = new ArrayList<>(fort(M, D, D));
        Map<Integer, int[]> turned = FortCounterattack.turned(D, facts);
        assertEquals(Set.of(0), turned.keySet());
        assertTrue(FortCounterattack.onTurnedGround(turned, FORT_X, FORT_Y + FortCounterattack.CONTACT_RADIUS_CELLS - 1));
        assertFalse(FortCounterattack.onTurnedGround(turned, FORT_X, FORT_Y + FortCounterattack.CONTACT_RADIUS_CELLS + 2));
        assertTrue(FortCounterattack.turned(D, fort(D, M, M)).isEmpty(), "the bunker still ours: not turned");
    }

    @Test
    void aCounterAttackThatRetakesTheBunkerGivesItsSquadsBack() {
        FortCounterattack counter = new FortCounterattack();
        List<Body> all = pool(150);
        Result launch = launched(counter, all, 0);
        Result won = counter.plan(600, D, fort(D, M, M), QUIET, KEEP, hostilesAtFort(2), worn(launch, 5),
                without(all, launch.orders().keySet()), 2, 8, topology);
        assertNull(counter.stage(), String.join("\n", won.notes()));
        assertEquals(launch.orders().keySet(), new TreeSet<>(won.released()));
        assertEquals(0, counter.failures(0));
        assertNotNull(won.notes().stream().filter(n -> n.contains("retook")).findFirst().orElse(null));
    }
}
