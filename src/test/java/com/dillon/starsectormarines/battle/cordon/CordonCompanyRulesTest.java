package com.dillon.starsectormarines.battle.cordon;

import com.dillon.starsectormarines.battle.cordon.CordonCompanyRules.Duty;
import com.dillon.starsectormarines.battle.cordon.CordonCompanyRules.Kind;
import com.dillon.starsectormarines.battle.cordon.CordonCompanyRules.Squad;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CordonCompanyRulesTest {

    private static final int NEVER = Integer.MAX_VALUE / 2;

    private static CordonRadio quiet(int n, int x) {
        return CordonRadio.report(n, x, 100, true, 0, 10, 10, NEVER, false, false);
    }

    private static List<Squad> company(int count) {
        List<Squad> out = new ArrayList<>();
        for (int i = 0; i < count; i++) out.add(new Squad(i, 200 + i * 8, 130, null));
        return out;
    }

    @Test
    void radioSplitsTheCallByItsCause() {
        CordonRadio breached = CordonRadio.report(1, 0, 0, true, 3, 10, 10, 0, false, false);
        assertTrue(breached.breached() && breached.unsettled() && !breached.bled());
        CordonRadio settling = CordonRadio.report(1, 0, 0, true, 0, 10, 10, CordonRadio.SETTLE_TICKS - 1, false, false);
        assertTrue(settling.unsettled() && !settling.breached());
        assertFalse(CordonRadio.report(1, 0, 0, true, 0, 10, 10, CordonRadio.SETTLE_TICKS, false, false).unsettled());
        CordonRadio bled = CordonRadio.report(1, 0, 0, true, 0, 5, 10, NEVER, false, false);
        assertTrue(bled.bled() && bled.wantsRelief());
        assertFalse(CordonRadio.report(1, 0, 0, true, 0, 5, 10, NEVER, false, true).wantsRelief());
        assertFalse(CordonRadio.report(1, 0, 0, false, 9, 0, 10, 0, true, false).wantsRelief());
    }

    @Test
    void aBreachTakesADetachmentNotTheCompany() {
        List<CordonRadio> radio = List.of(quiet(1, 100), CordonRadio.report(2, 220, 100, true, 4, 10, 10, 0, false, false));
        List<Duty> duties = CordonCompanyRules.duties(radio, List.of(), List.of(), List.of(), null);
        Map<Integer, Duty> out = CordonCompanyRules.assign(duties, company(6), CordonCompanyRules.reserveFloor(6, false));
        assertEquals(CordonCompanyRules.DETACHMENT, out.size());
        out.values().forEach(d -> assertEquals("relieve-2", d.key()));
    }

    @Test
    void theReliefHoldsUntilSettledAndKeepsItsSquads() {
        CordonRadio settling = CordonRadio.report(2, 220, 100, true, 0, 10, 10, 100, false, false);
        List<Duty> duties = CordonCompanyRules.duties(List.of(settling), List.of(), List.of(), List.of(), null);
        List<Squad> squads = new ArrayList<>(company(6));
        // Squads 5 and 4 are already on it, though 0 and 1 stand nearer.
        squads.set(5, new Squad(5, 900, 900, "relieve-2"));
        squads.set(4, new Squad(4, 900, 900, "relieve-2"));
        Map<Integer, Duty> out = CordonCompanyRules.assign(duties, squads, 2);
        assertEquals(2, out.size());
        assertTrue(out.containsKey(4) && out.containsKey(5));
    }

    @Test
    void bledWithHelpComingAsksForNobody() {
        CordonRadio helped = CordonRadio.report(2, 220, 100, true, 0, 4, 10, NEVER, false, true);
        assertTrue(CordonCompanyRules.duties(List.of(helped), List.of(), List.of(), List.of(), null).isEmpty());
        CordonRadio alone = CordonRadio.report(2, 220, 100, true, 0, 4, 10, NEVER, false, false);
        assertEquals(Kind.BLED, CordonCompanyRules.duties(List.of(alone), List.of(), List.of(), List.of(), null).get(0).kind());
    }

    @Test
    void theFloorHoldsOutsideATideAndGoesInOne() {
        List<CordonRadio> radio = new ArrayList<>();
        for (int n = 1; n <= 4; n++) radio.add(CordonRadio.report(n, n * 100, 100, true, 2, 10, 10, 0, false, false));
        List<Duty> duties = CordonCompanyRules.duties(radio, List.of(), List.of(), List.of(), null);
        assertEquals(4, CordonCompanyRules.assign(duties, company(6), CordonCompanyRules.reserveFloor(6, false)).size());
        assertEquals(6, CordonCompanyRules.assign(duties, company(6), CordonCompanyRules.reserveFloor(6, true)).size());
    }

    @Test
    void theRoadIsNotHeldToTheFloor() {
        List<Duty> duties = CordonCompanyRules.duties(List.of(), List.of(new int[]{0, 0}), List.of(new int[]{50, 0}),
                List.of(), null);
        assertEquals(5, CordonCompanyRules.assign(duties, company(6), 2).size());
    }

    @Test
    void beatsTakeAtMostHalfTheCompanyAndKeepTheirKeyAsTheTruckMoves() {
        List<int[]> beats = List.of(new int[]{10, 10, 2}, new int[]{20, 20, 2});
        List<Duty> duties = CordonCompanyRules.duties(List.of(), List.of(), List.of(), beats, null);
        assertEquals("beat-0", duties.get(0).key());
        // A call that names its beat keeps that beat's key whatever calls before it.
        assertEquals("beat-2", CordonCompanyRules.duties(List.of(), List.of(), List.of(),
                List.of(new int[]{10, 10, 1, 2}), null).get(0).key());
        assertEquals(3, CordonCompanyRules.assign(duties, company(6), 0).size());
    }

    @Test
    void theGateIsHeldAfterTheEscortAndOverTheFloor() {
        List<Duty> duties = CordonCompanyRules.duties(List.of(), List.of(), List.of(new int[]{50, 0}),
                List.of(new int[]{300, 300}), List.of(), null);
        assertEquals(List.of(Kind.CONVOY, Kind.GATE), duties.stream().map(Duty::kind).toList());
        // Six squads with a floor of two: the escort's three and the gate's two all go, as the road's duties do.
        Map<Integer, Duty> out = CordonCompanyRules.assign(duties, company(6), 2);
        assertEquals(CordonCompanyRules.DETACHMENT, out.values().stream().filter(d -> d.kind() == Kind.GATE).count());
        assertEquals(5, out.size());
    }

    @Test
    void aSquadDrawnToACallGoesBackToItsDutyAndOutranksItsStandIn() {
        assertEquals("gate-0", CordonCompanyRules.dutyOf("road-0", "gate-0", k -> !k.equals("road-0")));
        assertEquals("road-0", CordonCompanyRules.dutyOf("road-0", "gate-0", k -> true));
        assertEquals(null, CordonCompanyRules.dutyOf("road-0", "gate-0", k -> false));
        List<Duty> duties = CordonCompanyRules.duties(List.of(), List.of(), List.of(), List.of(new int[]{300, 300}),
                List.of(), null);
        // Squad 0 came back to the gate; 1 and 2 were sent from afar in its place: the nearest two keep it.
        List<Squad> squads = List.of(new Squad(0, 300, 295, "gate-0"), new Squad(1, 300, 160, "gate-0"),
                new Squad(2, 302, 290, "gate-0"));
        assertEquals(Map.of(0, duties.get(0), 2, duties.get(0)), CordonCompanyRules.assign(duties, squads, 0));
    }

    @Test
    void aBeatSendsInfantryBeforeANearerMachine() {
        List<Duty> duties = CordonCompanyRules.duties(List.of(), List.of(), List.of(), List.of(new int[]{200, 300, 1}), null);
        List<Squad> squads = List.of(new Squad(0, 200, 160, null, true, null), new Squad(1, 260, 130, null, false, null));
        assertEquals(Map.of(1, duties.get(0)), CordonCompanyRules.assign(duties, squads, 0));
        assertEquals(Map.of(0, duties.get(0)), CordonCompanyRules.assign(duties,
                List.of(squads.get(0), new Squad(2, 300, 130, null, true, null)), 0));
    }

    @Test
    void aSquadBarredFromItsDutyHandsItToTheNextNearest() {
        List<Duty> duties = CordonCompanyRules.duties(List.of(), List.of(), List.of(), List.of(new int[]{200, 300, 1}), null);
        List<Squad> squads = List.of(new Squad(0, 200, 290, "beat-0", false, "beat-0"), new Squad(1, 260, 130, null));
        assertEquals(Map.of(1, duties.get(0)), CordonCompanyRules.assign(duties, squads, 0));
    }

    @Test
    void aWalkingSquadStallsOnlyWhenItStopsGoing() {
        CordonCompanyRules.Progress progress = new CordonCompanyRules.Progress();
        int stall = CordonCompanyRules.STALL_TICKS;
        assertFalse(progress.stalled(1, "beat-0", 0, 0, 90, false, 0));
        assertFalse(progress.stalled(1, "beat-0", 0, 5, 85, false, stall - 1));
        assertTrue(progress.stalled(1, "beat-0", 0, 5, 85, false, stall));
        // Walking after an escort cell that runs ahead of it, never nearer, is going.
        assertFalse(progress.stalled(2, "convoy-0", 0, 0, 40, false, 0));
        assertFalse(progress.stalled(2, "convoy-0", 0, 12, 45, false, stall - 1));
        assertFalse(progress.stalled(2, "convoy-0", 0, 24, 50, false, 2 * stall - 2));
        // Standing on the duty or riding to it never stalls.
        assertFalse(progress.stalled(3, "relieve-1", 0, 0, 5, false, 0));
        assertFalse(progress.stalled(3, "relieve-1", 0, 0, 5, false, 3 * stall));
        assertFalse(progress.stalled(4, "beat-0", 0, 0, 90, true, 0));
        assertFalse(progress.stalled(4, "beat-0", 0, 0, 90, true, 3 * stall));
    }

    @Test
    void urbanWaitsAsTwoHammersAndTheTownAtItsHinge() {
        List<int[]> line = List.of(new int[]{100, 100}, new int[]{200, 100}, new int[]{300, 100}, new int[]{400, 100});
        List<int[]> hammers = CordonCompanyRules.reservePosts(CordonPlan.Density.URBAN, line, 250, 130, 6, false);
        assertEquals(6, hammers.size());
        assertEquals(150, hammers.get(1)[0]);
        assertEquals(350, hammers.get(4)[0]);
        assertEquals(130, hammers.get(0)[1]);
        List<int[]> hinge = CordonCompanyRules.reservePosts(CordonPlan.Density.TOWN, line, 250, 130, 2, true);
        assertEquals(100, hinge.get(0)[1]);
        assertEquals(250, hinge.get(0)[0]);
        List<int[]> rural = CordonCompanyRules.reservePosts(CordonPlan.Density.RURAL, line, 250, 130, 1, true);
        assertEquals(250, rural.get(0)[0]);
    }

    @Test
    void aLiftIsWorthItOnlyFarOutsideACityWithACarrierNearer() {
        assertTrue(CordonCompanyRules.liftWorth(CordonPlan.Density.RURAL, 0, 0, 150, 0, 10, 0));
        assertFalse(CordonCompanyRules.liftWorth(CordonPlan.Density.URBAN, 0, 0, 150, 0, 10, 0));
        assertFalse(CordonCompanyRules.liftWorth(CordonPlan.Density.TOWN, 0, 0, 80, 0, 10, 0));
        assertFalse(CordonCompanyRules.liftWorth(CordonPlan.Density.TOWN, 0, 0, 150, 0, 200, 0));
        // Fifty cells back to the base for a hundred and fifty out walks as far as the duty, for nothing.
        assertFalse(CordonCompanyRules.liftWorth(CordonPlan.Density.TOWN, 0, 0, 100, 0, -50, 0));
        assertTrue(CordonCompanyRules.liftWorth(CordonPlan.Density.TOWN, 0, 0, 150, 0, -20, 0));
    }

    @Test
    void theBaseOverrunIsServedLast() {
        List<Duty> duties = CordonCompanyRules.duties(List.of(quiet(1, 0)), List.of(), List.of(), List.of(), new int[]{5, 5});
        assertEquals(Kind.BASE, duties.get(duties.size() - 1).kind());
        assertEquals("RESERVE", CordonCommand.phase(Map.of()));
    }
}
