package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.air.FittedBoat;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.fixture.CommandPowerCommitment;
import com.dillon.starsectormarines.battle.power.CommandPower;
import com.dillon.starsectormarines.battle.power.MechSupport;
import com.dillon.starsectormarines.battle.power.OrbitalBarrage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DebugMechRosterTest {

    @Test
    public void randomRosterIsStableAndHasNoAuthoredCeiling() {
        DebugMechRoster first = DebugMechRoster.randomized(101, 42L);
        DebugMechRoster second = DebugMechRoster.randomized(101, 42L);

        assertEquals(first.variants(), second.variants());
        assertEquals(101, first.count());
        MechSupport support = assertInstanceOf(MechSupport.class,
                first.applyTo(List.of(), List.of(), true).get(0));
        assertEquals(101, support.availableCalls());
        assertEquals(101, support.deployments().size());
        MechSupport restored = assertInstanceOf(MechSupport.class,
                CommandPowerCommitment.capture(support).toPower());
        assertEquals(101, restored.availableCalls());
        assertEquals(support.deployments(), restored.deployments());
        assertEquals(0, DebugMechRoster.randomized(-1, 42L).count());
    }

    @Test
    public void configuredRosterReplacesPowerInPlaceWithEveryMechAboard() {
        DebugMechRoster roster = DebugMechRoster.randomized(9, 7L);
        List<CommandPower> powers = roster.applyTo(
                List.of(new OrbitalBarrage(), new MechSupport()), List.of(), true);

        assertEquals(2, powers.size());
        assertInstanceOf(OrbitalBarrage.class, powers.get(0));
        assertInstanceOf(MechSupport.class, powers.get(1));
        assertEquals(9, powers.get(1).availableCalls());
    }

    @Test
    public void zeroRosterRemovesDebugMechSupport() {
        List<CommandPower> powers = DebugMechRoster.randomized(0, 1L)
                .applyTo(List.of(new OrbitalBarrage(),
                        new MechSupport(List.of(MechVariant.HOUND))), List.of(), true);

        assertEquals(List.of(OrbitalBarrage.ID), powers.stream().map(p -> p.id).toList());
    }

    @Test
    public void theBayFliesTheCompanyBoatWithHeavyLiftOnlyWhereTheMissionProvidesIt() {
        FittedBoat hermes = DebugMissionCatalog.maxFitted(ShuttleType.HERMES);
        DebugMechRoster roster = DebugMechRoster.randomized(2, 3L);

        MechSupport own = assertInstanceOf(MechSupport.class,
                roster.applyTo(List.of(), List.of(hermes), false).get(0));
        MechSupport arranged = assertInstanceOf(MechSupport.class,
                roster.applyTo(List.of(), List.of(hermes), true).get(0));

        assertEquals(hermes, own.companyCarrier());
        assertFalse(own.syntheticLift());
        assertTrue(arranged.syntheticLift());
        MechSupport stranded = assertInstanceOf(MechSupport.class,
                roster.applyTo(List.of(), List.of(), false).get(0));
        assertTrue(stranded.budget().carriers().isEmpty(), "no boat, no lift: the bay stays aboard");
    }
}
