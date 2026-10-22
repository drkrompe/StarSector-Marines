package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.command.RescueCommandFacts.KnownBore;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Which bore the company goes for: its own lane's satellites first, then the nearest mother. */
class HeartAssaultCommandTest {

    /** Advancing north from x=100, with satellites within 30 cells across its own. */
    private final HeartAssaultCommand command = new HeartAssaultCommand(new HeartLane(true, 100, 30));

    @Test
    void satellitesInTheLaneComeBeforeAnyMother() {
        List<KnownBore> bores = List.of(
                new KnownBore("mother-1", true, 100, 40, true),
                new KnownBore("1", false, 20, 90, true),
                new KnownBore("2", false, 115, 70, true));
        assertEquals("2", command.target(bores, 100, 150).key(), "the far lane's nearer satellite is not the company's");
    }

    @Test
    void theNearestLivingMotherOnceTheLaneIsClear() {
        List<KnownBore> bores = List.of(
                new KnownBore("mother-1", true, 60, 20, true),
                new KnownBore("mother-2", true, 110, 25, true),
                new KnownBore("mother-3", true, 105, 22, false),
                new KnownBore("2", false, 115, 70, false),
                new KnownBore("1", false, 20, 90, true));
        assertEquals("mother-2", command.target(bores, 100, 60).key());
        assertNull(command.target(List.of(new KnownBore("mother-1", true, 60, 20, false)), 100, 60));
    }
}
