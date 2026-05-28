package com.dillon.starsectormarines.battle.ai.goap.goals;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.weapons.MechLoadoutState;
import com.dillon.starsectormarines.battle.weapons.MechRole;
import com.dillon.starsectormarines.battle.unit.Squad;
import com.dillon.starsectormarines.battle.unit.Unit;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.ai.goap.Predicate;
import com.dillon.starsectormarines.battle.ai.goap.WorldState;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Relevance gating for {@link OverwatchKillZoneGoal}. The action's
 * own behavior is covered by its slot-picker tests; what we check here is
 * the MISSION/SURVIVAL handshake — broken morale must drop the goal out of
 * the running so {@code SurviveContact} (SURVIVAL bucket) wins the replan.
 */
public class OverwatchKillZoneGoalTest {

    private static final int W = 12;
    private static final int H = 12;

    private static BattleSimulation openSim() {
        NavigationGrid grid = new NavigationGrid(W, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) grid.setWalkableFloor(x, y);
        }
        return new BattleSimulation(grid, new CellTopology(W, H));
    }

    private static Squad lrSupportSquadWithContact(BattleSimulation sim) {
        Unit mech = new Unit("lr0", Faction.DEFENDER, UnitType.HEAVY_MECH, 3, 3);
        mech.mech = MechLoadoutState.defaultLoadout(MechRole.LR_SUPPORT);
        int sid = sim.mintSquad(Faction.DEFENDER, mech);
        mech.squadId = sid;
        sim.addUnit(mech);
        Squad squad = sim.getSquad(sid);
        squad.aliveMembers = 1;
        squad.lastSeenEnemyX = 8;
        squad.lastSeenEnemyY = 8;
        return squad;
    }

    @Test
    public void relevancePositiveWithLrSupportAndKnownContact() {
        BattleSimulation sim = openSim();
        Squad squad = lrSupportSquadWithContact(sim);
        assertTrue(OverwatchKillZoneGoal.INSTANCE.relevance(WorldState.EMPTY, squad, sim) > 0f,
                "LR_SUPPORT + lastSeenEnemy set → overwatch is on the table");
    }

    @Test
    public void relevanceZeroWhenMoraleBroken() {
        // Mirror of ClearAssignedZoneGoalTest.relevanceZeroWhenMoraleBroken —
        // playtest dump squad_0 caught a defender mech squad pinned in
        // OverwatchKillZone at morale 0.37 with members at 8/90 HP, because
        // MISSION outranks SURVIVAL unconditionally. SurviveContact only wins
        // if every MISSION goal drops to zero relevance for broken squads.
        BattleSimulation sim = openSim();
        Squad squad = lrSupportSquadWithContact(sim);
        WorldState broken = WorldState.EMPTY.with(Predicate.MORALE_BROKEN, true);

        assertEquals(0f, OverwatchKillZoneGoal.INSTANCE.relevance(broken, squad, sim),
                "morale-broken LR_SUPPORT squad → overwatch yields, SurviveContact takes over");
    }
}
