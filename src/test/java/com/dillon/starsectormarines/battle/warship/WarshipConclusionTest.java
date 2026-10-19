package com.dillon.starsectormarines.battle.warship;

import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.testsupport.InstalledHullSpecs;
import com.dillon.starsectormarines.testsupport.InstalledVanillaData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A hull the battle waits for ({@link WarshipSystem#concludeBeforeBattleEnds}) concludes when it has left the map or
 * when it has been destroyed and its wreck has come to rest; until then the battle is held open.
 */
class WarshipConclusionTest {

    private static final float DT = 1f / 30f;

    @BeforeAll
    static void install() {
        InstalledHullSpecs.install();
        InstalledVanillaData.install();
    }

    private static Warship hound() throws Exception {
        Warship ship = Warship.fit("hound", "hound_Standard", Faction.DEFENDER, 50f, 50f, 0f);
        ship.serial(3);
        return ship;
    }

    private static void destroy(Warship ship) {
        for (int i = 0; i < 1000 && !ship.destroyed(); i++) ship.takeDamage(100_000f, 100f);
        assertTrue(ship.destroyed());
    }

    @Test
    void aHullStillOverTheBattleHoldsItOpen() throws Exception {
        WarshipSystem system = new WarshipSystem();
        Warship ship = hound();
        assertFalse(system.awaitingConclusion(), "nothing to wait for until a mission asks");
        system.concludeBeforeBattleEnds(ship);
        assertTrue(system.awaitingConclusion());
        assertFalse(system.concluded(ship));
    }

    @Test
    void aHullThatHasLeftTheMapHasConcluded() throws Exception {
        WarshipSystem system = new WarshipSystem();
        Warship ship = hound();
        system.concludeBeforeBattleEnds(ship);
        ship.markDeparted();
        assertTrue(system.concluded(ship));
        assertFalse(system.awaitingConclusion());
    }

    @Test
    void aDestroyedHullConcludesOnlyOnceItsWreckIsAtRest() throws Exception {
        Warship ship = hound();
        destroy(ship);
        assertFalse(WarshipSystem.concluded(ship, List.of()), "destroyed, but its wreck has not begun to fall");
        WarshipWreck wreck = new WarshipWreck(ship, -1L);
        assertFalse(WarshipSystem.concluded(ship, List.of(wreck)), "still falling");
        for (int i = 0; i < 10_000 && wreck.phase() == WarshipWreck.Phase.FALLING; i++) wreck.advance(DT);
        if (wreck.phase() == WarshipWreck.Phase.SKIDDING) {
            assertFalse(WarshipSystem.concluded(ship, List.of(wreck)), "still skidding");
        }
        for (int i = 0; i < 10_000 && wreck.phase() != WarshipWreck.Phase.DOWN; i++) wreck.advance(DT);
        assertTrue(WarshipSystem.concluded(ship, List.of(wreck)), "struck and at rest");
    }
}
