package com.dillon.starsectormarines.battle.mech;

import com.dillon.starsectormarines.battle.mech.components.MechLoadoutComponent;
import com.dillon.starsectormarines.battle.vehicle.VehicleKit;
import com.dillon.starsectormarines.battle.weapon.LoaderPace;
import com.dillon.starsectormarines.battle.weapon.RotaryFeedDef;
import com.dillon.starsectormarines.battle.weapon.WeaponRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** The loader's one rule: it paces missile packs and rotary hoppers alike, and the authored rates are the standard's. */
class LoaderPaceTest {

    private static final float DT = 0.1f;
    /** A belt of 100 refilled at four rounds a second. */
    private static final RotaryFeedDef GUN = new RotaryFeedDef(0f, 0f, 0f, 10f, 10f, 100, 4f, 0.5f);

    /** Empties {@code feed} in one long pull, then lets it refill for {@code seconds}; returns the rounds put back. */
    private static int refilledOver(RotaryFeed feed, float seconds) {
        for (int i = 0; i < 1000 && feed.beltRounds() > 0; i++) {
            int rounds = feed.advance(DT, true);
            for (int r = 0; r < rounds; r++) feed.spend();
        }
        int dry = feed.beltRounds();
        for (int i = 0; i < Math.round(seconds / DT); i++) feed.advance(DT, false);
        return feed.beltRounds() - dry;
    }

    @Test
    void theAcceleratedLoaderRefillsAHopperAThirdFasterAndTheStandardOneAtTheGunsOwnRate() {
        assertEquals(40, refilledOver(new RotaryFeed(GUN), 10f), 1, "the authored rate is the standard loader's");
        assertEquals(53, refilledOver(new RotaryFeed(GUN, LoaderPace.ACCELERATED, 1f), 10f), 1,
                "a pack in three quarters of the time: four thirds the rounds");
    }

    @Test
    void missilePacksKeepTheirCadenceAndOnlyThePacksStopWithoutALoader() {
        assertEquals(12f, MissileReplenisherComponent.STANDARD.replenishmentSeconds(WeaponRegistry.MECH_SRM_POD_ID));
        assertEquals(18f, MissileReplenisherComponent.STANDARD.replenishmentSeconds(WeaponRegistry.MECH_LRM_ARTILLERY_ID));
        assertEquals(9f, MissileReplenisherComponent.ACCELERATED_FEED.replenishmentSeconds(WeaponRegistry.MECH_SRM_POD_ID));
        assertEquals(13.5f,
                MissileReplenisherComponent.ACCELERATED_FEED.replenishmentSeconds(WeaponRegistry.MECH_LRM_ARTILLERY_ID));
        assertEquals(Float.POSITIVE_INFINITY,
                MissileReplenisherComponent.NONE.replenishmentSeconds(WeaponRegistry.MECH_SRM_POD_ID));

        assertEquals(LoaderPace.STANDARD, MissileReplenisherComponent.STANDARD.loaderFactor(), 1e-6f);
        assertEquals(LoaderPace.ACCELERATED, MissileReplenisherComponent.ACCELERATED_FEED.loaderFactor(), 1e-5f);
        assertEquals(LoaderPace.STANDARD, MissileReplenisherComponent.NONE.loaderFactor(), 1e-6f,
                "a gun's hopper is its own: without a loader it keeps the gun's rate");
    }

    @Test
    void aMechsLoaderPacesEveryRotaryGunItCarries() {
        MechLoadoutComponent loadout = MechVariant.HOUND.createLoadout(MechRole.ASSAULT);
        MechWeaponMount gun = null;
        for (MechWeaponMount mount : loadout.mounts()) if (mount != null && mount.isRotary()) gun = mount;
        assertNotNull(gun, "the Hound carries a rotary gun");
        float authored = gun.component.rotary().refillPerSecond();
        assertEquals(authored, gun.rotary.def().refillPerSecond(), 1e-5f, "the stock loader is the gun's own rate");

        loadout.installMissileReplenisher(MissileReplenisherComponent.ACCELERATED_FEED);
        assertEquals(authored * LoaderPace.ACCELERATED, gun.rotary.def().refillPerSecond(), 1e-4f);

        loadout.installMissileReplenisher(MissileReplenisherComponent.NONE);
        assertEquals(authored, gun.rotary.def().refillPerSecond(), 1e-5f, "no loader strands no gun");
    }

    @Test
    void aVehiclesKitsPaceTheHopperAndDeepenTheBelt() {
        VehicleKit.Effect kits = VehicleKit.Effect.of(List.of(VehicleKit.ACCELERATED_LOADER, VehicleKit.READY_RACK));
        RotaryFeed feed = new RotaryFeed(GUN, kits.loaderFactor(), kits.ammoFactor());
        assertEquals(150, feed.beltRounds(), "half again the rounds aboard");
        assertEquals(150, feed.def().belt());
        assertEquals(4f * LoaderPace.ACCELERATED, feed.def().refillPerSecond(), 1e-4f);
    }
}
