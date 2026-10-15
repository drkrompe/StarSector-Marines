package com.dillon.starsectormarines.battle.combat;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.model.DoodadService;
import com.dillon.starsectormarines.battle.world.tiles.DoodadCover;
import com.dillon.starsectormarines.battle.world.tiles.DoodadDef;
import com.dillon.starsectormarines.battle.world.tiles.FightingPositionRole;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A trench covers the man standing in it from every direction alike, more the further off the
 * shooter is, and never stops a round passing over it.
 */
class TrenchOccupantCoverTest {

    private static final int SIZE = 48;
    /** The trench runs east-west along this row; the target stands in it at {@link #TX}. */
    private static final int ROW = 24;
    private static final int TX = 24;
    private static final float VEL = 30f;
    private static final float MAX_RANGE = 40f;
    private static final int SHOTS = 4000;
    private static final DoodadDef TRENCH = new DoodadDef("doodad.test.trench", "sheet", 0, 0,
            DoodadCover.HEAVY, 0.3f, 1, 1, null, 0, null, FightingPositionRole.TRENCH);

    private static BattleSimulation arena(boolean trench) {
        NavigationGrid grid = new NavigationGrid(SIZE, SIZE);
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) grid.setWalkableFloor(x, y);
        }
        BattleSimulation sim = new BattleSimulation(grid, new CellTopology(SIZE, SIZE));
        if (trench) {
            for (int x = 4; x < SIZE - 4; x++) sim.addDoodad(new Doodad(x, ROW, TRENCH));
        }
        return sim;
    }

    private static DoodadService trenchDoodads(NavigationGrid grid) {
        DoodadService doodads = new DoodadService(grid);
        for (int x = 4; x < SIZE - 4; x++) doodads.addDoodad(new Doodad(x, ROW, TRENCH));
        return doodads;
    }

    /** Share of perfectly aimed shots from (sx, sy) that the trench stops short of the target. */
    private static float blockedShare(int sx, int sy) {
        BattleSimulation sim = arena(false);
        NavigationGrid grid = sim.getGrid();
        DoodadService doodads = trenchDoodads(grid);
        long target = sim.spawn(new EntitySpec("in-trench", Faction.DEFENDER, UnitType.MARINE, TX, ROW));
        long shooter = sim.spawn(new EntitySpec("shooter", Faction.MARINE, UnitType.MARINE, sx, sy));
        BallisticResolver resolver = new BallisticResolver(grid, doodads, sim.getUnitIndex(), sim.getRoster());
        Random rng = new Random(7L);
        int blocked = 0;
        for (int i = 0; i < SHOTS; i++) {
            BallisticResolver.Resolution res = resolver.resolve(shooter, target, 1f, 0f, VEL, MAX_RANGE, rng);
            if (res.victimId() != target) blocked++;
        }
        return blocked / (float) SHOTS;
    }

    @Test
    void aTrenchCoversItsOccupantAlikeFromFrontBackAndAlongTheRun() {
        float full = CoverProfile.expectedOccupantInterception(DoodadCover.HEAVY.level());
        float front = blockedShare(TX, ROW - 20);
        float back = blockedShare(TX, ROW + 20);
        float alongWest = blockedShare(TX - 20, ROW);
        float alongEast = blockedShare(TX + 20, ROW);
        float diagonal = blockedShare(TX + 14, ROW + 14);
        for (float share : new float[] {front, back, alongWest, alongEast, diagonal}) {
            assertEquals(full, share, 0.03f, "every bearing meets the same full trench cover at 20 cells: "
                    + "front " + front + " back " + back + " along " + alongWest + "/" + alongEast
                    + " diagonal " + diagonal);
        }
    }

    @Test
    void aShooterWhoClosesOnTheTrenchHasBeatenItsCover() {
        assertEquals(0f, blockedShare(TX, ROW - 3), 0f, "point blank over the lip");
        assertEquals(0f, blockedShare(TX - 3, ROW), 0f, "point blank along the run");
        float mid = blockedShare(TX, ROW - 9);
        float far = blockedShare(TX, ROW - 18);
        assertTrue(mid > 0.1f && mid < far - 0.1f,
                "cover grows with the shooter's distance: 9 cells " + mid + ", 18 cells " + far);
    }

    @Test
    void aRoundCrossingATrenchIsNeverStoppedByIt() {
        BattleSimulation sim = arena(false);
        NavigationGrid grid = sim.getGrid();
        DoodadService doodads = trenchDoodads(grid);
        // Shot across the trench at a man in the open beyond it.
        long target = sim.spawn(new EntitySpec("in-open", Faction.DEFENDER, UnitType.MARINE, TX, ROW + 10));
        long shooter = sim.spawn(new EntitySpec("shooter", Faction.MARINE, UnitType.MARINE, TX, ROW - 10));
        BallisticResolver resolver = new BallisticResolver(grid, doodads, sim.getUnitIndex(), sim.getRoster());
        Random rng = new Random(11L);
        for (int i = 0; i < 500; i++) {
            BallisticResolver.Resolution res = resolver.resolve(shooter, target, 1f, 0f, VEL, MAX_RANGE, rng);
            assertEquals(target, res.victimId(), "the trench is a hole, not a wall: " + res.kind());
        }
    }

    @Test
    void occupantCoverRampsSmoothlyFromThreeCellsToFifteen() {
        assertEquals(0f, BallisticResolver.occupantCoverScale(0f));
        assertEquals(0f, BallisticResolver.occupantCoverScale(3f));
        assertEquals(0.5f, BallisticResolver.occupantCoverScale(9f), 1e-5f);
        assertEquals(1f, BallisticResolver.occupantCoverScale(15f));
        assertEquals(1f, BallisticResolver.occupantCoverScale(40f));
    }

    @Test
    void theManBesideATrenchTakesNoCoverFromIt() {
        DoodadService doodads = trenchDoodads(arena(false).getGrid());
        assertEquals(DoodadCover.HEAVY.level(), doodads.occupantCoverAt(TX, ROW, 0f));
        assertEquals(0, doodads.occupantCoverAt(TX, ROW + 1, 0f));
        assertEquals(0, doodads.occupantCoverAt(TX, ROW - 1, 0f));
        assertEquals(0, doodads.occupantCoverAt(TX, ROW, 0.5f), "a round above the lip clears it");
    }

    @Test
    void aRoundThatGetsPastTheTrenchHitsWhatShowsAboveIt() {
        BattleSimulation sim = arena(false);
        NavigationGrid grid = sim.getGrid();
        DoodadService doodads = trenchDoodads(grid);
        long target = sim.spawn(new EntitySpec("in-trench", Faction.DEFENDER, UnitType.MARINE, TX, ROW));
        long shooter = sim.spawn(new EntitySpec("shooter", Faction.MARINE, UnitType.MARINE, TX, ROW - 20));
        BallisticResolver resolver = new BallisticResolver(grid, doodads, sim.getUnitIndex(), sim.getRoster());
        Random rng = new Random(3L);
        int[] landed = new int[BodyPart.values().length];
        for (int i = 0; i < SHOTS; i++) {
            BallisticResolver.Resolution res = resolver.resolve(shooter, target, 1f, 0f, VEL, MAX_RANGE, rng);
            if (res.victimId() == target) landed[res.victimPart().ordinal()]++;
        }
        assertEquals(0, landed[BodyPart.LEGS.ordinal()], "the legs are below the lip");
        assertTrue(landed[BodyPart.HEAD.ordinal()] + landed[BodyPart.ARMS.ordinal()]
                        > landed[BodyPart.TORSO.ordinal()],
                "head and arms take most of what lands: " + Arrays.toString(landed));
    }

    @Test
    void aRoundThatLandsOnAPartTakesNoFurtherCutForTheTrench() {
        float open = damageTakenOnPart(false);
        assertEquals(open, damageTakenOnPart(true), 1e-3f);
    }

    private static float damageTakenOnPart(boolean trench) {
        BattleSimulation sim = arena(trench);
        long target = sim.spawn(new EntitySpec("target", Faction.DEFENDER, UnitType.MARINE, TX, ROW));
        long shooter = sim.spawn(new EntitySpec("shooter", Faction.MARINE, UnitType.MARINE, TX, ROW - 20));
        float before = sim.world().hp(target) + (sim.world().hasArmor(target) ? sim.world().armor(target) : 0f);
        sim.getRoster().damageService().applyDamage(target, shooter, 10f, 100f, 0f, BodyPart.HEAD);
        return before - sim.world().hp(target) - (sim.world().hasArmor(target) ? sim.world().armor(target) : 0f);
    }

    @Test
    void aPayloadWithNoPartIsStillCutByTheTrenchFromEveryBearingAtRange() {
        float full = DamageResolver.COVER_DAMAGE_REDUCTION[DoodadCover.HEAVY.level()];
        for (int[] at : new int[][] {{TX, ROW - 20}, {TX, ROW + 20}, {TX - 20, ROW}}) {
            float open = damageTaken(false, at[0], at[1]);
            float dug = damageTaken(true, at[0], at[1]);
            assertEquals(open * (1f - full), dug, 1e-3f, "bearing " + at[0] + "," + at[1]);
        }
        float close = damageTaken(true, TX, ROW - 2);
        assertEquals(damageTaken(false, TX, ROW - 2), close, 1e-3f, "no cut at point blank");
    }

    private static float damageTaken(boolean trench, int sx, int sy) {
        BattleSimulation sim = arena(trench);
        long target = sim.spawn(new EntitySpec("target", Faction.DEFENDER, UnitType.MARINE, TX, ROW));
        long shooter = sim.spawn(new EntitySpec("shooter", Faction.MARINE, UnitType.MARINE, sx, sy));
        float before = sim.world().hp(target) + (sim.world().hasArmor(target) ? sim.world().armor(target) : 0f);
        sim.applyDamage(target, shooter, 10f, 100f, 0f);
        return before - sim.world().hp(target) - (sim.world().hasArmor(target) ? sim.world().armor(target) : 0f);
    }
}
