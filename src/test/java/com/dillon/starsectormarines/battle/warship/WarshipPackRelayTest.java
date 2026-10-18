package com.dillon.starsectormarines.battle.warship;

import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.warship.WarshipManoeuvre.Role;
import com.dillon.starsectormarines.testsupport.InstalledHullSpecs;
import com.dillon.starsectormarines.testsupport.InstalledVanillaData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The pack relay's pure pieces ({@link WarshipTactics}): how much of a threat a hull is to the one choosing, who holds
 * the target and who strikes, the swap when the tank runs high, and the venting ship holding its fire.
 */
class WarshipPackRelayTest {

    @BeforeAll
    static void install() {
        InstalledHullSpecs.install();
        InstalledVanillaData.install();
    }

    /** A Rusk Wolf at {@code (x, y)} facing {@code facing}, fighting hulls, its pool on. */
    private static Warship wolf(float x, float y, float facing) throws Exception {
        Warship w = Warship.fit("wolf_hegemony", "wolf_hegemony_Rusk", Faction.ALLY, x, y, facing);
        w.shipFire(ShipFireOrder.ALL);
        w.limitFlux();
        return w;
    }

    @Test
    void aHullIsAThreatWhenItsGunsAreOnTheChooserFromInsideItsReach() throws Exception {
        // Facing 0 the nose is along +y. A Venture due north of the Wolf, inside its reach.
        Warship venture = Warship.fit("venture", "venture_Greyhook", Faction.DEFENDER, 0f, 120f, 180f);
        Warship on = wolf(0f, 0f, 0f);
        Warship away = wolf(0f, 0f, 180f);
        assertTrue(WarshipTactics.menace(on, venture) > 0.5f, "nose and guns on it: " + WarshipTactics.menace(on, venture));
        assertEquals(0f, WarshipTactics.menace(away, venture), 1e-4f, "turned away, nothing bears");
        Warship far = wolf(0f, 120f - 3f * WarshipShipFire.longestReach(on), 0f);
        assertEquals(0f, WarshipTactics.menace(far, venture), 1e-4f, "out of its own reach, no threat yet");
    }

    @Test
    void oneWolfHoldsTheTargetAndTheOtherStrikesThenTheySwapWhenTheTankRunsHigh() throws Exception {
        Warship venture = Warship.fit("venture", "venture_Greyhook", Faction.DEFENDER, 0f, 120f, 180f);
        venture.shipFire(ShipFireOrder.ALL);
        Warship a = wolf(-20f, 0f, 0f);
        Warship b = wolf(20f, 0f, 0f);
        List<Warship> ships = List.of(venture, a, b);
        WarshipTactics tactics = new WarshipTactics();
        tactics.plan(0f, ships, false);
        Role ra = a.manoeuvre().role();
        Role rb = b.manoeuvre().role();
        assertTrue((ra == Role.TANK && rb == Role.STRIKER) || (ra == Role.STRIKER && rb == Role.TANK), ra + "/" + rb);
        Warship tank = ra == Role.TANK ? a : b;
        Warship striker = tank == a ? b : a;

        // The tank's pool runs high after it has held the role its minimum time: the fresh striker takes over.
        tank.fluxPool().spend(0.7f * tank.fluxPool().capacity());
        tactics.plan(WarshipTactics.RELAY_MIN_SECONDS + 1f, ships, false);
        assertSame(Role.TANK, striker.manoeuvre().role(), "the fresh partner takes the target");
        assertSame(Role.STRIKER, tank.manoeuvre().role());
        assertTrue(tank.manoeuvre().relayVenting(), "the old tank breaks off to vent");
        assertEquals(1, striker.manoeuvre().relaySwaps());
        // Venting, it holds its fire however little a salvo costs.
        assertFalse(WarshipShipFire.mayStart(tank, venture, 1f));
    }

    @Test
    void withTheRelayOffTheyShareTheHullAsBefore() throws Exception {
        Warship venture = Warship.fit("venture", "venture_Greyhook", Faction.DEFENDER, 0f, 120f, 180f);
        venture.shipFire(ShipFireOrder.ALL);
        Warship a = wolf(-20f, 0f, 0f);
        Warship b = wolf(20f, 0f, 0f);
        WarshipTactics tactics = new WarshipTactics();
        tactics.packRelay(false);
        tactics.plan(0f, List.of(venture, a, b), false);
        assertSame(Role.FREE, a.manoeuvre().role());
        assertSame(Role.FREE, b.manoeuvre().role());
    }

    @Test
    void aTankKeepsItsPoolLowEnoughForItsShield() throws Exception {
        Warship venture = Warship.fit("venture", "venture_Greyhook", Faction.DEFENDER, 0f, 120f, 180f);
        venture.limitFlux();
        Warship tank = wolf(0f, 0f, 0f);
        tank.manoeuvre().role = Role.TANK;
        float cap = tank.fluxPool().capacity();
        assertTrue(WarshipShipFire.mayStart(tank, venture, 0.1f * cap), "plenty of headroom");
        tank.fluxPool().spend(0.45f * cap);
        assertFalse(WarshipShipFire.mayStart(tank, venture, 0.1f * cap), "past its share it holds fire");
    }
}
