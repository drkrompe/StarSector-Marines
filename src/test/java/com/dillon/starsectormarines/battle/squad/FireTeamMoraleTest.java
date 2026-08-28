package com.dillon.starsectormarines.battle.squad;

import com.dillon.starsectormarines.battle.infantry.GoapInfantryBehavior;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The multi-team half of the morale model: which team a hit charges, when the
 * squad as a whole counts as broken, and what a broken team stops doing while
 * its siblings fight on. The single-team drain, recovery, and hysteresis
 * arithmetic is covered by {@code SquadMoraleTest}.
 */
public class FireTeamMoraleTest {

    private static final int W = 20;
    private static final int H = 12;

    /**
     * Open floor with a wall at column 15, splitting a marine side (left)
     * from a hideout (right) — the same shape {@code SquadMoraleTest} uses,
     * for the same reason: a case that wants several ticks without contact
     * still needs a live enemy somewhere or the win check completes the
     * battle and later {@code advance} calls do nothing.
     */
    private static BattleSimulation openSim() {
        NavigationGrid grid = new NavigationGrid(W, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) grid.setWalkableFloor(x, y);
        }
        for (int y = 0; y < H; y++) grid.setWalkable(15, y, false);
        return new BattleSimulation(grid, new CellTopology(W, H));
    }

    /** Park a defender behind the wall — never seen, never fought, just alive. */
    private static void hideDefender(BattleSimulation sim) {
        sim.spawn(new EntitySpec("d-hidden", Faction.DEFENDER, UnitType.MARINE, 18, 6));
    }

    /** Four marines in two billeted teams of two, plus their squad. */
    private static Squad twoTeamSquad(BattleSimulation sim) {
        int squadId = sim.mintSquad(Faction.MARINE, UnitType.MARINE);
        for (int i = 0; i < 4; i++) {
            sim.spawn(new EntitySpec("m" + i, Faction.MARINE, UnitType.MARINE, 1 + i, 1)
                    .squad(squadId).fireTeam(i / 2));
        }
        Squad squad = sim.getSquad(squadId);
        squad.originalSize = 4;
        // The morale tick's census establishes this on the squad's first tick;
        // the drain cases below shoot before ever ticking, so seed it here.
        for (int team = 0; team < 2; team++) {
            FireTeamMorale morale = squad.fireTeamMorale(team);
            morale.aliveMembers = 2;
            morale.originalSize = 2;
        }
        return squad;
    }

    /** Marine {@code index} of {@link #twoTeamSquad}, in spawn order. */
    private static long marine(BattleSimulation sim, int index) {
        return sim.liveUnitAt(index);
    }

    /**
     * Drives {@code team} to a morale of zero and holds it there for the tick
     * about to run: zero is far below the broken threshold, and a fresh
     * under-fire timer keeps recovery from lifting it back out before the
     * hysteresis is read.
     */
    private static void breakTeam(Squad squad, int teamIndex) {
        FireTeamMorale team = squad.fireTeamMorale(teamIndex);
        team.morale = 0f;
        team.timeSinceUnderFire = 0f;
    }

    @Test
    public void hitChargesOnlyTheHitMarinesOwnTeam() {
        BattleSimulation sim = openSim();
        Squad squad = twoTeamSquad(sim);
        long target = marine(sim, 0);

        sim.applyDamage(target, 1f, 1f);
        assertTrue(sim.world().isAlive(target), "test prerequisite: target survived the hit");

        assertEquals(1.0f - SquadMoraleSystem.MORALE_DROP_ON_HIT,
                squad.fireTeamMorale(0).morale, 1e-5f,
                "the hit marine's own team takes the drain");
        assertEquals(1.0f, squad.fireTeamMorale(1).morale, 1e-5f,
                "a sibling team is not rattled by a round that found someone else");
    }

    @Test
    public void deathChargesOnlyTheDeadMarinesOwnTeam() {
        BattleSimulation sim = openSim();
        Squad squad = twoTeamSquad(sim);
        long target = marine(sim, 3);

        sim.applyDamage(target, sim.world().hp(target) + 1000f, 1f);
        assertFalse(sim.world().isAlive(target), "test prerequisite: target died");

        assertEquals(1.0f, squad.fireTeamMorale(0).morale, 1e-5f,
                "losing a marine in the other team leaves this one composed");
        assertEquals(1.0f - SquadMoraleSystem.MORALE_DROP_ON_HIT
                        - SquadMoraleSystem.MORALE_DROP_ON_DEATH,
                squad.fireTeamMorale(1).morale, 1e-5f,
                "the dead marine's team takes hit drain plus death drain");
    }

    @Test
    public void drainCooldownIsPerTeamNotPerSquad() {
        BattleSimulation sim = openSim();
        Squad squad = twoTeamSquad(sim);

        // Two hits in the same tick, one into each team. Under a squad-wide
        // cooldown the second would have been swallowed by the window the
        // first armed; each team owns its own gate now.
        sim.applyDamage(marine(sim, 0), 1f, 1f);
        sim.applyDamage(marine(sim, 2), 1f, 1f);

        assertEquals(1.0f - SquadMoraleSystem.MORALE_DROP_ON_HIT,
                squad.fireTeamMorale(0).morale, 1e-5f, "first team drained");
        assertEquals(1.0f - SquadMoraleSystem.MORALE_DROP_ON_HIT,
                squad.fireTeamMorale(1).morale, 1e-5f,
                "second team drains in the same tick — the cooldown is its own");
    }

    @Test
    public void squadReadsBrokenOnlyWhenEveryTeamHasBroken() {
        BattleSimulation sim = openSim();
        Squad squad = twoTeamSquad(sim);
        hideDefender(sim);
        breakTeam(squad, 0);

        sim.advance(BattleSimulation.TICK_DT);

        assertTrue(squad.fireTeamMorale(0).broken, "the drained team breaks");
        assertFalse(squad.fireTeamMorale(1).broken, "its sibling is untouched");
        assertFalse(squad.moraleBroken,
                "a squad with one team in cover is not a broken squad");

        breakTeam(squad, 1);
        sim.advance(BattleSimulation.TICK_DT);

        assertTrue(squad.moraleBroken,
                "with no composed team left, the squad as a whole reads broken");
    }

    @Test
    public void brokenTeamIsLeftOutOfTheSquadPlan() {
        BattleSimulation sim = openSim();
        Squad squad = twoTeamSquad(sim);
        sim.spawn(new EntitySpec("d", Faction.DEFENDER, UnitType.MARINE, 9, 1));
        breakTeam(squad, 0);

        sim.advance(BattleSimulation.TICK_DT);

        SquadPlan plan = squad.currentPlan;
        assertNotNull(plan, "test prerequisite: a visible enemy gives the squad a plan");
        SquadPlan.Step step = plan.currentStep();
        assertNotNull(step, "test prerequisite: the plan has a live step");
        assertNull(step.slotOf(marine(sim, 0)),
                "a peeling marine must not hold a slot in the plan he is walking away from");
        assertNull(step.slotOf(marine(sim, 1)), "nor his teammate");
        assertNotNull(step.slotOf(marine(sim, 2)),
                "the composed team keeps executing the squad's plan");
        assertNotNull(step.slotOf(marine(sim, 3)), "both of them");
    }

    @Test
    public void brokenTeamPeelsToCoverWhileSiblingsFightOn() {
        BattleSimulation sim = openSim();
        Squad squad = twoTeamSquad(sim);
        sim.spawn(new EntitySpec("d", Faction.DEFENDER, UnitType.MARINE, 9, 1));
        // Sentinel: BreakContact is the only thing in an infantry marine's
        // path that authors a fall-back cell, so an unset one after the tick
        // means that marine never peeled.
        for (int i = 0; i < 4; i++) sim.world().setFallbackCell(marine(sim, i), -1, -1);
        breakTeam(squad, 0);

        sim.advance(BattleSimulation.TICK_DT);

        assertTrue(sim.world().fallbackCellX(marine(sim, 0)) >= 0,
                "a marine whose team broke pulls back to cover on his team's own account");
        assertTrue(sim.world().fallbackCellX(marine(sim, 1)) >= 0,
                "his teammate goes with him — the team is what peeled");
        assertEquals(-1, sim.world().fallbackCellX(marine(sim, 2)),
                "the composed team stays in the fight");
        assertEquals(-1, sim.world().fallbackCellX(marine(sim, 3)),
                "both of them");
    }

    @Test
    public void teamRejoinsThePlanOnceItsMoraleClears() {
        BattleSimulation sim = openSim();
        Squad squad = twoTeamSquad(sim);
        sim.spawn(new EntitySpec("d", Faction.DEFENDER, UnitType.MARINE, 9, 1));
        breakTeam(squad, 0);
        sim.advance(BattleSimulation.TICK_DT);
        assertTrue(squad.fireTeamMorale(0).broken, "test prerequisite: the team broke");

        // Lift it clear of the hysteresis outright — recovery timing is
        // SquadMoraleTest's subject; what matters here is that clearing the
        // flag puts the team back in the slot pool.
        squad.fireTeamMorale(0).morale = 1.0f;
        squad.fireTeamMorale(0).timeSinceUnderFire = 10f;
        sim.advance(BattleSimulation.TICK_DT);

        assertFalse(squad.fireTeamMorale(0).broken, "morale past the clear threshold");
        SquadPlan.Step step = squad.currentPlan != null
                ? squad.currentPlan.currentStep() : null;
        assertNotNull(step, "the squad still has a live plan");
        assertNotNull(step.slotOf(marine(sim, 0)),
                "a recomposed team is assigned work again");
    }

    @Test
    public void anyTeamFlipPublishesTheReplanInterrupt() {
        BattleSimulation sim = openSim();
        Squad squad = twoTeamSquad(sim);
        hideDefender(sim);
        breakTeam(squad, 0);

        sim.advance(BattleSimulation.TICK_DT);

        assertTrue(squad._moraleBrokenChangedThisTick,
                "one team flipping is what forces the squad to replan around it, "
                        + "even though the squad-level flag never moved");
        assertFalse(squad.moraleBroken, "test prerequisite: the squad flag did not move");
    }

    @Test
    public void unbrokenTeamLookupNeverCreatesState() {
        // The per-unit dispatch reads this concurrently; a computeIfAbsent
        // hiding in the read path would be a data race on the squad's map.
        Squad squad = new Squad(1, Faction.MARINE);
        assertFalse(squad.fireTeamBroken(0), "an untouched team is not broken");
        assertTrue(squad.fireTeamMorale().isEmpty(),
                "reading a team's broken flag must not mint state for it");
    }

    @Test
    public void goalListStillCarriesTheWholeSquadTail() {
        // SurviveContact is no longer the ordinary break path, but it is still
        // what releases a squad's mission goal once every team has broken.
        assertTrue(GoapInfantryBehavior.INFANTRY_GOALS.stream()
                        .anyMatch(g -> "SurviveContact".equals(g.name())),
                "the all-teams-broken tail goal stays in the library");
    }
}
