package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.power.CommandPower;
import com.dillon.starsectormarines.battle.power.MechSupport;
import com.dillon.starsectormarines.battle.power.ReconPing;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

public class DebugMechRosterTest {

    @Test
    public void randomRosterIsStableAndClamped() {
        DebugMechRoster first = DebugMechRoster.randomized(3, 42L);
        DebugMechRoster second = DebugMechRoster.randomized(3, 42L);

        assertEquals(first.variants(), second.variants());
        assertEquals(DebugMechRoster.MAX_COUNT,
                DebugMechRoster.randomized(99, 42L).count());
        assertEquals(0, DebugMechRoster.randomized(-1, 42L).count());
    }

    @Test
    public void configuredRosterReplacesPowerInPlaceAndSetsChargeCount() {
        DebugMechRoster roster = DebugMechRoster.randomized(3, 7L);
        List<CommandPower> powers = roster.applyTo(
                List.of(new ReconPing(), new MechSupport()));

        assertEquals(2, powers.size());
        assertInstanceOf(ReconPing.class, powers.get(0));
        assertInstanceOf(MechSupport.class, powers.get(1));
        assertEquals(3, powers.get(1).maxCharges);
    }

    @Test
    public void zeroRosterRemovesDebugMechSupport() {
        List<CommandPower> powers = DebugMechRoster.randomized(0, 1L)
                .applyTo(List.of(new ReconPing(),
                        new MechSupport(List.of(MechVariant.HOUND))));

        assertEquals(List.of(ReconPing.ID), powers.stream().map(p -> p.id).toList());
    }
}
