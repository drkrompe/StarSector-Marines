package com.dillon.starsectormarines.battle.world.gen;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** The story chapter's ground start sits on the rear apron, inside the wall and off both raider roads. */
class ReliefCompanyStartTest {
    @Test
    void companyAndTruckStandOnTheNorthernRearApron() {
        for (ReliefCheckpoint.Cell cell : new ReliefCheckpoint.Cell[]{
                ReliefCheckpoint.COMPANY_START, ReliefCheckpoint.HAULER}) {
            // Rear apron: x 16..34, y 34..53, north of the through-road and inside the compound wall.
            assertTrue(cell.x() >= 16 && cell.x() <= 34 && cell.y() >= 34 && cell.y() <= 53, cell.toString());
        }
        // The squad walks to the gate; it does not start at it.
        assertTrue(83 - ReliefCheckpoint.COMPANY_START.x() > 50);
        assertTrue(Math.abs(ReliefCheckpoint.COMPANY_START.x() - ReliefCheckpoint.HAULER.x()) >= 4,
                "the squad forms up beside the truck, not inside it");
    }
}
