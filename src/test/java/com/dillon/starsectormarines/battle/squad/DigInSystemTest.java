package com.dillon.starsectormarines.battle.squad;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.model.FightingPositionIndex;
import com.dillon.starsectormarines.battle.world.tiles.DoodadCover;
import com.dillon.starsectormarines.battle.world.tiles.DoodadDef;
import com.dillon.starsectormarines.battle.world.tiles.FightingPositionRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The dig-in rule and the morale scaling it buys. Fighting positions come from a hand-built
 * trench; the marine is a spawned body moved by hand and the system is ticked directly, so no
 * battle is played.
 */
class DigInSystemTest {

    private static final int W = 30;
    private static final int H = 10;
    private static final float DT = 1f / 30f;
    private static final int TICKS_TO_DIG = Math.round(DigInSystem.DIG_IN_SECONDS / DT) + 1;

    private boolean previouslyEnabled;
    private BattleSimulation sim;
    private DigInSystem system;
    private FightingPositionIndex index;
    private long marine;

    private static FightingPositionIndex trench(int fromX, int toX, int y) {
        DoodadDef def = new DoodadDef("doodad.test.trench", "sheet", 0, 0, DoodadCover.HEAVY, 0.3f,
                1, 1, null, 0, null, FightingPositionRole.TRENCH);
        List<Doodad> props = new ArrayList<>();
        for (int x = fromX; x <= toX; x++) props.add(new Doodad(x, y, def));
        return FightingPositionIndex.build(W, H, props, (x, y2) -> true);
    }

    @BeforeEach
    void setUp() {
        previouslyEnabled = DigInSystem.isEnabled();
        DigInSystem.setEnabledForEvidence(true);
        NavigationGrid grid = new NavigationGrid(W, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) grid.setWalkableFloor(x, y);
        }
        sim = new BattleSimulation(grid, new CellTopology(W, H));
        index = trench(5, 16, 4);
        system = new DigInSystem(sim.getRoster(), () -> index);
        int squadId = sim.mintSquad(Faction.MARINE, UnitType.MARINE);
        marine = sim.spawn(new EntitySpec("m0", Faction.MARINE, UnitType.MARINE, 8, 4).squad(squadId));
    }

    @AfterEach
    void tearDown() {
        DigInSystem.setEnabledForEvidence(previouslyEnabled);
    }

    private void tick(int n) {
        for (int i = 0; i < n; i++) system.tick(DT);
    }

    private void stand(int cellX, int cellY) {
        sim.world().setPos(marine, cellX + 0.5f, cellY + 0.5f);
    }

    @Test
    void digsInOnlyAfterTheFullDelay() {
        stand(8, 4);
        tick(TICKS_TO_DIG - 3);
        assertFalse(sim.isDugIn(marine), "not before the delay");
        tick(3);
        assertTrue(sim.isDugIn(marine));
    }

    @Test
    void leavingTheCellEndsItAtOnce() {
        stand(8, 4);
        tick(TICKS_TO_DIG);
        assertTrue(sim.isDugIn(marine));
        stand(8, 7);
        tick(1);
        assertFalse(sim.isDugIn(marine));
        stand(8, 4);
        tick(TICKS_TO_DIG - 3);
        assertFalse(sim.isDugIn(marine), "the clock restarts from zero");
    }

    @Test
    void aCellOffThePositionNeverCounts() {
        stand(8, 7);
        tick(TICKS_TO_DIG * 2);
        assertFalse(sim.isDugIn(marine));
    }

    @Test
    void theCellLeavingTheIndexEndsIt() {
        stand(8, 4);
        tick(TICKS_TO_DIG);
        assertTrue(sim.isDugIn(marine));
        index = FightingPositionIndex.empty(W, H);
        tick(1);
        assertFalse(sim.isDugIn(marine));
    }

    @Test
    void aMarineStillMovingDoesNotDigIn() {
        stand(8, 4);
        sim.setPath(marine, new int[]{9, 4, 10, 4});
        tick(TICKS_TO_DIG * 2);
        assertFalse(sim.isDugIn(marine));
    }

    @Test
    void aMechSquadMemberNeverDigsIn() {
        int mechSquad = sim.mintSquad(Faction.MARINE, UnitType.HEAVY_MECH);
        long mech = sim.spawn(new EntitySpec("mech", Faction.MARINE, UnitType.HEAVY_MECH, 9, 4).squad(mechSquad));
        tick(TICKS_TO_DIG * 2);
        assertFalse(sim.isDugIn(mech));
    }

    @Test
    void anUnsquaddedBodyNeverDigsIn() {
        long loner = sim.spawn(new EntitySpec("loner", Faction.MARINE, UnitType.MARINE, 10, 4));
        tick(TICKS_TO_DIG * 2);
        assertFalse(sim.isDugIn(loner));
    }

    @Test
    void theToggleOffMeansNobodyIsEverDugIn() {
        DigInSystem.setEnabledForEvidence(false);
        stand(8, 4);
        tick(TICKS_TO_DIG * 2);
        assertFalse(sim.isDugIn(marine));
    }

    @Test
    void clockHelpersClampAndReset() {
        assertEquals(DigInSystem.DIG_IN_SECONDS, DigInSystem.advance(DigInSystem.DIG_IN_SECONDS, true, 1f));
        assertEquals(0f, DigInSystem.advance(1.5f, false, DT));
        assertTrue(DigInSystem.isDugIn(DigInSystem.DIG_IN_SECONDS));
        assertFalse(DigInSystem.isDugIn(DigInSystem.DIG_IN_SECONDS - 0.01f));
    }

    @Test
    void hitDrainIsHalvedForADugInMarine() {
        float loose = SquadMoraleSystem.hitDrain(1f, 1f, false);
        assertEquals(SquadMoraleSystem.MORALE_DROP_ON_HIT, loose, 1e-6f);
        assertEquals(loose * DigInSystem.DUG_IN_MORALE_DRAIN_MULT, SquadMoraleSystem.hitDrain(1f, 1f, true), 1e-6f);
        assertEquals(0.5f * SquadMoraleSystem.MORALE_DROP_ON_HIT / 0.25f,
                SquadMoraleSystem.hitDrain(0.25f, 1f, true), 1e-6f, "cap scaling still applies");
    }

    @Test
    void nearMissDrainIsHalvedForADugInMarine() {
        float loose = SquadMoraleSystem.nearMissDrain(1f, 1f, false);
        assertEquals(SquadMoraleSystem.MORALE_DROP_ON_NEAR_MISS, loose, 1e-6f);
        assertEquals(loose * 0.5f, SquadMoraleSystem.nearMissDrain(1f, 1f, true), 1e-6f);
    }

    @Test
    void aHitOnADugInMarineChargesHalfAndADeathStillChargesFull() {
        stand(8, 4);
        tick(TICKS_TO_DIG);
        Squad squad = sim.getSquad(sim.getRoster().squad().squadId(marine));
        FireTeamMorale team = squad.fireTeamMorale(0);
        team.aliveMembers = 1;
        team.originalSize = 1;

        sim.applyDamage(marine, 1f, 1f);
        assertEquals(1f - SquadMoraleSystem.MORALE_DROP_ON_HIT * DigInSystem.DUG_IN_MORALE_DRAIN_MULT,
                team.morale, 1e-5f);

        team.morale = 1f;
        team.drainCooldown = 0f;
        sim.applyDamage(marine, sim.world().hp(marine) + 1000f, 1f);
        assertEquals(1f - SquadMoraleSystem.MORALE_DROP_ON_HIT * DigInSystem.DUG_IN_MORALE_DRAIN_MULT
                        - SquadMoraleSystem.MORALE_DROP_ON_DEATH,
                team.morale, 1e-5f, "death drain is not scaled");
    }

    @Test
    void aHitOnAMarineNotDugInChargesTheFullDrain() {
        Squad squad = sim.getSquad(sim.getRoster().squad().squadId(marine));
        FireTeamMorale team = squad.fireTeamMorale(0);
        team.aliveMembers = 1;
        team.originalSize = 1;
        sim.applyDamage(marine, 1f, 1f);
        assertEquals(1f - SquadMoraleSystem.MORALE_DROP_ON_HIT, team.morale, 1e-5f);
    }
}
