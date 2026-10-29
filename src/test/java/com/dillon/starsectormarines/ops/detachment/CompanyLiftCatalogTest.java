package com.dillon.starsectormarines.ops.detachment;

import com.dillon.starsectormarines.battle.air.FittedBoat;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.fixture.CommandPowerCommitment;
import com.dillon.starsectormarines.battle.mech.MechDeploymentSpec;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.power.CommandPower;
import com.dillon.starsectormarines.battle.power.MechSupport;
import com.dillon.starsectormarines.marine.BoatFitting;
import com.dillon.starsectormarines.ops.Mission;
import com.dillon.starsectormarines.ops.MissionSource;
import com.dillon.starsectormarines.ops.MissionType;
import com.dillon.starsectormarines.ops.RiskLevel;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Which committed boat flies which drop power. */
class CompanyLiftCatalogTest {

    private static final FittedBoat SEATED = FittedBoat.standard(ShuttleType.BUFFALO);
    private static final FittedBoat LIGHT = cradled("light", BoatFitting.LIFT_CRADLE);
    private static final FittedBoat MEDIUM = cradled("medium", BoatFitting.HEAVY_CRADLE);

    @Test
    void aFleetGrantedPowerTakesTheLightestCradleThatLiftsItsLance() {
        Map<String, CommandPower> byId = new LinkedHashMap<>();
        byId.put(MechSupport.ID, MechSupport.configured(List.of(
                MechDeploymentSpec.standard(MechVariant.HOUND))));

        PowerCatalog.assignCompanyLift(byId, Set.of(MechSupport.ID), List.of(SEATED, MEDIUM, LIGHT), true);

        assertSame(LIGHT, ((MechSupport) byId.get(MechSupport.ID)).companyCarrier());
    }

    @Test
    void whereHeavyLiftIsProvidedAnEmployerOfferKeepsItsSyntheticCarrier() {
        Map<String, CommandPower> byId = new LinkedHashMap<>();
        byId.put(MechSupport.ID, new MechSupport());

        PowerCatalog.assignCompanyLift(byId, Set.of(), List.of(LIGHT), true);

        assertNull(((MechSupport) byId.get(MechSupport.ID)).companyCarrier());
    }

    @Test
    void withoutProvidedLiftTheBayFliesOnlyOnTheCompanysBoat() {
        Map<String, CommandPower> carried = new LinkedHashMap<>();
        carried.put(MechSupport.ID, MechSupport.configured(List.of(
                MechDeploymentSpec.standard(MechVariant.HOUND))));
        PowerCatalog.assignCompanyLift(carried, Set.of(), List.of(SEATED, LIGHT), false);
        MechSupport mech = (MechSupport) carried.get(MechSupport.ID);
        assertSame(LIGHT, mech.companyCarrier());
        assertFalse(mech.syntheticLift(), "no Valkyrie behind her");

        Map<String, CommandPower> unlifted = new LinkedHashMap<>();
        unlifted.put(MechSupport.ID, MechSupport.configured(List.of(
                MechDeploymentSpec.standard(MechVariant.HOUND))));
        PowerCatalog.assignCompanyLift(unlifted, Set.of(), List.of(SEATED), false);
        MechSupport stranded = (MechSupport) unlifted.get(MechSupport.ID);
        assertNull(stranded.companyCarrier());
        assertFalse(stranded.syntheticLift());
        assertTrue(stranded.budget().carriers().isEmpty(), "no boat lifts the bay, so nothing comes down");
    }

    @Test
    void onlyConquestProvidesHeavyLift() {
        assertTrue(PowerCatalog.providesHeavyLift(mission(MissionType.CONQUEST)));
        assertFalse(PowerCatalog.providesHeavyLift(mission(MissionType.EXTRACTION)));
    }

    @Test
    void theCarrierSurvivesTheLaunchCommitment() {
        MechSupport power = MechSupport.configured(List.of(
                MechDeploymentSpec.standard(MechVariant.HOUND)), LIGHT, true);

        CommandPower restored = CommandPowerCommitment.capture(power).toPower();

        assertEquals(LIGHT, ((MechSupport) restored).companyCarrier());
    }

    private static Mission mission(MissionType type) {
        return Mission.builder().id("lift-test").name("Lift").type(type)
                .source(MissionSource.GENERATED).risk(RiskLevel.LOW).build();
    }

    private static FittedBoat cradled(String id, BoatFitting cradle) {
        return new FittedBoat(ShuttleType.BUFFALO, BoatFitting.STANDARD_PLATING,
                BoatFitting.STANDARD_DRIVE, cradle, id);
    }
}
