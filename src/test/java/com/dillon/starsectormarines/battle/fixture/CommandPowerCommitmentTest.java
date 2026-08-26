package com.dillon.starsectormarines.battle.fixture;

import com.dillon.starsectormarines.battle.mech.MechDeploymentSpec;
import com.dillon.starsectormarines.battle.mech.MechRole;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.mech.MissileReplenisherComponent;
import com.dillon.starsectormarines.battle.power.CommandPower;
import com.dillon.starsectormarines.battle.power.EmergencyResupply;
import com.dillon.starsectormarines.battle.power.MarineInsertion;
import com.dillon.starsectormarines.battle.power.MechSupport;
import com.dillon.starsectormarines.battle.power.OrbitalBarrage;
import com.dillon.starsectormarines.battle.power.ReconPing;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;

class CommandPowerCommitmentTest {

    @Test
    void standardPowerRoundTripsThroughProductionCatalog() {
        List<CommandPower> originals = List.of(
                new ReconPing(),
                new EmergencyResupply(),
                new OrbitalBarrage(),
                new MarineInsertion());

        List<CommandPowerCommitment> commitments = originals.stream()
                .map(CommandPowerCommitment::capture)
                .toList();
        List<CommandPower> restored = commitments.stream()
                .map(CommandPowerCommitment::toPower)
                .toList();

        assertEquals(List.of(ReconPing.ID, EmergencyResupply.ID,
                        OrbitalBarrage.ID, MarineInsertion.ID),
                commitments.stream().map(CommandPowerCommitment::id).toList());
        assertEquals(List.of(), commitments.stream()
                .flatMap(commitment -> commitment.mechDeployments().stream()).toList());
        for (int i = 0; i < originals.size(); i++) {
            CommandPower original = originals.get(i);
            CommandPower copy = restored.get(i);
            assertEquals(original.getClass(), copy.getClass());
            assertNotSame(original, copy);
            assertEquals(original.id, copy.id);
            assertEquals(original.cpCost, copy.cpCost);
            assertEquals(original.cooldownSeconds, copy.cooldownSeconds);
        }
    }

    @Test
    void configuredMechSupportRoundTripsOrderedDeploymentValues() {
        List<MechDeploymentSpec> deployments = List.of(
                new MechDeploymentSpec(MechVariant.SIROCCO, MechRole.LR_SUPPORT,
                        MissileReplenisherComponent.ACCELERATED_FEED),
                new MechDeploymentSpec(MechVariant.HOUND, MechRole.ASSAULT,
                        MissileReplenisherComponent.STANDARD));
        MechSupport original = MechSupport.configured(deployments);

        CommandPowerCommitment commitment = CommandPowerCommitment.capture(original);
        MechSupport restored = assertInstanceOf(MechSupport.class, commitment.toPower());

        assertEquals(MechSupport.ID, commitment.id());
        assertEquals(deployments, commitment.mechDeployments());
        assertEquals(deployments, restored.deployments());
        assertNotSame(original, restored);
        assertEquals(original.maxCharges, restored.maxCharges);
    }
}
