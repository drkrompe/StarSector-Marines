package com.dillon.starsectormarines.battle.mech;

import com.dillon.starsectormarines.battle.mech.components.MechLoadoutComponent;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.Entity;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.decision.goap.Predicate;
import com.dillon.starsectormarines.battle.decision.goap.WorldState;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Relevance gating for {@link BackstopAssignedSquadGoal}. Sister coverage to
 * {@link OverwatchKillZoneGoalTest} — the morale gate is the same pattern,
 * carved out for the same reason (role hint, not unit-level objective).
 */
public class BackstopAssignedSquadGoalTest {

    private static final int W = 12;
    private static final int H = 12;

    private static BattleSimulation openSim() {
        NavigationGrid grid = new NavigationGrid(W, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) grid.setWalkableFloor(x, y);
        }
        return new BattleSimulation(grid, new CellTopology(W, H));
    }

    private static Squad armoredSquadWithFriendlyInfantry(BattleSimulation sim) {
        Entity mech = new Entity("ar0", Faction.MARINE, UnitType.HEAVY_MECH, 3, 3);
        int mechSid = sim.mintSquad(Faction.MARINE, mech);
        mech.squadId = mechSid;
        sim.addUnit(mech);
        sim.world().attachMechLoadout(mech.entityId, MechLoadoutComponent.defaultLoadout(MechRole.ARMORED_SUPPORT));
        Squad mechSquad = sim.getSquad(mechSid);
        mechSquad.aliveMembers = 1;

        Entity grunt = new Entity("m0", Faction.MARINE, UnitType.MARINE, 5, 5);
        int infSid = sim.mintSquad(Faction.MARINE, grunt);
        grunt.squadId = infSid;
        sim.addUnit(grunt);
        Squad infSquad = sim.getSquad(infSid);
        infSquad.aliveMembers = 1;

        return mechSquad;
    }

    @Test
    public void relevancePositiveWithArmoredAndFriendlyInfantry() {
        BattleSimulation sim = openSim();
        Squad squad = armoredSquadWithFriendlyInfantry(sim);
        assertTrue(BackstopAssignedSquadGoal.INSTANCE.relevance(WorldState.EMPTY, squad, sim) > 0f,
                "ARMORED_SUPPORT + friendly non-mech squad → backstop is on the table");
    }

    @Test
    public void relevanceZeroWhenMoraleBroken() {
        // Same shape as the OverwatchKillZone gate — see that test's comment
        // for the playtest provenance. A backstop squad whose morale has
        // broken must yield so SurviveContact's BreakContact can pull them
        // out of the line.
        BattleSimulation sim = openSim();
        Squad squad = armoredSquadWithFriendlyInfantry(sim);
        WorldState broken = WorldState.EMPTY.with(Predicate.MORALE_BROKEN, true);

        assertEquals(0f, BackstopAssignedSquadGoal.INSTANCE.relevance(broken, squad, sim),
                "morale-broken backstop squad → role hint yields, SurviveContact takes over");
    }
}
