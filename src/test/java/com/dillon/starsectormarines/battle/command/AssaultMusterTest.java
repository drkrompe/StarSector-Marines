package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.command.AssaultMuster.Body;
import com.dillon.starsectormarines.battle.command.AssaultMuster.Objective;
import com.dillon.starsectormarines.battle.command.AssaultMuster.Readiness;
import com.dillon.starsectormarines.battle.command.AssaultMuster.Result;
import com.dillon.starsectormarines.battle.command.ConquestFrontSnapshot.AssignmentReason;
import com.dillon.starsectormarines.battle.command.ConquestFrontSnapshot.CompanyState;
import com.dillon.starsectormarines.battle.command.DefenceStructureFact.Family;
import com.dillon.starsectormarines.battle.command.DefenceStructureFact.Kind;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The muster and assault phases as a unit: the readiness judgement, the rally choice and the company's phase
 * transitions, each on a small hand-made picture.
 */
class AssaultMusterTest {

    private static final int SIZE = 300;
    private static final int[] DROP = {20, 150};
    private static final Faction M = Faction.MARINE;
    private static final Faction D = Faction.DEFENDER;

    private static CommandTopology openTopology() {
        NavigationGrid grid = new NavigationGrid(SIZE, SIZE);
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) grid.setWalkableFloor(x, y);
        }
        return CommandTopology.freeze(new BattleSimulation(grid, new CellTopology(SIZE, SIZE)));
    }

    private static Objective fort(int id, int x, int y, int believedContacts) {
        DefenceStructureFact bunker = new DefenceStructureFact(Kind.FIRE_CONTROL, x, y, D, D, false, 5, x, y, id,
                Family.BATTERY_FORT);
        return new Objective(id, Family.BATTERY_FORT, x, y, List.of(bunker), believedContacts);
    }

    private static Body squad(int id, int x, int y, int alive) {
        return new Body(id, x, y, alive, false, false, false, null);
    }

    private static Body mech(int id, int x, int y, int alive) {
        return new Body(id, x, y, alive, true, false, false, null);
    }

    // ---------------------------------------------------------------------------------------- readiness judgement

    @Test
    void whatAFortIsBelievedToTakeGrowsWithWhatIsBelievedNearItAndWithBrokenAttempts() {
        int base = AssaultMuster.required(Family.BATTERY_FORT, 0, 0);
        assertEquals(AssaultMuster.FORT_BASE_STRENGTH, base);
        assertEquals(base + 3 * AssaultMuster.PER_CONTACT_STRENGTH, AssaultMuster.required(Family.BATTERY_FORT, 3, 0));
        assertTrue(AssaultMuster.required(Family.BATTERY_FORT, 0, 1) > base, "a broken attempt raises the bar");
        assertTrue(AssaultMuster.required(Family.BATTERY_FORT, 0, 2) > AssaultMuster.required(Family.BATTERY_FORT, 0, 1));
        assertTrue(AssaultMuster.required(Family.BARE, 0, 0) < base, "a bare structure is not a fort");
    }

    @Test
    void aMachineMemberCountsForMoreThanAMarine() {
        assertEquals(4f, AssaultMuster.weight(4, false));
        assertEquals(4f * AssaultMuster.MACHINE_MEMBER_WEIGHT, AssaultMuster.weight(4, true));
        assertEquals(0f, AssaultMuster.weight(-2, false));
    }

    @Test
    void aCompanyIsReadyWhenWhatHasGatheredReachesWhatIsBelievedNeededAndNotBefore() {
        assertEquals(Readiness.GATHERING, AssaultMuster.judge(23f, 24, 100, 4600));
        assertEquals(Readiness.READY, AssaultMuster.judge(24f, 24, 100, 4600));
        assertEquals(Readiness.READY, AssaultMuster.judge(40f, 24, 100, 4600));
    }

    @Test
    void aMusterCommitsWithWhatItHasWhenTheTimeoutPassesSoItNeverWaitsForever() {
        assertEquals(Readiness.GATHERING, AssaultMuster.judge(8f, 24, 4599, 4600));
        assertEquals(Readiness.TIMED_OUT, AssaultMuster.judge(8f, 24, 4600, 4600));
        assertEquals(Readiness.TIMED_OUT, AssaultMuster.judge(0f, 24, 9000, 4600));
    }

    // -------------------------------------------------------------------------------------------- rally choice

    @Test
    void aNearbyObjectiveIsMusteredAtTheDropZone() {
        int[] rally = AssaultMuster.rallyCell(20, 150, 70, 150, (x, y) -> true, List.of());
        assertEquals(20, rally[0]);
        assertEquals(150, rally[1]);
    }

    @Test
    void aFarObjectiveIsMusteredOnTheApproachShortOfItNotAtTheDoor() {
        int[] rally = AssaultMuster.rallyCell(20, 150, 220, 150, (x, y) -> true, List.of());
        assertTrue(rally[0] > 20, "staged forward of the drop zone");
        double gap = Math.hypot(220 - rally[0], 150 - rally[1]);
        assertTrue(gap >= AssaultMuster.STAGING_STANDOFF_CELLS - 2, "and outside the standoff of the objective, was " + gap);
    }

    @Test
    void aBelievedHostileNearTheStagingCellPullsItBackTowardTheDropZone() {
        int[] open = AssaultMuster.rallyCell(20, 150, 220, 150, (x, y) -> true, List.of());
        int[] pulled = AssaultMuster.rallyCell(20, 150, 220, 150, (x, y) -> true, List.of(new int[] {open[0] + 5, open[1]}));
        assertTrue(pulled[0] < open[0] - 8, "pulled back from " + open[0] + " to " + pulled[0]);
        int[] everywhere = AssaultMuster.rallyCell(20, 150, 220, 150, (x, y) -> true,
                List.of(new int[] {20, 150}, new int[] {56, 150}, new int[] {92, 150}, new int[] {128, 150},
                        new int[] {164, 150}));
        assertEquals(20, everywhere[0], "unsafe all the way back is the drop zone itself");
    }

    @Test
    void aStagingCellSlidesOntoGroundThatCanBeReached() {
        int[] rally = AssaultMuster.rallyCell(20, 150, 220, 150, (x, y) -> !(x > 150 && x < 175), List.of());
        assertTrue(rally[0] <= 150 || rally[0] >= 175, "never on the blocked band, was " + rally[0]);
    }

    // ---------------------------------------------------------------------------------------- phase transitions

    private static Result pulse(AssaultMuster muster, int tick, List<Objective> objectives, List<Body> bodies,
                                CommandTopology topology) {
        return muster.plan(tick, M, objectives, bodies, DROP, List.of(), topology);
    }

    private static CompanyState company(Result result) {
        return result.companies().stream().filter(c -> c.objectiveId() == 1).findFirst().orElseThrow();
    }

    private static CompanyState foothold(Result result) {
        return result.companies().stream().filter(c -> c.stage().equals("FOOTHOLD")).findFirst().orElseThrow();
    }

    @Test
    void squadsFarFromTheRallyAreOrderedToItAndNoneIsSentAtTheFortWhileTheCompanyIsShort() {
        CommandTopology topology = openTopology();
        AssaultMuster muster = new AssaultMuster();
        List<Body> bodies = List.of(squad(1, 20, 150, 12), squad(2, 50, 150, 12), squad(3, 55, 150, 12));
        Result result = pulse(muster, 0, List.of(fort(1, 70, 150, 0)), bodies, topology);
        CompanyState gathering = company(result);
        assertEquals("MUSTER", gathering.stage());
        assertEquals(List.of(2, 3), gathering.squadIds());
        assertEquals(0, gathering.arrivedSquads(), "they are 30 cells from a rally at the drop zone");
        assertEquals(DROP[0], gathering.rallyCellX());
        for (int id : gathering.squadIds()) {
            assertEquals(AssignmentReason.MUSTER_GATHER, result.orders().get(id).reason());
            assertEquals(AssignmentKind.ADVANCE_TRACK, result.orders().get(id).assignment().kind());
            assertEquals(DROP[0], result.orders().get(id).assignment().targetCellX());
        }
        // The moment they have come up, the same company commits.
        List<Body> arrived = List.of(squad(1, 20, 150, 12), squad(2, 24, 150, 12), squad(3, 26, 150, 12));
        assertEquals("ASSAULT", company(pulse(muster, 400, List.of(fort(1, 70, 150, 0)), arrived, topology)).stage());
    }

    @Test
    void oneSquadHoldsTheDropZoneWhileACompanyMustersAndIsNotInIt() {
        CommandTopology topology = openTopology();
        AssaultMuster muster = new AssaultMuster();
        List<Body> bodies = List.of(squad(1, 20, 150, 12), squad(2, 52, 152, 12), squad(3, 54, 150, 12));
        Result result = pulse(muster, 0, List.of(fort(1, 70, 150, 0)), bodies, topology);
        assertEquals(List.of(1), foothold(result).squadIds());
        assertFalse(company(result).squadIds().contains(1), "the foothold element is not also in the company");
        AssaultMuster.Order order = result.orders().get(1);
        assertEquals(AssignmentReason.MUSTER_FOOTHOLD, order.reason());
        assertEquals(DROP[0], order.assignment().targetCellX());
        assertEquals(DROP[1], order.assignment().targetCellY());
        // It is sticky, and it is released with the last company.
        Result later = pulse(muster, 30, List.of(fort(1, 70, 150, 0)), bodies, topology);
        assertEquals(List.of(1), foothold(later).squadIds());
        Result over = pulse(muster, 60, List.of(), bodies, topology);
        assertTrue(over.companies().isEmpty());
        assertTrue(over.orders().isEmpty());
    }

    @Test
    void theCompanyCommitsAsOneBodyOnceEnoughHasGatheredAtTheRally() {
        CommandTopology topology = openTopology();
        AssaultMuster muster = new AssaultMuster();
        List<Body> bodies = new ArrayList<>();
        for (int i = 1; i <= 8; i++) bodies.add(squad(i, 20 + i, 150, 12));
        Result result = pulse(muster, 0, List.of(fort(1, 70, 150, 0)), bodies, topology);
        CompanyState committed = company(result);
        assertEquals("ASSAULT", committed.stage());
        assertEquals(2, committed.squadIds().size(), "two squads of twelve are the 24 a fort is believed to take");
        assertEquals(24, committed.strength());
        for (int id : committed.squadIds()) {
            assertEquals(AssignmentReason.ASSAULT_COMMIT, result.orders().get(id).reason());
            assertEquals(AssignmentKind.ATTACK_MOVE, result.orders().get(id).assignment().kind());
            // Every one of them at the same aspect: its doorstep is within reach of the bunker.
            assertTrue(Math.hypot(result.orders().get(id).assignment().targetCellX() - 70,
                    result.orders().get(id).assignment().targetCellY() - 150) <= 5);
        }
        assertTrue(muster.active());
    }

    @Test
    void whatIsBelievedNearTheFortRaisesTheBarSoMoreSquadsAreRecruited() {
        CommandTopology topology = openTopology();
        List<Body> bodies = new ArrayList<>();
        for (int i = 1; i <= 10; i++) bodies.add(squad(i, 20 + i, 150, 12));
        CompanyState quiet = company(pulse(new AssaultMuster(), 0, List.of(fort(1, 70, 150, 0)), bodies, topology));
        CompanyState watched = company(pulse(new AssaultMuster(), 0, List.of(fort(1, 70, 150, 6)), bodies, topology));
        assertTrue(watched.required() > quiet.required());
        assertTrue(watched.squadIds().size() > quiet.squadIds().size());
    }

    @Test
    void aCompanyBelowStrengthCommitsOnlyAtTheTimeout() {
        CommandTopology topology = openTopology();
        AssaultMuster muster = new AssaultMuster();
        List<Objective> objectives = List.of(fort(1, 70, 150, 0));
        // Squad 2 is still on its way (30 cells from the rally) so the company is not yet everything it can get.
        List<Body> bodies = List.of(squad(1, 20, 150, 4), squad(2, 50, 150, 4));
        assertEquals("MUSTER", company(pulse(muster, 0, objectives, bodies, topology)).stage());
        assertEquals("MUSTER", company(pulse(muster, AssaultMuster.MUSTER_TIMEOUT_TICKS - 30, objectives, bodies, topology)).stage());
        CompanyState committed = company(pulse(muster, AssaultMuster.MUSTER_TIMEOUT_TICKS + 700, objectives, bodies, topology));
        assertEquals("ASSAULT", committed.stage(), "a muster never waits forever");
        assertTrue(committed.strength() < committed.required(), "and the commit says it went in short");
    }

    @Test
    void aCompanyThatHasEverythingItCanGetAndIsStillShortWaitsOnlyAGraceNotTheWholeTimeout() {
        CommandTopology topology = openTopology();
        AssaultMuster muster = new AssaultMuster();
        List<Objective> objectives = List.of(fort(1, 70, 150, 0));
        List<Body> bodies = List.of(squad(1, 20, 150, 4), squad(2, 22, 150, 4));
        assertEquals("MUSTER", company(pulse(muster, 0, objectives, bodies, topology)).stage());
        assertEquals("MUSTER", company(pulse(muster, AssaultMuster.STRAGGLER_GRACE_TICKS - 30, objectives, bodies, topology)).stage());
        CompanyState committed = company(pulse(muster, AssaultMuster.STRAGGLER_GRACE_TICKS + 30, objectives, bodies, topology));
        assertEquals("ASSAULT", committed.stage());
        assertTrue(committed.strength() < committed.required());
    }

    @Test
    void aSquadStillOnTheWayAtTheTimeoutGoesStraightAtTheObjectiveNotToTheRally() {
        CommandTopology topology = openTopology();
        AssaultMuster muster = new AssaultMuster();
        List<Objective> objectives = List.of(fort(1, 160, 150, 0));
        List<Body> bodies = List.of(squad(1, 20, 150, 4), squad(2, 22, 150, 4), squad(3, 20, 152, 4));
        CompanyState first = company(pulse(muster, 0, objectives, bodies, topology));
        assertTrue(first.rallyCellX() > DROP[0] + 40, "a far fort is mustered for on the approach, at " + first.rallyCellX());
        Result late = pulse(muster, AssaultMuster.MUSTER_TIMEOUT_TICKS + 700, objectives, bodies, topology);
        assertEquals("ASSAULT", company(late).stage());
        int sent = 0;
        for (int id : company(late).squadIds()) {
            assertEquals(AssignmentKind.ATTACK_MOVE, late.orders().get(id).assignment().kind());
            sent++;
        }
        assertTrue(sent > 0);
    }

    @Test
    void aSquadInContactIsNeverRecruited() {
        CommandTopology topology = openTopology();
        Body fighting = new Body(1, 20, 150, 12, false, false, true, null);
        Result result = pulse(new AssaultMuster(), 0, List.of(fort(1, 70, 150, 0)), List.of(fighting), topology);
        assertTrue(result.orders().isEmpty());
        assertTrue(result.companies().isEmpty(), "no free squad, no company");
    }

    @Test
    void aMechLanceJoinsTheCompanyAndStandsOffOutsideTheReachAsBaseOfFire() {
        CommandTopology topology = openTopology();
        List<Body> bodies = new ArrayList<>();
        bodies.add(squad(1, 20, 150, 12));
        bodies.add(squad(2, 22, 150, 12));
        bodies.add(mech(3, 24, 150, 4)); // four mechs: twelve marine-equivalents
        bodies.add(squad(4, 40, 150, 12));
        Result result = pulse(new AssaultMuster(), 0, List.of(fort(1, 70, 150, 0)), bodies, topology);
        CompanyState committed = company(result);
        assertEquals("ASSAULT", committed.stage());
        assertTrue(committed.squadIds().contains(3), "the lance is in the company: " + committed.squadIds());
        ObjectiveAssignment order = result.orders().get(3).assignment();
        assertEquals(AssignmentKind.ATTACK_MOVE, order.kind());
        double gap = Math.hypot(order.targetCellX() - 70, order.targetCellY() - 150);
        assertTrue(gap >= 5 + DefenceStructureTactics.BASE_OF_FIRE_EXTRA_CELLS, "outside the reach, was " + gap);
    }

    @Test
    void aBrokenCompanyIsCountedAndTheNextMusterBelievesTheFortTakesMore() {
        CommandTopology topology = openTopology();
        AssaultMuster muster = new AssaultMuster();
        List<Objective> objectives = List.of(fort(1, 70, 150, 0));
        List<Body> strong = new ArrayList<>();
        for (int i = 1; i <= 8; i++) strong.add(squad(i, 20 + i, 150, 12));
        CompanyState committed = company(pulse(muster, 0, objectives, strong, topology));
        assertEquals("ASSAULT", committed.stage());
        // Everyone who went in is dead; the foothold squad and one other are all that is left.
        List<Body> remnant = List.of(squad(1, 21, 150, 1), squad(8, 28, 150, 12));
        Result broken = pulse(muster, 600, objectives, remnant, topology);
        assertTrue(broken.notes().stream().anyMatch(n -> n.contains("broken")), "the loss is recorded: " + broken.notes());
        CompanyState again = company(broken);
        assertEquals("MUSTER", again.stage(), "a fresh company forms");
        assertTrue(again.required() > AssaultMuster.required(Family.BATTERY_FORT, 0, 0), "and it asks for more");
    }

    @Test
    void anObjectiveThatIsGoneDissolvesItsCompanyAndFreesTheSquads() {
        CommandTopology topology = openTopology();
        AssaultMuster muster = new AssaultMuster();
        List<Body> bodies = List.of(squad(1, 20, 150, 12), squad(2, 50, 150, 12));
        pulse(muster, 0, List.of(fort(1, 70, 150, 0)), bodies, topology);
        assertTrue(muster.active());
        Result after = pulse(muster, 90, List.of(), bodies, topology);
        assertFalse(muster.active());
        assertTrue(after.orders().isEmpty());
        assertTrue(after.notes().stream().anyMatch(n -> n.contains("dissolved")));
    }

    @Test
    void aSquadThatRunsWellAheadOfTheCompanyHoldsForABoundedWhileThenCarriesOn() {
        CommandTopology topology = openTopology();
        AssaultMuster muster = new AssaultMuster();
        // A far fort: the company stages well up the approach and commits there.
        List<Objective> objectives = List.of(fort(1, 250, 150, 0));
        List<Body> gathered = new ArrayList<>();
        for (int i = 1; i <= 8; i++) gathered.add(squad(i, 194, 150, 12));
        CompanyState committed = company(pulse(muster, 0, objectives, gathered, topology));
        assertEquals("ASSAULT", committed.stage());
        assertEquals(List.of(1, 2), committed.squadIds());
        // Squad 1 has run to 35 cells from the fort; squad 2 is 65 out. Thirty cells apart is past the bound of 24.
        List<Body> strung = new ArrayList<>(gathered);
        strung.set(0, squad(1, 215, 150, 12));
        strung.set(1, squad(2, 185, 150, 12));
        Result holding = pulse(muster, 60, objectives, strung, topology);
        assertEquals(AssignmentReason.ASSAULT_COMPANY_RALLY, holding.orders().get(1).reason());
        assertEquals(AssignmentKind.ADVANCE_TRACK, holding.orders().get(1).assignment().kind());
        assertEquals(215, holding.orders().get(1).assignment().targetCellX(), "it holds where it stands");
        assertEquals(AssignmentReason.ASSAULT_COMMIT, holding.orders().get(2).reason(), "the rear squad is not held");
        Result stillHolding = pulse(muster, 60 + ConquestGroupCommand.WAIT_TICKS - 30, objectives, strung, topology);
        assertEquals(AssignmentReason.ASSAULT_COMPANY_RALLY, stillHolding.orders().get(1).reason());
        Result released = pulse(muster, 60 + ConquestGroupCommand.WAIT_TICKS + 30, objectives, strung, topology);
        assertEquals(AssignmentReason.ASSAULT_COMMIT, released.orders().get(1).reason(),
                "the wait is bounded, so the lead is released to carry on");
    }

    @Test
    void aSquadInContactOrInsideTheEngageRingIsNeverHeldBack() {
        CommandTopology topology = openTopology();
        AssaultMuster muster = new AssaultMuster();
        List<Objective> objectives = List.of(fort(1, 250, 150, 0));
        List<Body> gathered = new ArrayList<>();
        for (int i = 1; i <= 8; i++) gathered.add(squad(i, 194, 150, 12));
        pulse(muster, 0, objectives, gathered, topology);
        List<Body> strung = new ArrayList<>(gathered);
        strung.set(0, new Body(1, 215, 150, 12, false, false, true, null)); // in contact
        strung.set(1, squad(2, 185, 150, 12));
        assertEquals(AssignmentReason.ASSAULT_COMMIT, pulse(muster, 60, objectives, strung, topology).orders().get(1).reason());
        strung.set(0, squad(1, 235, 150, 12)); // 15 cells from the fort: in the fight already
        assertEquals(AssignmentReason.ASSAULT_COMMIT, pulse(muster, 90, objectives, strung, topology).orders().get(1).reason());
    }
}
