package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.setup.ColdAccountIntervention.Phase;
import com.dillon.starsectormarines.battle.setup.ColdAccountLayout;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ColdAccountMarkersTest {
    private final ColdAccountLayout layout = new ColdAccountLayout(10, 12, 90, 35, 30, 35, 25);

    @Test void openingOnlyDisclosesTheDispatchOffice() {
        var markers = ColdAccountMarkers.forPhase(Phase.EVIDENCE, layout);
        assertEquals(1, markers.size());
        assertEquals(ColdAccountMarkers.Kind.EVIDENCE, markers.get(0).kind());
        assertEquals("SECURE DISPATCH OFFICE", markers.get(0).label());
    }

    @Test void interventionReplacesEvidenceWithPickupAndActualExclusionBoundary() {
        for (Phase phase : new Phase[]{Phase.WITHDRAW, Phase.WARNING, Phase.BOMBARD}) {
            var markers = ColdAccountMarkers.forPhase(phase, layout);
            assertEquals(2, markers.size());
            assertEquals(ColdAccountMarkers.Kind.PICKUP, markers.get(0).kind());
            var danger = markers.get(1);
            assertEquals(ColdAccountMarkers.Kind.DANGER, danger.kind());
            assertEquals(layout.strikeRadius() + ColdAccountLayout.BLAST_MARGIN, danger.radius());
            assertEquals(layout.strikeX(), danger.x());
            assertEquals(layout.strikeY(), danger.y());
        }
    }

    @Test void completedInterventionRetiresItsInstructions() {
        for (Phase phase : new Phase[]{Phase.PATROL, Phase.AFTERMATH, Phase.COMPLETE, Phase.FAILED})
            assertTrue(ColdAccountMarkers.forPhase(phase, layout).isEmpty());
    }
}
