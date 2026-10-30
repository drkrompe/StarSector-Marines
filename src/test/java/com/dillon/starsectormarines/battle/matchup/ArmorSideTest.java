package com.dillon.starsectormarines.battle.matchup;

import com.dillon.starsectormarines.battle.mech.MechFrame;
import com.dillon.starsectormarines.battle.mech.MechRole;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.mech.MechWeaponComponent;
import com.dillon.starsectormarines.battle.vehicle.VehicleStockFit;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import com.dillon.starsectormarines.marine.MechFitSpace;
import com.dillon.starsectormarines.marine.MechInternal;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** An armor side is written like a kit: machines, fits and counts, parsed and printed back the same. */
class ArmorSideTest {

    @Test
    void bareChassisIsTheFactoryMech() {
        ArmorSide.Mech hound = mech("hound");
        assertEquals(MechVariant.HOUND, hound.variant());
        assertEquals(MechVariant.HOUND.arms, hound.fit().arms());
        assertEquals(MechFrame.stock(MechVariant.HOUND), hound.frame());
        assertEquals(MechVariant.HOUND.defaultRole, hound.role());
        assertEquals("hound", hound.label());
        assertEquals(hound, mech("mech:hound:stock"));
    }

    @Test
    void refitIsTheBestBuildableFrameAroundTheWeapons() {
        ArmorSide.Mech refit = mech("bulwark:refit");
        assertEquals(MechFitSpace.bestRefitAroundStockWeapons(MechVariant.BULWARK), refit.fit());
        assertFalse(refit.fit().overBudget());
        assertTrue(refit.frame().armorCapacity() > MechVariant.BULWARK.armorCapacity);
        assertEquals("bulwark:refit", refit.label());
    }

    @Test
    void weaponsAndInternalsByPatternId() {
        ArmorSide.Mech custom = mech("bulwark:arms=hc-10,rs=component.mech-ll-3,"
                + "engine=uprated,capacitors=internal.capacitor-bank-double,vents=auxiliary,role=assault");
        assertEquals(MechWeaponComponent.HC_10, custom.fit().leftArm());
        assertEquals(MechWeaponComponent.HC_10, custom.fit().rightArm(), "arms= fits every arm");
        assertEquals(MechWeaponComponent.SRM_15, custom.fit().leftShoulder());
        assertEquals(MechWeaponComponent.LL_3, custom.fit().rightShoulder());
        assertEquals(MechInternal.UPRATED_ENGINE, custom.fit().engine());
        assertEquals(MechInternal.DOUBLE_CAPACITOR_BANK, custom.fit().capacitorBank());
        assertEquals(MechInternal.AUXILIARY_VENT_ARRAY, custom.fit().ventArray());
        assertEquals(MechRole.ASSAULT, custom.role());
        assertEquals(custom, mech(custom.label()));
    }

    @Test
    void oneArmIsFittedOnItsOwn() {
        ArmorSide.Mech mixed = mech("bulwark:la=hc-10");
        assertEquals(MechWeaponComponent.HC_10, mixed.fit().leftArm());
        assertEquals(MechVariant.BULWARK.rightArm, mixed.fit().rightArm());
        assertEquals(mixed, mech(mixed.label()));
    }

    @Test
    void refitAppliesAfterTheWeaponsWhereverItIsWritten() {
        assertEquals(mech("bulwark:arms=hc-10,refit"), mech("bulwark:refit,arms=hc-10"));
        assertEquals("bulwark:arms=hc-10,refit", mech("bulwark:refit,arms=hc-10").label());
    }

    @Test
    void emptyMountAndOverBudgetAreAllowedAndShown() {
        ArmorSide.Mech bare = mech("bulwark:rs=none");
        assertNull(bare.fit().rightShoulder());
        assertEquals("bulwark:rs=none", bare.label());
        ArmorSide.Mech heavy = mech("hound:arms=dc-5,engine=military,plating=heavy,capacitors=double");
        assertTrue(heavy.fit().overBudget());
        assertTrue(heavy.label().endsWith("!over-budget"));
    }

    @Test
    void vehiclesByKebabNameAndAlias() {
        ArmorSide.Vehicle tank = assertInstanceOf(ArmorSide.Vehicle.class, ArmorSide.parseUnit("breakthrough-tank"));
        assertEquals(VehicleType.BREAKTHROUGH_TANK, tank.type());
        assertEquals(VehicleType.MILITIA_TECHNICAL, vehicle("technical"));
        assertEquals(VehicleType.SCOUT_CAR, vehicle("vehicle:scout"));
        assertEquals(VehicleType.HEAVY_APC, vehicle("heavy-apc"));
        ArmorSide.Vehicle parapet = assertInstanceOf(ArmorSide.Vehicle.class, ArmorSide.parseUnit("parapet"));
        assertEquals(VehicleType.BREAKTHROUGH_TANK, parapet.type());
        assertEquals(VehicleStockFit.PARAPET.kits, parapet.kits());
        assertEquals("parapet", parapet.label());
    }

    @Test
    void countsAndMixedSides() {
        ArmorSide side = ArmorSide.parse("2xhound:refit + heavy-apc + 3*warden");
        assertEquals(3, side.units().size());
        assertEquals(6, side.size());
        assertEquals(2, side.units().get(0).count());
        assertEquals("2xhound:refit+heavy-apc+3xwarden", side.label());
        assertEquals(side, ArmorSide.parse(side.label()));
    }

    @Test
    void mistakesSayWhatIsLegal() {
        IllegalArgumentException socket = assertThrows(IllegalArgumentException.class,
                () -> ArmorSide.parse("hound:ls=lrm-15"));
        assertTrue(socket.getMessage().contains("legal"), socket.getMessage());
        assertThrows(IllegalArgumentException.class, () -> ArmorSide.parse("hound:arms=railgun"));
        assertThrows(IllegalArgumentException.class, () -> ArmorSide.parse("hound:wings=2"));
        assertThrows(IllegalArgumentException.class, () -> ArmorSide.parse("hound:plating=none"));
        assertThrows(IllegalArgumentException.class, () -> ArmorSide.parse("hovertank"));
        assertThrows(IllegalArgumentException.class, () -> ArmorSide.parse("bulwark:role=pilot"));
        assertThrows(IllegalArgumentException.class, () -> ArmorSide.parse(""));
    }

    @Test
    void defaultLadderCoversEveryGroup() {
        int fits = MechVariant.values().length * 2;
        int vehicles = ArmorMatchupEvidence.LADDER_VEHICLES.size();
        assertEquals(1, ArmorMatchupEvidence.ladder(Set.of("control")).size());
        assertEquals(fits * (fits - 1), ArmorMatchupEvidence.ladder(Set.of("mech")).size());
        assertEquals(2 * MechVariant.values().length * vehicles, ArmorMatchupEvidence.ladder(Set.of("mixed")).size());
        assertEquals(vehicles * (vehicles - 1), ArmorMatchupEvidence.ladder(Set.of("vehicle")).size());
    }

    private static ArmorSide.Mech mech(String text) {
        return assertInstanceOf(ArmorSide.Mech.class, ArmorSide.parseUnit(text));
    }

    private static VehicleType vehicle(String text) {
        return assertInstanceOf(ArmorSide.Vehicle.class, ArmorSide.parseUnit(text)).type();
    }
}
