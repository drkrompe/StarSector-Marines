package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.command.CounterattackTactics.Report;
import com.dillon.starsectormarines.battle.command.OpeningOperationCommandFacts.Place;
import com.dillon.starsectormarines.battle.command.OpeningOperationCommandPicture.Reason;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;

class CounterattackTacticsTest {
    private static final List<Place> STAGES = List.of(new Place("Gate", 20, 10),
            new Place("Yard", 40, 10), new Place("Depot", 60, 10));
    private static Report guard(int id, int alive, int x, boolean contact) {
        return new Report(id, alive, x, 10, contact);
    }
    @Test void quietGuardsKeepThreeDistinctPosts() {
        var policy = new CounterattackTactics(STAGES, true);
        var own = List.of(guard(0,4,20,false),guard(1,4,40,false),guard(2,4,60,false));
        policy.observe(own);
        for (int i=0;i<3;i++) assertEquals(STAGES.get(i), policy.intent(own.get(i)).place());
    }
    @Test void forwardContactCommitsTheExistingReserveAndQuietDoesNotRecallIt() {
        var policy = new CounterattackTactics(STAGES, true);
        var rear = guard(3,2,60,false);
        policy.observe(List.of(guard(0,4,20,true),guard(1,4,40,false),guard(2,2,60,false),rear));
        assertEquals(Reason.DEPOT_RESERVE_COMMITTED,policy.intent(rear).reason());
        assertEquals(STAGES.get(2), policy.intent(guard(2,2,60,false)).place());
        assertEquals(STAGES.get(1),policy.intent(rear).place());
        policy.observe(List.of(guard(0,4,20,false),guard(1,4,40,false),guard(2,2,60,false),rear));
        assertEquals(STAGES.get(1),policy.intent(rear).place());
    }
    @Test void halfStrengthGateGuardFallsBackOnceWithoutOscillation() {
        var policy = new CounterattackTactics(STAGES, true);
        policy.observe(List.of(guard(0,4,20,false)));
        var damaged = guard(0,2,20,false);
        policy.observe(List.of(damaged));
        assertEquals(Reason.DEPOT_FALLBACK, policy.intent(damaged).reason());
        assertEquals(STAGES.get(1),policy.intent(guard(0,2,40,false)).place());
        assertEquals(STAGES.get(1),policy.intent(guard(0,3,40,false)).place());
    }
    @Test void lossOfGateRecallsReserveAndLetsEngagedYardGuardFallBack() {
        var policy = new CounterattackTactics(STAGES, true);
        var rearGuard = guard(2,2,60,false);
        var reserve = guard(3,2,60,false);
        policy.observe(List.of(guard(0,4,20,true),guard(1,4,40,false),rearGuard,reserve));
        assertEquals(Reason.DEPOT_RESERVE_COMMITTED, policy.intent(reserve).reason());
        var yard = guard(1,4,40,true);
        policy.observe(List.of(yard,rearGuard,guard(3,2,45,false)));
        assertEquals(STAGES.get(2),policy.intent(yard).place());
        assertEquals(STAGES.get(2),policy.intent(reserve).place());
    }
    @Test void advanceFollowsOwnArrivalWithoutAskingWhetherEnemiesAreDead() {
        var policy = new CounterattackTactics(STAGES, false);
        assertEquals(STAGES.get(0), policy.intent(guard(8,6,0,false)).place());
        assertEquals(STAGES.get(1), policy.intent(guard(8,6,20,true)).place());
        assertEquals(STAGES.get(1), policy.intent(guard(8,6,0,false)).place());
        assertEquals(STAGES.get(2), policy.intent(guard(8,6,40,false)).place());
        assertEquals(STAGES.get(2), policy.intent(guard(8,6,60,false)).place());
    }
}
