package com.dillon.starsectormarines.battle.warship;

import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.testsupport.InstalledHullSpecs;
import com.dillon.starsectormarines.testsupport.InstalledVanillaData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/** The pack's division of a hunt: the hunter nearer an escorted hull runs it down, the other pins an escort. */
class WarshipPackTest {

    @BeforeAll
    static void install() {
        InstalledHullSpecs.install();
        InstalledVanillaData.install();
    }

    private static Warship hunter(float x, float y) throws Exception {
        Warship wolf = Warship.fit("wolf_hegemony", "wolf_hegemony_Assault", Faction.ALLY, x, y, 0f);
        wolf.shipFire(ShipFireOrder.ALL);
        return wolf;
    }

    @Test
    void theNearerHunterRunsTheChargeDownAndTheOtherPinsItsEscort() throws Exception {
        Warship rig = Warship.fit("crig", "crig_Standard", Faction.DEFENDER, 100f, 50f, 180f);
        Warship hound = Warship.fit("hound_d_pirates", "hound_d_pirates_Standard", Faction.DEFENDER, 92f, 50f, 180f);
        hound.escort(rig);
        Warship near = hunter(70f, 50f);
        Warship far = hunter(40f, 40f);
        List<Warship> ships = List.of(rig, hound, near, far);
        assertSame(rig, WarshipShipFire.packTarget(near, ships));
        assertSame(hound, WarshipShipFire.packTarget(far, ships));
    }

    @Test
    void theRunnerKeepsTheRoleUntilAnotherIsClearlyNearer() throws Exception {
        Warship rig = Warship.fit("crig", "crig_Standard", Faction.DEFENDER, 100f, 50f, 180f);
        Warship hound = Warship.fit("hound_d_pirates", "hound_d_pirates_Standard", Faction.DEFENDER, 92f, 50f, 180f);
        hound.escort(rig);
        Warship a = hunter(70f, 50f);
        Warship b = hunter(70f + WarshipShipFire.PACK_SWITCH_CELLS - 1f, 44f);
        b.hullTarget(rig);
        List<Warship> ships = List.of(rig, hound, a, b);
        assertSame(rig, WarshipShipFire.packTarget(b, ships), "a little further off, it keeps the charge");
        assertSame(hound, WarshipShipFire.packTarget(a, ships));
    }

    @Test
    void aLoneHunterOrAnUnescortedHullIsNoPackMatter() throws Exception {
        Warship rig = Warship.fit("crig", "crig_Standard", Faction.DEFENDER, 100f, 50f, 180f);
        Warship hound = Warship.fit("hound_d_pirates", "hound_d_pirates_Standard", Faction.DEFENDER, 92f, 50f, 180f);
        hound.escort(rig);
        Warship lone = hunter(70f, 50f);
        assertNull(WarshipShipFire.packTarget(lone, List.of(rig, hound, lone)));
        Warship other = hunter(40f, 40f);
        Warship plain = Warship.fit("hound_d_pirates", "hound_d_pirates_Standard", Faction.DEFENDER, 92f, 20f, 180f);
        assertNull(WarshipShipFire.packTarget(lone, List.of(plain, lone, other)), "no hull is being escorted");
    }
}
