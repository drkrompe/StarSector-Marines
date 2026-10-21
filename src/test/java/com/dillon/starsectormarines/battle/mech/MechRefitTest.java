package com.dillon.starsectormarines.battle.mech;

import com.dillon.starsectormarines.battle.mech.components.MechLoadoutComponent;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** A refit rides the frozen deployment and lands on the live mech, never folded into the fit's own frame. */
class MechRefitTest {
    private static final MechRefit REFIT = new MechRefit(1.06f, 1.20f, 1.10f, 1.25f);

    @Test void theDeployedFrameIsTheFitWithPlateAndCoolantOver() {
        MechFrame fit = new MechFrame(100f, 2f, 50f, 6f);
        MechFrame deployed = REFIT.over(fit);
        assertEquals(110f, deployed.armorCapacity(), 1e-4f);
        assertEquals(7.2f, deployed.dissipationPerSecond(), 1e-4f);
        assertEquals(2f, deployed.moveSpeed());
        assertEquals(50f, deployed.fluxCapacity());
        assertSame(fit, MechRefit.NEUTRAL.over(fit));
    }

    @Test void aDeploymentFreezesTheRefitBesideItsUnchangedFrame() {
        MechDeploymentSpec stock = MechDeploymentSpec.standard(MechVariant.HOUND);
        assertEquals(MechRefit.NEUTRAL, stock.refit());
        MechDeploymentSpec refitted = stock.withRefit(REFIT);
        assertEquals(stock.frame(), refitted.frame(), "the fit's own frame is never rewritten");
        assertEquals(stock.frame().armorCapacity() * 1.10f, refitted.deployedFrame().armorCapacity(), 1e-3f);
        assertEquals(stock.variant(), refitted.variant());
    }

    @Test void theLoadoutTakesTheAimTraverseAndCoolant() {
        MechDeploymentSpec deployment = MechDeploymentSpec.standard(MechVariant.HOUND).withRefit(REFIT);
        MechLoadoutComponent loadout = deployment.applyTo(new MechLoadoutComponent(MechVariant.HOUND, deployment.role()));
        assertEquals(1.06f, loadout.fireControlAccuracy);
        assertEquals(MechLoadoutComponent.DEFAULT_TORSO_TURN_RATE_DEGREES * 1.25f, loadout.torsoTurnRateDegrees, 1e-3f);
        assertEquals(deployment.frame().dissipationPerSecond() * 1.20f, loadout.flux.dissipationPerSecond(), 1e-4f);

        MechLoadoutComponent plain = MechDeploymentSpec.standard(MechVariant.HOUND)
                .applyTo(new MechLoadoutComponent(MechVariant.HOUND, MechRole.ASSAULT));
        assertEquals(1f, plain.fireControlAccuracy);
        assertEquals(MechLoadoutComponent.DEFAULT_TORSO_TURN_RATE_DEGREES, plain.torsoTurnRateDegrees);
    }
}
