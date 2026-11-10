package com.dillon.starsectormarines.battle.derelict;

import com.dillon.starsectormarines.battle.derelict.FlockCommandPort.Contact;
import com.dillon.starsectormarines.battle.derelict.WalkerPosture.Kind;
import com.dillon.starsectormarines.battle.derelict.WalkerPosture.Order;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.dillon.starsectormarines.battle.derelict.DerelictWalkerFrame.LINE;
import static com.dillon.starsectormarines.battle.derelict.DerelictWalkerFrame.SIEGE;
import static com.dillon.starsectormarines.battle.derelict.DerelictWalkerFrame.VAULT;
import static org.junit.jupiter.api.Assertions.assertEquals;

class WalkerPostureTest {

    private static final List<int[]> EGRESS = List.of(new int[] {90, 0}, new int[] {0, 90});

    private static SecurityZone awake() {
        SecurityZone zone = new SecurityZone(0, 20, 20, 15);
        zone.stir(0);
        return zone;
    }

    private static Order decide(DerelictWalkerFrame frame, SecurityZone zone, boolean contingency,
                                List<Contact> pins, List<Contact> seen, List<Long> haul) {
        return WalkerPosture.decide(frame, zone, contingency, 20, 20, pins, seen, haul, EGRESS, 30f);
    }

    @Test
    void aDormantOrAlertZoneHoldsItsPostWhateverIsPinned() {
        SecurityZone zone = new SecurityZone(0, 20, 20, 15);
        List<Contact> pins = List.of(new Contact(7, 25, 25, 0));
        assertEquals(Kind.HOLD, decide(LINE, zone, false, pins, pins, List.of()).kind());
        assertEquals(Kind.HOLD, decide(SIEGE, zone, false, pins, pins, List.of()).kind());
        zone.hearRelay();
        assertEquals(Kind.HOLD, decide(LINE, zone, false, pins, pins, List.of()).kind());
    }

    @Test
    void aLineFrameComesToTheNearestPinInsideItsPerimeterOnly() {
        List<Contact> pins = List.of(new Contact(1, 33, 20, 0), new Contact(2, 26, 22, 0), new Contact(3, 60, 20, 0));
        Order order = decide(LINE, awake(), false, pins, List.of(), List.of());
        assertEquals(Kind.CLOSE, order.kind());
        assertEquals(2L, order.unitId());
        Order outside = decide(LINE, awake(), false, List.of(new Contact(3, 60, 20, 0)), List.of(), List.of());
        assertEquals(Kind.HOLD, outside.kind(), "a walker never follows past its perimeter");
    }

    @Test
    void aVaultFrameComesOnlyToAPinNearItsPost() {
        Order far = decide(VAULT, awake(), false, List.of(new Contact(1, 34, 20, 0)), List.of(), List.of());
        assertEquals(Kind.HOLD, far.kind());
        Order near = decide(VAULT, awake(), false, List.of(new Contact(1, 26, 20, 0)), List.of(), List.of());
        assertEquals(Kind.CLOSE, near.kind());
    }

    @Test
    void aSiegeFrameFiresOnTheFreshestSightingInReach() {
        List<Contact> seen = List.of(new Contact(1, 30, 20, 50), new Contact(2, 32, 22, 80),
                new Contact(3, 90, 90, 99));
        Order order = decide(SIEGE, awake(), false, List.of(), seen, List.of());
        assertEquals(Kind.FIRE, order.kind());
        assertEquals(2L, order.unitId());
        assertEquals(Kind.HOLD, decide(SIEGE, awake(), false, List.of(), List.of(), List.of()).kind());
    }

    @Test
    void inTheContingencyLineFramesHoldTheEgressTheVaultFrameTheVaultAndSiegeFramesFireOnTheHaul() {
        SecurityZone zone = awake();
        Order line = decide(LINE, zone, true, List.of(new Contact(1, 22, 20, 0)), List.of(), List.of());
        assertEquals(Kind.HOLD_EGRESS, line.kind());
        assertEquals(90, line.cellX());
        assertEquals(0, line.cellY());
        assertEquals(Kind.HOLD, decide(VAULT, zone, true, List.of(), List.of(), List.of()).kind(),
                "the vault frame guards the vault, not the way out");
        assertEquals(Kind.CLOSE, decide(VAULT, zone, true, List.of(new Contact(4, 24, 20, 0)), List.of(), List.of())
                .kind(), "and fights what reaches it");
        List<Contact> seen = List.of(new Contact(1, 30, 20, 90), new Contact(5, 28, 20, 10));
        Order siege = decide(SIEGE, zone, true, List.of(), seen, List.of(5L));
        assertEquals(5L, siege.unitId(), "the haul's escort first, however stale");
    }

    @Test
    void aZoneWithADeadNodeIsOutOfTheContingencysCall() {
        SecurityZone zone = awake();
        zone.loseNode();
        assertEquals(Kind.HOLD, decide(LINE, zone, true, List.of(), List.of(), List.of()).kind());
    }

    private static Order rounds(SecurityZone zone, boolean contingency, int[] round, boolean egressFits,
                                List<Contact> pins, DerelictWalkerFrame frame) {
        return WalkerPosture.decide(frame, zone, contingency, 20, 20, round, egressFits, pins, List.of(),
                List.of(), EGRESS, 30f);
    }

    @Test
    void aRoundsWalkerWalksItsRoundWhileItsZoneSleepsAndComesToAPinOnceAwake() {
        int[] place = {28, 14};
        SecurityZone zone = new SecurityZone(0, 20, 20, 15);
        List<Contact> pins = List.of(new Contact(4, 25, 25, 0));
        Order sleeping = rounds(zone, false, place, true, pins, DerelictWalkerFrame.ROUNDS);
        assertEquals(Kind.ROUND, sleeping.kind());
        assertEquals(28, sleeping.cellX());
        assertEquals(Kind.HOLD, rounds(zone, false, place, true, pins, LINE).kind(), "a heavy is no patrol");
        assertEquals(Kind.CLOSE, rounds(awake(), false, place, true, pins, DerelictWalkerFrame.ROUNDS).kind());
        assertEquals(Kind.ROUND, rounds(awake(), false, place, true, List.of(), DerelictWalkerFrame.ROUNDS).kind(),
                "awake with nothing pinned, it walks the round");
        assertEquals(Kind.HOLD, rounds(zone, false, null, true, pins, DerelictWalkerFrame.ROUNDS).kind(),
                "with no round it holds");
    }

    @Test
    void aHallFrameWalksToTheEgressOnlyWhereItsWayFits() {
        assertEquals(Kind.HOLD_EGRESS, rounds(awake(), true, null, true, List.of(), DerelictWalkerFrame.HALL).kind());
        assertEquals(Kind.HOLD, rounds(awake(), true, null, false, List.of(), DerelictWalkerFrame.HALL).kind(),
                "its way out is all corridor: it keeps its hall");
    }
}
