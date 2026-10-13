package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.warship.ShipSupportResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** The settlement rules for a called ship, decided without a game or a fleet. */
class ShipSupportSettlementTest {

    private static final float EPS = 1e-5f;

    private static final class FakeMember implements ShipSupportSettlement.Member {
        float hull;
        float cr;
        final float deployCost;
        int writes;

        FakeMember(float hull, float cr, float deployCost) {
            this.hull = hull; this.cr = cr; this.deployCost = deployCost;
        }

        @Override public float hullFraction() { return hull; }
        @Override public float combatReadiness() { return cr; }
        @Override public float deployCost() { return deployCost; }
        @Override public void setHullFraction(float fraction) { hull = fraction; writes++; }
        @Override public void setCombatReadiness(float fraction) { cr = fraction; writes++; }
    }

    private static ShipSupportResult survived(float fraction) {
        return new ShipSupportResult("m1", "Anvil", "onslaught", fraction, false);
    }

    private static ShipSupportResult destroyed() {
        return new ShipSupportResult("m1", "Anvil", "onslaught", 0.4f, true);
    }

    @Test
    void aCalledShipPaysItsDeployCostAndCarriesItsHullHomeProportionally() {
        FakeMember member = new FakeMember(0.8f, 0.7f, 0.12f);

        ShipSupportSettlement.apply(survived(0.5f), member);

        assertEquals(0.4f, member.hull, EPS, "half the hull left of 80% is 40%");
        assertEquals(0.58f, member.cr, EPS, "CR 70% less the Onslaught's 12% to deploy");
    }

    @Test
    void anUntouchedShipStillPaysReadinessButKeepsItsHull() {
        FakeMember member = new FakeMember(1f, 0.9f, 0.20f);

        ShipSupportSettlement.apply(survived(1f), member);

        assertEquals(1f, member.hull, EPS);
        assertEquals(0.70f, member.cr, EPS);
    }

    @Test
    void aShipTheBattleDestroyedComesBackCrippledNotLost() {
        FakeMember member = new FakeMember(0.9f, 0.7f, 0.12f);

        ShipSupportSettlement.apply(destroyed(), member);

        assertEquals(ShipSupportSettlement.HULL_FLOOR, member.hull, EPS);
        assertEquals(0f, member.cr, EPS);
    }

    @Test
    void aSurvivorNeverFallsBelowTheFloorAndNeverGainsHull() {
        assertEquals(ShipSupportSettlement.HULL_FLOOR,
                ShipSupportSettlement.plan(survived(0.01f), 0.9f, 0.5f, 0.12f).hullFraction(), EPS);
        assertEquals(0.05f, ShipSupportSettlement.plan(destroyed(), 0.05f, 0.5f, 0.12f).hullFraction(), EPS,
                "a hull already under the floor is not repaired by being destroyed");
        assertEquals(0.3f, ShipSupportSettlement.plan(survived(1f), 0.3f, 0.5f, 0.12f).hullFraction(), EPS);
    }

    @Test
    void readinessCannotGoBelowZero() {
        assertEquals(0f, ShipSupportSettlement.plan(survived(1f), 1f, 0.05f, 0.12f).combatReadiness(), EPS);
    }

    @Test
    void aResultNeedsAMemberToWriteTo() {
        assertThrows(IllegalArgumentException.class,
                () -> new ShipSupportResult(null, "x", "onslaught", 1f, false));
    }

    @Test
    void settlingNothingWritesNothing() {
        // An uncalled committed ship, and every debug or employer ship, never reaches the list.
        ShipSupportSettlement.settle(java.util.List.of());
        ShipSupportSettlement.settle(null);
    }
}
