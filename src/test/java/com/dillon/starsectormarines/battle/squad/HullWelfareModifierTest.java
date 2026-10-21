package com.dillon.starsectormarines.battle.squad;

import com.dillon.starsectormarines.battle.modifier.UnitModifiers;
import com.dillon.starsectormarines.battle.modifier.UnitStat;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** The founding hull's range and gym reach a campaign squad's marines as one named modifier. */
class HullWelfareModifierTest {

    @Test
    void aTrainedSquadsMarinesCarryTheRoomsFiguresAndNobodyElseDoes() {
        NavigationGrid grid = new NavigationGrid(10, 10);
        BattleSimulation sim = new BattleSimulation(grid, new CellTopology(10, 10));
        int trained = sim.mintSquad(Faction.MARINE, UnitType.MARINE);
        int plain = sim.mintSquad(Faction.MARINE, UnitType.MARINE);
        long a = sim.spawn(new EntitySpec("a", Faction.MARINE, UnitType.MARINE, 2, 2).squad(trained));
        long b = sim.spawn(new EntitySpec("b", Faction.MARINE, UnitType.MARINE, 4, 2).squad(plain));
        sim.getSquad(trained).hullWelfare = new HullWelfare(1.15f, 1.06f, 1.04f, 0.9f);

        UnitModifiers modifiers = sim.getRoster().modifiers();
        assertEquals(List.of(new UnitModifiers.Applied("unitModifierHullWelfare", 0f, 1.06f)),
                modifiers.explain(a, UnitStat.ACCURACY));
        assertEquals(1.04f, modifiers.resolve(a, UnitStat.MOVE_SPEED, 1f), 1e-6f);
        assertEquals(0.9f, modifiers.resolve(a, UnitStat.MORALE_DRAIN, 1f), 1e-6f);
        assertEquals(1f, modifiers.resolve(a, UnitStat.INCOMING_ACCURACY, 1f), 1e-6f);
        assertEquals(List.of(), modifiers.explain(b, UnitStat.ACCURACY));
    }

    @Test
    void aMessOnlyWelfareTouchesNoUnitStat() {
        assertFalse(HullWelfare.NEUTRAL.changesUnitStats());
        assertFalse(HullWelfare.mess(1.25f).changesUnitStats());
        assertTrue(new HullWelfare(1f, 1f, 1.02f, 1f).changesUnitStats());
        assertThrows(IllegalArgumentException.class, () -> new HullWelfare(1f, 0f, 1f, 1f));
    }

    @Test
    void theTagFreezesTheWholeWelfareAndKeepsTheMessFigure() {
        var welfare = new HullWelfare(1.25f, 1.03f, 1.02f, 1f);
        var tag = new CampaignSquadTag("cs-1", "Squad 01", true, 12, 0, null, welfare);
        assertSame(welfare, tag.welfare);
        assertEquals(1.25f, tag.moraleRecoveryMultiplier);
        assertEquals(HullWelfare.NEUTRAL, new CampaignSquadTag("cs-2", "Squad 02", true, 12).welfare);
    }
}
