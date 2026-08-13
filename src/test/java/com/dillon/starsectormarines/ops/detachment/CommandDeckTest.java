package com.dillon.starsectormarines.ops.detachment;

import com.dillon.starsectormarines.battle.flyby.FlybyRoster;
import com.dillon.starsectormarines.battle.power.CommandPower;
import com.dillon.starsectormarines.battle.power.EmergencyResupply;
import com.dillon.starsectormarines.battle.power.MarineInsertion;
import com.dillon.starsectormarines.battle.power.MechSupport;
import com.dillon.starsectormarines.battle.power.OrbitalBarrage;
import com.dillon.starsectormarines.battle.power.ReconPing;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CommandDeckTest {

    private static List<CommandPower> roster() {
        return List.of(new ReconPing(), new MechSupport(), new EmergencyResupply(),
                new OrbitalBarrage(), new MarineInsertion());
    }

    @Test
    public void defaultFillIsStableAndUsesWholeBudget() {
        List<CommandPower> available = roster();
        Set<String> selected = CommandDeck.defaultSelection(available);

        assertEquals(Set.of(ReconPing.ID, MechSupport.ID, EmergencyResupply.ID), selected);
        assertEquals(CommandDeck.BUDGET, CommandDeck.used(available, selected));
        assertFalse(CommandDeck.canAdd(available, selected, new MarineInsertion()));
    }

    @Test
    public void launchFilterPreservesCatalogOrderAndRejectsOverBudgetTail() {
        Detachment available = new Detachment(List.of(), FlybyRoster.EMPTY, roster());
        Detachment filtered = CommandDeck.apply(available,
                List.of(OrbitalBarrage.ID, MarineInsertion.ID, MechSupport.ID, ReconPing.ID));

        assertEquals(List.of(ReconPing.ID, MechSupport.ID),
                filtered.powers.stream().map(p -> p.id).toList());
        assertEquals(4, CommandDeck.used(filtered.powers,
                filtered.powers.stream().map(p -> p.id).toList()));
    }

    @Test
    public void explicitEmptyDeckRemainsEmpty() {
        assertTrue(CommandDeck.filter(roster(), Set.of()).isEmpty());
    }
}
