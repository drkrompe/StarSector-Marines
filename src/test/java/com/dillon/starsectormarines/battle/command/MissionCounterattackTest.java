package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.command.PlaceCounterattack.Body;
import com.dillon.starsectormarines.battle.command.PlaceCounterattack.Doctrine;
import com.dillon.starsectormarines.battle.command.PlaceCounterattack.Hostile;
import com.dillon.starsectormarines.battle.command.PlaceCounterattack.Losses;
import com.dillon.starsectormarines.battle.command.PlaceCounterattack.Order;
import com.dillon.starsectormarines.battle.command.PlaceCounterattack.Place;
import com.dillon.starsectormarines.battle.command.PlaceCounterattack.Result;
import com.dillon.starsectormarines.battle.command.PlaceCounterattack.Stage;
import com.dillon.starsectormarines.battle.command.PlaceCounterattack.Status;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A procedural mission's counter-attack doctrine on the shared planner ({@link MissionCounterattack},
 * {@link Doctrine#mission}): its group is capped at a share of the side, it has a budget of pushes a battle, a pool
 * with nobody free may still sortie its last garrison, and the mission's own proposals give way to the group's. One lost
 * place on open ground, a believed party in it watched by a squad of ours, patrols at y 150.
 */
class MissionCounterattackTest {

    private static final int SIZE = 200;
    private static final int PX = 100;
    private static final int PY = 40;
    private static final String KEY = "building 100,40";
    private static CommandTopology topology;

    @BeforeAll
    static void openGround() {
        NavigationGrid grid = new NavigationGrid(SIZE, SIZE);
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) grid.setWalkableFloor(x, y);
        }
        topology = CommandTopology.freeze(new BattleSimulation(grid, new CellTopology(SIZE, SIZE)));
    }

    private static List<Place> lost() {
        return List.of(new Place(KEY, PX, PY, 20, Status.LOST, 1f, false, false, (members, t) -> {
            Map<Integer, ObjectiveAssignment> out = new TreeMap<>();
            for (Body body : members) out.put(body.squadId(), ObjectiveAssignment.defendArea(body.squadId(), PX, PY, 4));
            return out;
        }));
    }

    private static Body squad(int id, int x, int y) {
        return new Body(id, x, y, 6, 6, false, false, false, null);
    }

    private static List<Body> patrols(int n) {
        List<Body> out = new ArrayList<>();
        for (int i = 0; i < n; i++) out.add(squad(i, 60 + 10 * i, 150));
        return out;
    }

    private static List<Hostile> holders(int n) {
        List<Hostile> out = new ArrayList<>();
        for (int i = 0; i < n; i++) out.add(new Hostile(PX - 3 + i % 6, PY + i / 6));
        return out;
    }

    private static final List<int[]> WATCHED = List.of(new int[] {PX, PY + 10});

    /** Pulses until the group launches or the bound runs out, members standing where they were sent. */
    private static Result untilLaunched(PlaceCounterattack counter, int fromTick, List<Hostile> hostiles, int pool) {
        List<Body> members = new ArrayList<>();
        Result result = null;
        for (int tick = fromTick; tick < fromTick + 75 * 80; tick += 75) {
            List<Body> free = new ArrayList<>();
            for (Body body : patrols(pool)) {
                if (members.stream().noneMatch(m -> m.squadId() == body.squadId())) free.add(body);
            }
            result = counter.plan(tick, lost(), null, hostiles, WATCHED, Losses.NONE, members, free, 0, pool, topology);
            if (counter.stage() == Stage.LAUNCHED || counter.stage() == null && tick > fromTick) return result;
            members = new ArrayList<>();
            for (Map.Entry<Integer, Order> entry : result.orders().entrySet()) {
                ObjectiveAssignment order = entry.getValue().assignment();
                members.add(squad(entry.getKey(), order.targetCellX(), order.targetCellY()));
            }
        }
        return result;
    }

    @Test
    void theGroupNeverHoldsMoreThanItsShareOfTheSideAndGoesWithWhatThatRaises() {
        Doctrine doctrine = Doctrine.mission(0.25f, 6, 2f, 0.75f, 2);
        assertEquals(2, doctrine.cap(8), "a quarter of eight squads");
        assertEquals(1, doctrine.cap(2), "never less than one squad");
        assertEquals(Integer.MAX_VALUE, Doctrine.conquest().cap(8), "Conquest's group is bounded by its pool alone");

        PlaceCounterattack counter = new PlaceCounterattack(doctrine);
        Result launch = untilLaunched(counter, 0, holders(7), 8);
        assertEquals(Stage.LAUNCHED, counter.stage(), String.join("\n", launch.notes()));
        assertEquals(2, launch.orders().size(), "seven holders want fourteen, but two squads are all its share raises");
    }

    @Test
    void theSideHasABudgetOfPushesABattle() {
        PlaceCounterattack counter = new PlaceCounterattack(Doctrine.mission(0.5f, 6, 2f, 0.75f, 1));
        untilLaunched(counter, 0, holders(3), 8);
        assertEquals(Stage.LAUNCHED, counter.stage());
        // The push is broken: nobody of it is left. With its one push spent, the side forms no group again.
        Result broken = counter.plan(6000, lost(), null, holders(3), WATCHED, Losses.NONE, List.of(), patrols(8), 0, 8,
                topology);
        assertNull(counter.stage(), String.join("\n", broken.notes()));
        for (int tick = 6075; tick < 6075 + 75 * 60; tick += 75) {
            counter.plan(tick, lost(), null, holders(3), WATCHED, Losses.NONE, List.of(), patrols(8), 0, 8, topology);
            assertNull(counter.stage(), "no group forms once the battle's pushes are spent (tick " + tick + ")");
        }
    }

    @Test
    void aSideWithNobodyFreeStillSortiesItsLastGarrisonUnderTheMissionDoctrine() {
        List<Body> garrison = List.of(squad(40, PX + 60, PY));
        PlaceCounterattack mission = new PlaceCounterattack(Doctrine.mission(1f / 3f, 6, 2f, 0.75f, 2));
        mission.plan(0, lost(), null, holders(2), WATCHED, Losses.NONE, List.of(), List.of(), garrison, 0, 3, topology);
        Result result = mission.plan(75, lost(), null, holders(2), WATCHED, Losses.NONE, List.of(), List.of(), garrison,
                0, 3, topology);
        // The garrison stands at the staging cell (staging runs from it), so it may muster and go on the same pulse.
        assertTrue(mission.stage() == Stage.MUSTER || mission.stage() == Stage.LAUNCHED,
                String.join("\n", result.notes()));
        assertTrue(result.recruited().contains(40), "the floor is kept out of the pool, and the pool is empty");

        PlaceCounterattack conquest = new PlaceCounterattack();
        conquest.plan(0, lost(), new int[] {100, 190}, holders(2), WATCHED, Losses.NONE, List.of(), List.of(), garrison,
                0, 3, topology);
        conquest.plan(75, lost(), new int[] {100, 190}, holders(2), WATCHED, Losses.NONE, List.of(), List.of(), garrison,
                0, 3, topology);
        assertFalse(conquest.claims(40), "Conquest's floor still keeps its last squad back");
    }

    @Test
    void theGroupsOrdersReplaceTheMissionsAndASquadLetGoIsTheReservesAgain() {
        ObjectiveAssignment post = ObjectiveAssignment.defendSite(1, 10, 10);
        ObjectiveAssignment retake = ObjectiveAssignment.defendArea(2, PX, PY, 4);
        List<CommandProposal> mission = List.of(
                CommandProposal.assign(post, CommandAuthority.MISSION_COMMAND, "ROUTINE",
                        CommandStabilityBreak.NONE),
                CommandProposal.claim(2, CommandAuthority.MISSION_COMMAND, "MOBILE_RESERVE_HELD",
                        CommandStabilityBreak.NONE));
        Map<Integer, MissionCounterattack.Duty> duties = Map.of(2,
                new MissionCounterattack.Duty(retake, MissionCounterattack.REASON_PREFIX + "LAUNCHED"));
        MissionCounterattack.Result result = new MissionCounterattack.Result(duties, List.of(2), List.of(3), List.of());
        List<CommandSquadState> roster = List.of(state(1), state(2), state(3));

        List<CommandProposal> out = MissionCounterattack.apply(mission, result, roster);
        assertEquals(3, out.size());
        assertEquals("ROUTINE", out.get(0).reason(), "a post the mission keeps is untouched");
        assertEquals("COUNTERATTACK_LAUNCHED", out.get(1).reason());
        assertEquals(retake, out.get(1).assignment());
        assertEquals(MissionCounterattack.RELEASED_REASON, out.get(2).reason(), "the squad let go is held in reserve");
        assertNull(out.get(2).assignment(), "and holds nothing of the counter-attack");

        Map<Integer, MissionGarrisons.Offer> offers = new TreeMap<>(Map.of(1, MissionGarrisons.Offer.duty(10, 10),
                2, MissionGarrisons.Offer.reserve()));
        Map<Integer, MissionGarrisons.Offer> left = MissionCounterattack.offers(offers, result);
        assertFalse(left.containsKey(2), "a squad in the group is never offered for manning");
        assertTrue(left.get(3).roving(), "a squad let go is offered as reserve");
    }

    @Test
    void anAuthoredCounterAttackIsALostPlaceOnlyWhileItsTriggerHasItActive() {
        PlaceTarget target = PlaceTarget.region(PX, PY, 4);
        MissionCounterattack counter = new MissionCounterattack();
        counter.directives(List.of(PlaceDirective.counterAttack("retake-hall", Faction.DEFENDER, target)
                .when(PlaceTrigger.allOf(PlaceTrigger.tickAtLeast(300), PlaceTrigger.held(PlaceHold.HOSTILE)))
                .build(), PlaceDirective.counterAttack("theirs", Faction.ALLY, target).build()), Faction.DEFENDER);
        assertEquals(1, counter.directives().directives().size(), "a party reads only its own directives");
        float[] nobody = new float[0];
        int[] inside = {PX + 1, PY};
        List<String> notes = new ArrayList<>();

        assertTrue(counter.triggered(PlaceSignals.of(225, nobody, inside, null, 1, 0, 0), notes).isEmpty(),
                "held by the marines, but not yet the hour the mission named");
        List<Place> places = counter.triggered(PlaceSignals.of(300, nobody, inside, null, 1, 0, 0), notes);
        assertEquals(1, places.size());
        Place place = places.get(0);
        assertEquals(MissionCounterattack.DIRECTIVE_KEY_PREFIX + "retake-hall", place.key());
        assertEquals(Status.LOST, place.status());
        assertTrue(place.worth() > MissionPlaces.OBJECTIVE_WORTH, "the mission's word outranks its own reading");
        assertTrue(notes.get(0).contains("retake-hall is active"), notes.toString());

        float[] ours = {PX, PY + 1};
        assertEquals(Status.LOSING, counter.triggered(PlaceSignals.of(375, ours, inside, null, 1, 0, 0), notes)
                .get(0).status(), "ours on it with the marines still there: being won back");
        assertTrue(counter.triggered(PlaceSignals.of(450, ours, new int[0], null, 1, 0, 0), notes).isEmpty(),
                "held again: released, and no longer offered");
        assertEquals(PlaceDirectives.State.RELEASED, counter.directives().state("retake-hall"));
    }

    private static CommandSquadState state(int id) {
        return new CommandSquadState(id, Faction.DEFENDER, 6, 50, 50, 50, 50, 0, UnitRole.PATROL, false, null, null,
                null, 0, new int[0]);
    }
}
