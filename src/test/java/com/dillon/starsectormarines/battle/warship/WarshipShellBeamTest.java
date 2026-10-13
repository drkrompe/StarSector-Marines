package com.dillon.starsectormarines.battle.warship;

import com.dillon.starsectormarines.battle.combat.PendingDetonation;
import com.dillon.starsectormarines.battle.unit.Faction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The sequence of a standoff missile: out, painting, then a beam nothing can shoot down. */
class WarshipShellBeamTest {

    private static WarshipShell dragon(float paint, float fire) {
        MissilePath path = MissilePath.straight(0f, 0f, 20f, 0f, 10f);
        PendingDetonation burn = WarshipSystem.detonation(Faction.MARINE, 50f, 0f, 4000f, 1f, 3f);
        return new WarshipShell(null, Faction.MARINE, null, 0f, 0f, 50f, 0f, path, 2f, 4000f, 1f, 3f,
                null, null, 600f, null, 0f)
                .withBeam(new WarshipShell.BeamPayload(paint, fire, burn, "dragon_payload"));
    }

    @Test
    void itFliesThenPaintsThenFiresAndIsSpentWhenTheBeamIsDone() {
        WarshipShell shell = dragon(3f, 1f);
        assertEquals(WarshipShell.BeamPhase.FLIGHT, shell.phase());
        assertEquals(6f, shell.lifeSeconds(), 1e-4f);
        shell.advance(2.5f);
        assertEquals(WarshipShell.BeamPhase.PAINT, shell.phase());
        assertFalse(shell.advance(1.0f));
        assertEquals(WarshipShell.BeamPhase.PAINT, shell.phase());
        shell.advance(2.0f);
        assertEquals(WarshipShell.BeamPhase.FIRE, shell.phase());
        assertTrue(shell.advance(1.0f));
    }

    @Test
    void itHoversWhereItsFlightEndedNotOverTheGround() {
        WarshipShell shell = dragon(3f, 1f);
        shell.advance(4f);
        assertEquals(20f, shell.x(), 1e-3f);
        assertEquals(50f, shell.landingX(), 0f);
    }

    @Test
    void pointDefenceMayStopItUntilTheBeamIsLoose() {
        WarshipShell shell = dragon(3f, 1f);
        shell.advance(2f);
        assertTrue(shell.canBeIntercepted());
        shell.advance(2.9f);
        assertTrue(shell.canBeIntercepted());
        shell.advance(0.2f);
        assertEquals(WarshipShell.BeamPhase.FIRE, shell.phase());
        assertFalse(shell.canBeIntercepted());
    }

    @Test
    void theBeamIsReleasedOnceAndTheFlightNeverLandsOnItsOwn() {
        WarshipShell shell = dragon(3f, 1f);
        shell.advance(2f);
        assertNull(shell.takeBeamRelease());
        assertNull(shell.landing());
        shell.advance(3.2f);
        assertNotNull(shell.takeBeamRelease());
        assertNull(shell.takeBeamRelease());
    }

    @Test
    void anOrdinaryRoundIsAlwaysInterceptableAndSpentOnLanding() {
        MissilePath path = MissilePath.straight(0f, 0f, 20f, 0f, 10f);
        WarshipShell shell = new WarshipShell(null, Faction.MARINE, null, 0f, 0f, 20f, 0f, path, 2f, 100f, 1f, 3f,
                null, null, 50f, null, 0f);
        assertTrue(shell.canBeIntercepted());
        assertEquals(shell.flightSeconds, shell.lifeSeconds(), 0f);
        assertTrue(shell.advance(2.1f));
    }
}
