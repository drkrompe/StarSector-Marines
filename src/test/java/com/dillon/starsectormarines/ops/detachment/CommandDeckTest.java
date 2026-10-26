package com.dillon.starsectormarines.ops.detachment;

import com.dillon.starsectormarines.battle.flyby.FlybyRoster;
import com.dillon.starsectormarines.battle.power.CloseAirSupport;
import com.dillon.starsectormarines.battle.power.CommandPower;
import com.dillon.starsectormarines.battle.power.MechSupport;
import com.dillon.starsectormarines.battle.power.OrbitalBarrage;
import com.dillon.starsectormarines.battle.power.SpawnInOnslaught;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CommandDeckTest {

    private static List<CommandPower> roster() {
        return List.of(new MechSupport(), new CloseAirSupport(), new OrbitalBarrage(),
                new SpawnInOnslaught());
    }

    @Test
    public void defaultFillIsStableAndUsesWholeBudget() {
        List<CommandPower> available = roster();
        Set<String> selected = CommandDeck.defaultSelection(available);

        assertEquals(Set.of(OrbitalBarrage.ID, SpawnInOnslaught.ID), selected);
        assertEquals(4, CommandDeck.used(available, selected));
        assertFalse(CommandDeck.canAdd(available, selected, new OrbitalBarrage()));
    }

    @Test
    public void launchFilterPreservesCatalogOrderAndRejectsOverBudgetTail() {
        Detachment available = new Detachment(List.of(), FlybyRoster.EMPTY, roster());
        Detachment filtered = CommandDeck.apply(available,
                List.of(OrbitalBarrage.ID, SpawnInOnslaught.ID));

        assertEquals(List.of(MechSupport.ID, CloseAirSupport.ID, OrbitalBarrage.ID, SpawnInOnslaught.ID),
                filtered.powers.stream().map(p -> p.id).toList());
        assertEquals(4, CommandDeck.used(filtered.powers,
                filtered.powers.stream().map(p -> p.id).toList()));
    }

    @Test
    public void theArmorDropAndCloseAirSupportRideOffTheDeck() {
        for (CommandPower implied : List.of(new MechSupport(), new CloseAirSupport())) {
            assertEquals(0, CommandDeck.weight(implied));
            assertFalse(CommandDeck.canAdd(roster(), Set.of(), implied));
        }
        assertEquals(List.of(MechSupport.ID, CloseAirSupport.ID),
                CommandDeck.filter(roster(), Set.of()).stream().map(p -> p.id).toList());
    }

    @Test
    public void explicitEmptyDeckKeepsOnlyTheImpliedDrop() {
        assertTrue(CommandDeck.filter(roster(), Set.of()).stream().allMatch(CommandDeck::implied));
    }
}
