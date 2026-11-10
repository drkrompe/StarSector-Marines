package com.dillon.starsectormarines.battle.derelict;

import com.dillon.starsectormarines.battle.derelict.FieldTender.Goal;
import com.dillon.starsectormarines.battle.derelict.FieldTender.Kind;
import com.dillon.starsectormarines.battle.derelict.RepairDrone.Charge;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FieldTenderTest {

    private static final List<Charge> WORN = List.of(new Charge(1L, 10f, 10f, 0.9f), new Charge(2L, 20f, 10f, 0.5f));

    @Test
    void asleepItStandsAtItsPost() {
        Goal goal = new FieldTender().decide(15f, 10f, 0f, 0f, false, WORN, null);
        assertEquals(Kind.STAND_BY, goal.kind());
        assertEquals(0f, goal.x());
    }

    @Test
    void awakeItWalksToTheMostWornWalkerAndWeldsWithinReach() {
        FieldTender tender = new FieldTender();
        Goal far = tender.decide(0f, 10f, 0f, 0f, true, WORN, null);
        assertEquals(Kind.FOLLOW, far.kind());
        assertEquals(2L, far.entityId());
        assertEquals(Kind.WELD, tender.decide(17f, 10f, 0f, 0f, true, WORN, null).kind());
    }

    @Test
    void itKeepsItsChargeUntilWholeThoughAnotherWearsWorse() {
        FieldTender tender = new FieldTender();
        tender.decide(17f, 10f, 0f, 0f, true, WORN, null);
        List<Charge> later = List.of(new Charge(1L, 10f, 10f, 0.3f), new Charge(2L, 20f, 10f, 0.9f));
        assertEquals(2L, tender.decide(17f, 10f, 0f, 0f, true, later, null).entityId());
        List<Charge> whole = List.of(new Charge(1L, 10f, 10f, 0.3f), new Charge(2L, 20f, 10f, FieldTender.TENDER_WHOLE));
        assertEquals(1L, tender.decide(17f, 10f, 0f, 0f, true, whole, null).entityId());
    }

    @Test
    void withNothingWornItShadowsTheWorstWithoutWelding() {
        Goal goal = new FieldTender().decide(10f, 10f, 0f, 0f, true, List.of(new Charge(1L, 10f, 10f, 0.95f)), null);
        assertEquals(Kind.FOLLOW, goal.kind());
        assertEquals(0L, goal.entityId());
    }

    @Test
    void dryItReloadsAtTheBayAndWithNoBayItGoesHome() {
        FieldTender tender = new FieldTender();
        tender.draw(FieldTender.TENDER_STOCK / FieldTender.TENDER_RATE + 1f);
        assertEquals(0f, tender.stock());
        RepairDrone.Bay bay = new RepairDrone.Bay(40f, 40f, 600f);
        assertEquals(Kind.FOLLOW, tender.decide(17f, 10f, 0f, 0f, true, WORN, bay).kind());
        assertEquals(Kind.RELOAD, tender.decide(39f, 41f, 0f, 0f, true, WORN, bay).kind());
        assertEquals(Kind.STAND_BY, tender.decide(17f, 10f, 0f, 0f, true, WORN, null).kind());
        ServiceBay stocked = new ServiceBay();
        tender.reload(stocked);
        assertEquals(FieldTender.TENDER_STOCK, tender.stock());
        assertEquals(ServiceBay.STOCK - FieldTender.TENDER_STOCK, stocked.stock());
    }

    @Test
    void itDrawsAtItsRateAndTakesBackWhatWentUnlaid() {
        FieldTender tender = new FieldTender();
        float points = tender.draw(2f);
        assertEquals(2f * FieldTender.TENDER_RATE, points);
        tender.refund(points);
        assertEquals(FieldTender.TENDER_STOCK, tender.stock());
    }
}
