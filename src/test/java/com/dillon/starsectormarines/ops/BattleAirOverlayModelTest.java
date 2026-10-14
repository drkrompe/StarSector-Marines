package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.airsupport.AirPoolKind;
import com.dillon.starsectormarines.battle.airsupport.AirPoolView;
import com.dillon.starsectormarines.battle.airsupport.AirPoolView.KindRow;
import com.dillon.starsectormarines.battle.airsupport.AirPoolView.Mission;
import com.dillon.starsectormarines.battle.airsupport.AirPoolView.SortieRow;
import com.dillon.starsectormarines.ops.BattleAirOverlayModel.KindCard;
import com.dillon.starsectormarines.ops.BattleAirOverlayModel.Projection;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BattleAirOverlayModelTest {

    private static KindRow bombers(int available, int out, int cap, int lost, float next) {
        return new KindRow(AirPoolKind.BOMBER, available, out, cap, lost, next);
    }

    private static KindRow fighters(int available, int out, int cap, int lost, float next) {
        return new KindRow(AirPoolKind.FIGHTER, available, out, cap, lost, next);
    }

    @Test
    void aBattleWithNoPoolShowsNothing() {
        assertFalse(BattleAirOverlayModel.project(AirPoolView.NONE).visible());
        assertFalse(BattleAirOverlayModel.project(null).visible());
    }

    @Test
    void anIdleFullPoolReadsReadyWithNoCaptionLines() {
        Projection p = BattleAirOverlayModel.project(new AirPoolView(
                List.of(bombers(3, 0, 3, 0, -1f), fighters(2, 0, 2, 0, -1f)), List.of(), 0, 0));
        assertTrue(p.visible());
        assertEquals("READY", p.status());
        assertEquals("air-status-ready", p.statusClass());
        assertEquals("3/3", p.kinds().get(0).count());
        assertEquals("", p.kinds().get(0).regen());
        assertEquals("width: 100%;", p.kinds().get(0).readyStyle());
        assertEquals("width: 0%;", p.kinds().get(0).flightStyle());
        assertTrue(p.lines().isEmpty());
    }

    @Test
    void sortiesInFlightAreCountedAndNamedByKindAndMission() {
        AirPoolView view = new AirPoolView(
                List.of(bombers(1, 2, 3, 0, 20f), fighters(1, 1, 2, 0, -1f)),
                List.of(new SortieRow(AirPoolKind.BOMBER, Mission.STRIKE),
                        new SortieRow(AirPoolKind.BOMBER, Mission.STRIKE),
                        new SortieRow(AirPoolKind.FIGHTER, Mission.COVER)), 0, 0);
        Projection p = BattleAirOverlayModel.project(view);
        assertEquals("3 IN FLIGHT", p.status());
        assertEquals("air-status-flight", p.statusClass());
        assertEquals("STRIKE BOMBER x2 / COVER FIGHTER", p.lines().get(0).text());
        assertEquals("width: 33%;", p.kinds().get(0).readyStyle());
        assertEquals("width: 67%;", p.kinds().get(0).flightStyle());
    }

    @Test
    void aCallThatHasNotLaunchedReadsCalling() {
        Projection p = BattleAirOverlayModel.project(new AirPoolView(
                List.of(bombers(2, 1, 3, 0, -1f)), List.of(), 1, 0));
        assertEquals("CALLING", p.status());
    }

    @Test
    void aDepletedPoolShowsTheRegenerationClock() {
        Projection p = BattleAirOverlayModel.project(new AirPoolView(
                List.of(bombers(0, 0, 3, 3, 24.2f), fighters(0, 0, 2, 2, 65f)), List.of(), 0, 2));
        assertEquals("DEPLETED", p.status());
        assertEquals("air-status-depleted", p.statusClass());
        assertEquals("+1 IN 0:25", p.kinds().get(0).regen());
        assertEquals("+1 IN 1:05", p.kinds().get(1).regen());
        assertEquals("LOST 5 / 2 IN LAST 60S", p.lines().get(0).text());
    }

    @Test
    void aSideThatNeverReplacesLossesSaysSo() {
        KindCard card = BattleAirOverlayModel.kindCard(bombers(1, 0, 3, 2, -1f));
        assertEquals("NO RESUPPLY", card.regen());
        assertTrue(card.regenClasses().contains("air-kind-regen-none"));
    }

    @Test
    void lossesOutsideTheRecentWindowAreStillCounted() {
        assertEquals("LOST 1", BattleAirOverlayModel.lossLine(1, 0));
        assertEquals("", BattleAirOverlayModel.lossLine(0, 0));
    }

    @Test
    void clockRoundsUpAndPadsSeconds() {
        assertEquals("0:01", BattleAirOverlayModel.clock(0.2f));
        assertEquals("0:00", BattleAirOverlayModel.clock(0f));
        assertEquals("2:05", BattleAirOverlayModel.clock(125f));
    }

    @Test
    void plateHeightGrowsWithItsRows() {
        float two = BattleAirOverlay.documentHeight(2, 0);
        assertEquals(two + 17f, BattleAirOverlay.documentHeight(2, 1), 0.001f);
    }
}
