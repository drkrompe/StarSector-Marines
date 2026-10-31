package com.dillon.starsectormarines.battle.squad;

import com.dillon.starsectormarines.battle.command.CommandGroupIdentity;
import com.dillon.starsectormarines.battle.modifier.UnitModifiers;
import com.dillon.starsectormarines.battle.modifier.UnitStat;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.marine.OfficerCommandProfile;
import com.dillon.starsectormarines.marine.Rank;
import com.dillon.starsectormarines.marine.Status;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** An officer's Tactical Cover reaches his squad's marines in battle, named apart from their armour. */
class TacticalCoverModifierTest {

    @Test
    void anOfficersCoverShowsOnHisMarinesAsItsOwnModifier() {
        NavigationGrid grid = new NavigationGrid(10, 10);
        BattleSimulation sim = new BattleSimulation(grid, new CellTopology(10, 10));
        int covered = sim.mintSquad(Faction.MARINE, UnitType.MARINE);
        int plain = sim.mintSquad(Faction.MARINE, UnitType.MARINE);
        long a = sim.spawn(new EntitySpec("a", Faction.MARINE, UnitType.MARINE, 2, 2).squad(covered));
        long b = sim.spawn(new EntitySpec("b", Faction.MARINE, UnitType.MARINE, 4, 2).squad(plain));
        OfficerCommandProfile officer = new OfficerCommandProfile("o1", "Vance", Rank.values()[0], 0,
                Status.values()[0], false, 2, 0);
        sim.getSquad(covered).commandGroupIdentity = new CommandGroupIdentity("g1", "o1", "Vance", true, officer);

        float armour = sim.getRoster().world().incomingAccuracyMult(b);
        assertEquals(armour * officer.incomingAccuracyMultiplier(),
                sim.getRoster().world().incomingAccuracyMult(a), 1e-6f);

        UnitModifiers modifiers = sim.getRoster().modifiers();
        assertEquals(List.of(new UnitModifiers.Applied("openingOfficerSpecialtyTacticalCover", 0f,
                        officer.incomingAccuracyMultiplier())),
                modifiers.explain(a, UnitStat.INCOMING_ACCURACY));
        assertEquals(List.of(), modifiers.explain(b, UnitStat.INCOMING_ACCURACY));
    }
}
