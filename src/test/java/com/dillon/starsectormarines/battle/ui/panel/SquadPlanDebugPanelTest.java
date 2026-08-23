package com.dillon.starsectormarines.battle.ui.panel;

import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.squad.SquadContactPicture;
import com.dillon.starsectormarines.battle.squad.SquadContactPicture.Doctrine;
import com.dillon.starsectormarines.battle.squad.SquadContactPicture.ForceBalance;
import com.dillon.starsectormarines.battle.squad.SquadContactPicture.Motion;
import com.dillon.starsectormarines.battle.squad.SquadContactPicture.Posture;
import com.dillon.starsectormarines.battle.squad.SquadContactPicture.Sector;
import com.dillon.starsectormarines.battle.ui.highlight.CellHighlight;
import com.dillon.starsectormarines.battle.ui.highlight.HighlightOverlay;
import com.dillon.starsectormarines.battle.unit.Faction;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SquadPlanDebugPanelTest {

    @Test
    void summariesExposeThePublishedContactDecision() {
        SquadContactPicture picture = picture(1f, 0f, Doctrine.DISENGAGE);

        assertEquals("Doctrine DISENGAGE   Posture ADVANCING   Odds UNFAVORABLE",
                SquadPlanDebugPanel.doctrineSummary(picture));
        assertEquals("Threat LEFT_FLANK   Motion APPROACHING   Seen D2/T3",
                SquadPlanDebugPanel.threatSummary(picture));
        assertEquals("Force H5.50/F3   Axis +1.00,+0.00",
                SquadPlanDebugPanel.forceSummary(picture));
        assertEquals("Primary Raider @18,12   Confidence 0.75",
                SquadPlanDebugPanel.primarySummary(picture, "Raider"));
        assertEquals("Primary —", SquadPlanDebugPanel.primarySummary(
                SquadContactPicture.NONE, "—"));
    }

    @Test
    void tacticalAxisIsBoundedNormalizedAndDoctrineColored() {
        Squad squad = new Squad(7, Faction.MARINE);
        squad.centroidX = 10.5f;
        squad.centroidY = 10.5f;
        squad.contactPicture = picture(2f, 0f, Doctrine.HOLD);

        List<CellHighlight> cells = SquadPlanDebugPanel.doctrineAxisCells(squad);

        assertEquals(SquadPlanDebugPanel.DOCTRINE_AXIS_TRACE_CELLS, cells.size());
        for (int i = 0; i < cells.size(); i++) {
            CellHighlight cell = cells.get(i);
            assertEquals(11 + i, cell.cellX);
            assertEquals(10, cell.cellY);
            assertEquals(HighlightOverlay.COLOR_DOCTRINE_HOLD, cell.color);
        }
    }

    @Test
    void tacticalAxisIsAbsentWhenThePictureHasNoDirection() {
        Squad squad = new Squad(8, Faction.MARINE);
        squad.contactPicture = SquadContactPicture.NONE;

        assertTrue(SquadPlanDebugPanel.doctrineAxisCells(squad).isEmpty());
    }

    private static SquadContactPicture picture(float axisX, float axisY,
                                               Doctrine doctrine) {
        return new SquadContactPicture(42, Posture.ADVANCING, axisX, axisY,
                3, 2, 5.5f, 3, ForceBalance.UNFAVORABLE,
                Sector.LEFT_FLANK, Motion.APPROACHING, 99L,
                18, 12, 0.75f, doctrine);
    }
}
