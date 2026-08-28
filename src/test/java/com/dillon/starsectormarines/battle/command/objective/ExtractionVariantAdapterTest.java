package com.dillon.starsectormarines.battle.command.objective;

import com.dillon.starsectormarines.battle.evacuation.CivilianEvacuationTracker;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExtractionVariantAdapterTest {

    @Test
    void civilianAndSilentColonyBranchesRetainDistinctPayloadSemantics() {
        ExtractionPayloadObjective civilians = new CivilianEvacuationObjective(
                new CivilianEvacuationTracker(2), "COLONY-SURVIVORS",
                "colony survivor cohort", 12, 12, 2, 2, 1, false);
        ExtractionPayloadObjective archive = new ColonyArchiveObjective(
                15, 15, 0);

        assertEquals(ExtractionPayloadObjective.Kind.COHORT,
                civilians.payloadKind());
        assertEquals("COLONY-SURVIVORS", civilians.payloadId());
        assertEquals(2, civilians.egressCellX());
        assertEquals(ExtractionPayloadObjective.Kind.ARCHIVE,
                archive.payloadKind());
        assertEquals("COLONY-ARCHIVE", archive.payloadId());
        assertEquals(-1, archive.egressCellX(),
                "archive recovery remains a separate branch without fake egress");
        assertEquals(-1, archive.boardedElements(),
                "archive recovery must not manufacture cohort boarding");
    }
}
