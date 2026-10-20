package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.command.DefenceStructureFact.Family;
import com.dillon.starsectormarines.battle.command.DefenceStructureFact.Kind;
import com.dillon.starsectormarines.battle.command.PlaceCounterattack.Body;
import com.dillon.starsectormarines.battle.command.PlaceCounterattack.Hostile;
import com.dillon.starsectormarines.battle.command.PlaceCounterattack.Losses;
import com.dillon.starsectormarines.battle.command.PlaceCounterattack.Order;
import com.dillon.starsectormarines.battle.command.PlaceCounterattack.Place;
import com.dillon.starsectormarines.battle.command.PlaceCounterattack.Result;
import com.dillon.starsectormarines.battle.command.PlaceCounterattack.Stage;
import com.dillon.starsectormarines.battle.command.PlaceCounterattack.Status;
import com.dillon.starsectormarines.battle.command.compound.CompoundService.CompoundState;
import com.dillon.starsectormarines.battle.decision.TacticalNode;
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
import java.util.TreeMap;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The counter-attack on a lost place as a unit, on a hand-made picture: one place (a compound, unless a test says a
 * fort), a keep, a pool of patrols. The place is not a fort on purpose: a fort is one case among many.
 */
class PlaceCounterattackTest {

    private static final int SIZE = 200;
    private static final Faction D = Faction.DEFENDER;
    private static final Faction M = Faction.MARINE;
    private static final int[] KEEP = {100, 190};
    private static final int PX = 100;
    private static final int PY = 40;
    private static final String KEY = "compound 1";
    private static CommandTopology topology;

    @BeforeAll
    static void openGround() {
        NavigationGrid grid = new NavigationGrid(SIZE, SIZE);
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) grid.setWalkableFloor(x, y);
        }
        topology = CommandTopology.freeze(new BattleSimulation(grid, new CellTopology(SIZE, SIZE)));
    }

    /** The place, standing as given; its retake holds its centre. */
    private static Place place(Status status, boolean alarmed) {
        return new Place(KEY, PX, PY, 20, status, 1f, alarmed, false, (members, t) -> {
            Map<Integer, ObjectiveAssignment> out = new TreeMap<>();
            for (Body body : members) out.put(body.squadId(), ObjectiveAssignment.defendArea(body.squadId(), PX, PY, 4));
            return out;
        });
    }

    private static List<Place> lost() {
        return List.of(place(Status.LOST, false));
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

    private static List<Hostile> holders(int n) {
        List<Hostile> out = new ArrayList<>();
        for (int i = 0; i < n; i++) out.add(new Hostile(PX - 5 + i % 10, PY + 2 + i / 10));
        return out;
    }

    private static List<int[]> cells(List<Body> bodies) {
        List<int[]> out = new ArrayList<>();
        for (Body body : bodies) out.add(new int[] {body.x(), body.y()});
        return out;
    }

    /** The members after a pulse, standing where their orders send them (a probe at its post, the rest at staging). */
    private static List<Body> arrived(Result result) {
        List<Body> out = new ArrayList<>();
        for (Map.Entry<Integer, Order> entry : result.orders().entrySet()) {
            ObjectiveAssignment order = entry.getValue().assignment();
            out.add(squad(entry.getKey(), order.targetCellX(), order.targetCellY()));
        }
        return out;
    }

    private static List<Body> without(List<Body> all, Set<Integer> ids) {
        List<Body> out = new ArrayList<>();
        for (Body body : all) if (!ids.contains(body.squadId())) out.add(body);
        return out;
    }

    private record Pulse(Result result, List<Body> members) {}

    /** One pulse with the members standing where the last pulse sent them; everyone else free at y 150. */
    private static Pulse pulse(PlaceCounterattack counter, int tick, List<Place> places, List<Hostile> hostiles,
                               List<Body> members, Losses losses) {
        List<Body> free = without(pool(150), ids(members));
        Result result = counter.plan(tick, places, KEEP, hostiles, cells(members), losses, members, free, 2, 8, topology);
        return new Pulse(result, arrived(result));
    }

    private static Set<Integer> ids(List<Body> bodies) {
        Set<Integer> out = new TreeSet<>();
        for (Body body : bodies) out.add(body.squadId());
        return out;
    }

    // ------------------------------------------------------------------------------------------------- the facts

    @Test
    void whatAPlaceTakesIsTheObservedHoldersWithAMarginPlusRememberedLossesAndGrowsWithFailure() {
        assertEquals(12, PlaceCounterattack.required(0f, 0f, 0, 12), "the floor when nothing is seen");
        assertEquals(Math.round(PlaceCounterattack.MARGIN * 20), PlaceCounterattack.required(20f, 0f, 0, 12));
        assertEquals(Math.round(PlaceCounterattack.MARGIN * 20 + PlaceCounterattack.LOSS_WEIGHT * 10),
                PlaceCounterattack.required(20f, 10f, 0, 12), "losses there stand for holders nobody saw");
        assertTrue(PlaceCounterattack.required(20f, 0f, 1, 12) > PlaceCounterattack.required(20f, 0f, 0, 12));
        assertTrue(PlaceCounterattack.worth(0.75f * 40, 40));
        assertFalse(PlaceCounterattack.worth(0.7f * 40, 40));
    }

    @Test
    void aPlaceIsValuedByWhatItIsWorthAndHowNearHomeItStands() {
        assertEquals(2f, PlaceCounterattack.value(1f, 0, 220), 1e-6f, "an objective at home");
        assertEquals(1f, PlaceCounterattack.value(1f, 220, 220), 1e-6f, "an objective at the bound");
        assertTrue(PlaceCounterattack.value(1.5f, 100, 220) > PlaceCounterattack.value(1f, 100, 220), "must-hold");
        assertEquals(0f, PlaceCounterattack.value(0f, 0, 220), "worth nothing is never retaken, however near");
    }

    @Test
    void aTurnedPlacesGroundStopsWhereAHeldPlaceIsNearer() {
        Place lost = place(Status.LOST, false);
        Place held = new Place("compound 2", PX, PY + 30, 20, Status.HELD, 1f, false, false, lost.assault());
        List<Place> places = List.of(lost, held);
        List<Place> turned = PlaceCounterattack.turnedGround(places, KEEP, 220);
        assertEquals(List.of(lost), turned);
        assertTrue(PlaceCounterattack.onTurnedGround(turned, places, PX, PY + 10));
        assertFalse(PlaceCounterattack.onTurnedGround(turned, places, PX, PY + 18),
                "within the lost place's radius but nearer the held one: the held place's to answer");
        assertFalse(PlaceCounterattack.onTurnedGround(turned, places, PX, PY - 25), "beyond its radius");
        assertTrue(PlaceCounterattack.turnedGround(places, new int[] {PX, PY + 400}, 220).isEmpty(),
                "a place out of home's reach is not the counter-attack's question");
    }

    @Test
    void aFortIsOneCaseLosingWhenItsBunkerFallsAndLostWhenEveryAspectHas() {
        assertEquals(Status.HELD, FortPlaces.status(D, fort(D, D, D)));
        assertEquals(Status.HELD, FortPlaces.status(D, fort(D, M, M)), "the guns' command still ours");
        assertEquals(Status.LOSING, FortPlaces.status(D, fort(M, D, D)));
        assertEquals(Status.LOST, FortPlaces.status(D, fort(M, M, M)));
        List<DefenceStructureFact> facts = new ArrayList<>(fort(M, D, M));
        DefenceStructureFact generator = new DefenceStructureFact(Kind.SHIELD_GENERATOR, 20, 20, M, D, false, 4);
        facts.add(generator);
        List<DefenceStructureFact> work = FortPlaces.withheld(D, facts);
        assertEquals(2, work.size(), "bunker and magazine withheld from single-squad work");
        assertTrue(work.contains(generator));
        Place place = FortPlaces.places(D, fort(M, M, M), id -> true).get(0);
        assertEquals("fort 0", place.key());
        assertEquals(Status.LOST, place.status());
    }

    @Test
    void aCompoundIsLostWhenTakenAndBeingWonBackWhileContestedAfterThatButNotBefore() {
        CompoundPlaces places = new CompoundPlaces();
        TacticalNode node = new TacticalNode(TacticalNode.Kind.MG_NEST, PX, PY, PX - 6, PY - 6, PX + 6, PY + 6, D, 1, 4);
        assertEquals(Status.HELD, places.places(List.of(compound(CompoundState.CONTESTED, node)), i -> true).get(0)
                .status(), "contested before it is lost: still the ordinary defence's");
        assertEquals(Status.LOST, places.places(List.of(compound(CompoundState.MARINE_HELD, node)), i -> true).get(0)
                .status());
        Place back = places.places(List.of(compound(CompoundState.CONTESTED, node)), i -> true).get(0);
        assertEquals(Status.LOSING, back.status(), "contested after it was lost: being won back");
        assertTrue(back.capturing());
        assertEquals(Status.HELD, places.places(List.of(compound(CompoundState.DEFENDER_HELD, node)), i -> true).get(0)
                .status());
        assertTrue(places.places(List.of(compound(CompoundState.MARINE_HELD, node)), i -> false).isEmpty());
        assertEquals(0, CompoundPlaces.index(CompoundPlaces.key(0)));
        assertEquals(-1, CompoundPlaces.index("fort 0"));
    }

    // ------------------------------------------------------------------------------------------------- informed

    @Test
    void aLostPlaceNobodyHasWatchedIsProbedBeforeAnythingMusters() {
        PlaceCounterattack counter = new PlaceCounterattack();
        Pulse first = pulse(counter, 0, lost(), holders(6), List.of(), Losses.NONE);
        assertEquals(Stage.PROBE, counter.stage(), String.join("\n", first.result().notes()));
        int probe = counter.probeId();
        assertTrue(probe >= 0);
        Order post = first.result().orders().get(probe);
        assertEquals(Stage.PROBE, post.stage());
        double gap = Math.hypot(post.assignment().targetCellX() - PX, post.assignment().targetCellY() - PY);
        assertTrue(gap <= PlaceCounterattack.OBSERVE_CELLS && gap >= PlaceCounterattack.OBSERVE_CELLS - 6,
                "the post is within observation range, short of the place: " + gap);
        for (Map.Entry<Integer, Order> entry : first.result().orders().entrySet()) {
            if (entry.getKey() == probe) continue;
            assertEquals(Stage.RESERVE, entry.getValue().stage(), "the rest wait at the staging cell");
            assertTrue(entry.getValue().assignment().targetCellY() > PY + 40);
        }
        assertEquals(Math.round(8 * PlaceCounterattack.RESERVE_SHARE), first.result().recruited().size(),
                "nobody beyond the reserve is raised while the place is unobserved");
    }

    @Test
    void theGroupIsSizedFromWhatTheProbeObservedAndMustersOnlyThen() {
        PlaceCounterattack counter = new PlaceCounterattack();
        Pulse p = pulse(counter, 0, lost(), holders(14), List.of(), Losses.NONE);
        p = pulse(counter, 75, lost(), holders(14), p.members(), Losses.NONE);
        assertEquals(Stage.PROBE, counter.stage(), "one pulse of watching is not an observation");
        p = pulse(counter, 150, lost(), holders(14), p.members(), Losses.NONE);
        assertEquals(Stage.MUSTER, counter.stage(), String.join("\n", p.result().notes()));
        assertEquals(14f, counter.believedHolders(KEY), 1e-6f);
        int required = PlaceCounterattack.required(14f, 0f, 0, PlaceCounterattack.MIN_STRENGTH);
        assertTrue(p.result().notes().stream().anyMatch(n -> n.contains("believed to take " + required)),
                String.join("\n", p.result().notes()));
        assertTrue(6 * p.members().size() >= required, "recruited to what the place takes");
    }

    @Test
    void anOwnSquadAlreadyWatchingThePlaceMakesAProbeNeedless() {
        PlaceCounterattack counter = new PlaceCounterattack();
        List<Body> free = pool(150);
        List<int[]> watchers = new ArrayList<>(cells(free));
        watchers.add(new int[] {PX, PY + 12});
        counter.plan(0, lost(), KEEP, holders(6), watchers, Losses.NONE, List.of(), free, 2, 8, topology);
        Result second = counter.plan(75, lost(), KEEP, holders(6), watchers, Losses.NONE, List.of(), free, 2, 8, topology);
        assertNotNull(counter.stage());
        assertTrue(counter.stage() == Stage.MUSTER, "a picket on the place observed it: " + second.notes());
        assertEquals(-1, counter.probeId());
    }

    @Test
    void aPlaceHeldByMoreThanCanBeRaisedIsNotAttemptedAndIsGivenUpAfterTheBound() {
        PlaceCounterattack counter = new PlaceCounterattack();
        Pulse p = pulse(counter, 0, lost(), holders(40), List.of(), Losses.NONE);
        p = pulse(counter, 75, lost(), holders(40), p.members(), Losses.NONE);
        p = pulse(counter, 150, lost(), holders(40), p.members(), Losses.NONE);
        assertEquals(Stage.RESERVE, counter.stage(), "outmatched: it waits rather than going in: " + p.result().notes());
        assertEquals(1, counter.failures(KEY));
        assertTrue(p.result().notes().stream().anyMatch(n -> n.contains("outmatched")));
        Pulse waiting = pulse(counter, 300, lost(), holders(40), p.members(), Losses.NONE);
        assertEquals(Stage.RESERVE, counter.stage(), "left alone until the retry: " + waiting.result().notes());
        int retry = 150 + PlaceCounterattack.RETRY_TICKS;
        // Observed afresh after the retry, still too many: the place is given up.
        p = pulse(counter, retry, lost(), holders(40), waiting.members(), Losses.NONE);
        p = pulse(counter, retry + 75, lost(), holders(40), p.members(), Losses.NONE);
        p = pulse(counter, retry + 150, lost(), holders(40), p.members(), Losses.NONE);
        assertTrue(counter.abandoned(KEY), String.join("\n", p.result().notes()));
        assertNull(counter.stage());
    }

    @Test
    void rememberedLossesOnItsGroundRaiseWhatThePlaceTakes() {
        PlaceCounterattack quiet = new PlaceCounterattack();
        PlaceCounterattack costly = new PlaceCounterattack();
        Losses heavy = (x, y, r) -> 100f;
        for (PlaceCounterattack counter : List.of(quiet, costly)) {
            Losses losses = counter == costly ? heavy : Losses.NONE;
            Pulse p = pulse(counter, 0, lost(), holders(6), List.of(), losses);
            p = pulse(counter, 75, lost(), holders(6), p.members(), losses);
            pulse(counter, 150, lost(), holders(6), p.members(), losses);
        }
        assertTrue(quiet.stage() == Stage.MUSTER || quiet.stage() == Stage.LAUNCHED, "six seen and nothing lost: it goes");
        assertEquals(Stage.RESERVE, costly.stage(), "six seen where a hundred of ours fell: more than six hold it");
        assertEquals(1, costly.failures(KEY));
    }

    // ------------------------------------------------------------------------------------------------- launch

    /** Probed, mustered and launched against a place held by fourteen; returns the launch pulse. */
    private static Pulse launched(PlaceCounterattack counter) {
        Pulse p = pulse(counter, 0, lost(), holders(14), List.of(), Losses.NONE);
        p = pulse(counter, 75, lost(), holders(14), p.members(), Losses.NONE);
        p = pulse(counter, 150, lost(), holders(14), p.members(), Losses.NONE);
        assertEquals(Stage.MUSTER, counter.stage(), String.join("\n", p.result().notes()));
        p = pulse(counter, 225, lost(), holders(14), p.members(), Losses.NONE);
        assertEquals(Stage.LAUNCHED, counter.stage(), String.join("\n", p.result().notes()));
        return p;
    }

    @Test
    void aMusterLaunchesEveryMemberOnOnePulseOnlyWithTheStrengthItBelievesItNeeds() {
        PlaceCounterattack counter = new PlaceCounterattack();
        Pulse p = pulse(counter, 0, lost(), holders(14), List.of(), Losses.NONE);
        p = pulse(counter, 75, lost(), holders(14), p.members(), Losses.NONE);
        Pulse muster = pulse(counter, 150, lost(), holders(14), p.members(), Losses.NONE);
        // Still walking in: the recruits stand where they were. Nobody is sent at the place.
        List<Body> walking = new ArrayList<>();
        for (Body body : muster.members()) {
            walking.add(muster.result().recruited().contains(body.squadId()) ? squad(body.squadId(), body.x(), 150) : body);
        }
        Pulse still = pulse(counter, 225, lost(), holders(14), walking, Losses.NONE);
        assertEquals(Stage.MUSTER, counter.stage());
        for (Order order : still.result().orders().values()) assertTrue(order.stage() != Stage.LAUNCHED);
        Pulse launch = pulse(counter, 300, lost(), holders(14), still.members(), Losses.NONE);
        assertEquals(Stage.LAUNCHED, counter.stage(), String.join("\n", launch.result().notes()));
        assertEquals(ids(still.members()), launch.result().orders().keySet(), "the whole group, probe included");
        for (Order order : launch.result().orders().values()) {
            assertEquals(Stage.LAUNCHED, order.stage());
            assertEquals(PX, order.assignment().targetCellX());
        }
        int required = PlaceCounterattack.required(14f, 0f, 0, PlaceCounterattack.MIN_STRENGTH);
        assertTrue(6 * launch.members().size() >= PlaceCounterattack.COMMIT_SHARE * required);
    }

    @Test
    void aRepulseRaisesTheEstimateToWhatThePushMet() {
        PlaceCounterattack counter = new PlaceCounterattack();
        Pulse launch = launched(counter);
        // In the place the push meets eighteen, not six, and is worn down.
        List<Body> worn = new ArrayList<>();
        for (int id : launch.result().orders().keySet()) worn.add(new Body(id, PX, PY + 6, 6, 6, false, false, true, null));
        counter.plan(300, lost(), KEEP, holders(18), cells(worn), Losses.NONE, worn, without(pool(150), ids(worn)), 2, 8,
                topology);
        List<Body> broken = new ArrayList<>();
        for (Body body : worn) broken.add(new Body(body.squadId(), PX, PY + 6, 1, 6, false, false, true, null));
        Result fell = counter.plan(375, lost(), KEEP, holders(18), cells(broken), Losses.NONE, broken,
                without(pool(150), ids(broken)), 2, 8, topology);
        assertEquals(Stage.RESERVE, counter.stage(), String.join("\n", fell.notes()));
        assertEquals(1, counter.failures(KEY));
        assertEquals(18f, counter.believedHolders(KEY), 1e-6f, String.join("\n", fell.notes()));
        assertTrue(fell.notes().stream().anyMatch(n -> n.contains("estimate after the repulse")));
    }

    @Test
    void aBelievedHostileNearHomeDissolvesAnythingNotLaunched() {
        PlaceCounterattack counter = new PlaceCounterattack();
        Pulse p = pulse(counter, 0, lost(), holders(6), List.of(), Losses.NONE);
        List<Hostile> atKeep = new ArrayList<>(holders(6));
        atKeep.add(new Hostile(KEEP[0] + 5, KEEP[1]));
        Pulse next = pulse(counter, 75, lost(), atKeep, p.members(), Losses.NONE);
        assertNull(counter.stage());
        assertEquals(ids(p.members()), new TreeSet<>(next.result().released()));
    }

    @Test
    void aCounterAttackThatRetakesThePlaceGivesItsSquadsBack() {
        PlaceCounterattack counter = new PlaceCounterattack();
        Pulse launch = launched(counter);
        Result won = counter.plan(600, List.of(place(Status.HELD, false)), KEEP, holders(2), cells(launch.members()),
                Losses.NONE, launch.members(), without(pool(150), ids(launch.members())), 2, 8, topology);
        assertTrue(counter.stage() == null || counter.stage() == Stage.RESERVE, String.join("\n", won.notes()));
        assertEquals(launch.result().orders().keySet(), new TreeSet<>(won.released()));
        assertTrue(won.notes().stream().anyMatch(n -> n.contains("retook")));
    }

    @Test
    void aQuietPlaceHoldsNobodyBackUntilTheBattleReachesTheSideAndAnAlarmedOneDoes() {
        PlaceCounterattack quiet = new PlaceCounterattack();
        quiet.plan(0, List.of(place(Status.HELD, false)), KEEP, List.of(), List.of(), Losses.NONE, List.of(), pool(150),
                2, 8, topology);
        assertNull(quiet.stage());
        PlaceCounterattack alarmed = new PlaceCounterattack();
        Result result = alarmed.plan(0, List.of(place(Status.HELD, true)), KEEP, List.of(), List.of(), Losses.NONE,
                List.of(), pool(150), 2, 8, topology);
        assertEquals(Stage.RESERVE, alarmed.stage());
        assertEquals(2, result.recruited().size(), "before anything is lost the reserve is the law's share");
        assertTrue(alarmed.lendable().isEmpty(), "nothing above the law's share to lend");
        PlaceCounterattack finite = new PlaceCounterattack(PlaceCounterattack.Doctrine.finitePool(12));
        Result all = finite.plan(0, List.of(place(Status.HELD, false)), KEEP, List.of(), List.of(), Losses.NONE,
                List.of(), pool(150), 0, 8, topology);
        assertEquals(8, all.recruited().size(), "a finite counter-attack pool is all reserve from the start");
    }

    @Test
    void aReserveBehindAHeldPlaceMovesToOneThatTurns() {
        PlaceCounterattack counter = new PlaceCounterattack();
        Place held = new Place("compound 2", PX, 120, 20, Status.HELD, 1f, true, false, place(Status.HELD, false).assault());
        Pulse p = pulse(counter, 0, List.of(held, place(Status.HELD, false)), List.of(), List.of(), Losses.NONE);
        assertEquals("compound 2", counter.placeKey(), "the nearer home, alarmed");
        pulse(counter, 75, List.of(held, place(Status.LOST, false)), holders(4), p.members(), Losses.NONE);
        assertEquals(KEY, counter.placeKey());
    }

    private static ConquestCommandFacts.Compound compound(CompoundState state, TacticalNode node) {
        return new ConquestCommandFacts.Compound(state, node, PX, PY, 1, new int[0]);
    }

    private static List<DefenceStructureFact> fort(Faction bunker, Faction plant, Faction magazine) {
        return List.of(aspect(Kind.FIRE_CONTROL, PX, PY, bunker), aspect(Kind.FORT_POWER, PX + 6, PY, plant),
                aspect(Kind.FORT_MAGAZINE, PX - 6, PY, magazine));
    }

    private static DefenceStructureFact aspect(Kind kind, int x, int y, Faction holder) {
        return new DefenceStructureFact(kind, x, y, holder, D, false, 4, x, y, 0, Family.BATTERY_FORT);
    }
}
