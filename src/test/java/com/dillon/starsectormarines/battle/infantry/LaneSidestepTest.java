package com.dillon.starsectormarines.battle.infantry;

import com.dillon.starsectormarines.battle.combat.FiringLane;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.Paths;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.LongBucket;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a marine does about his own man standing in his firing lane.
 *
 * <p>The arena is deliberately the one {@code FriendlyLanePenaltyTest} uses —
 * open floor, a shooter, a squadmate on the line, an enemy beyond — because
 * these two are the two halves of one question and the geometry they ask it of
 * should be the same. That test pins what the <em>picker</em> does, which is
 * nothing; this pins what the marine does instead.
 *
 * <p>Each case is one gate. A reflex is a list of reasons to decline and one
 * reason to act, so a test that set up the acting case and asserted a move
 * would say nothing about the declines — and the declines are the whole of what
 * keeps a reflex from inventing work.
 *
 * <p><b>The reflex ships off and these tests turn it on.</b> That is deliberate
 * rather than a workaround: the Conquest matrix says the verb costs compounds
 * ({@link LaneSidestep} carries the numbers), and what ships is what a default
 * test should pin — but a switched-off behaviour with no tests is a behaviour
 * nobody can revive. So the seam is used here exactly as
 * {@code FiringLineScene} uses it, and one case leaves it alone to pin the
 * shipped answer.
 */
public class LaneSidestepTest {

    private static final int W = 60;
    private static final int H = 40;
    private static final int ROW = 20;
    private static final int SHOOTER_X = 10;
    /**
     * The squadmate in the way, three cells ahead. Where he stands along the
     * lane decides whether a step can clear it at all: the lane converges on
     * the target, so a two-cell shift at the muzzle is worth two cells of
     * clearance right in front of the marine and almost none out at the far
     * end. A mate halfway down a fourteen-cell lane cannot be stepped around
     * inside {@link LaneSidestep#SEARCH_RADIUS}, and the reflex correctly
     * declines — this fixture is the case where a step is a move that exists.
     */
    private static final int MATE_X = 13;
    private static final int ENEMY_X = 24;

    private boolean previouslyEnabled;

    @BeforeEach
    public void enableTheReflex() {
        previouslyEnabled = LaneSidestep.isEnabled();
        LaneSidestep.setEnabledForEvidence(true);
    }

    @AfterEach
    public void restoreTheDefault() {
        LaneSidestep.setEnabledForEvidence(previouslyEnabled);
    }

    private static BattleSimulation arena() {
        NavigationGrid grid = new NavigationGrid(W, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) grid.setWalkableFloor(x, y);
        }
        BattleSimulation sim = new BattleSimulation(grid, new CellTopology(W, H));
        sim.setMissionCompletionEnabled(false);
        return sim;
    }

    private static long marine(BattleSimulation sim, int squadId, String name, int x, int y) {
        return sim.spawn(new EntitySpec(name, Faction.MARINE, UnitType.MARINE, x, y)
                .squad(squadId));
    }

    private static long enemy(BattleSimulation sim, String name, int x, int y) {
        EntitySpec spec = new EntitySpec(name, Faction.DEFENDER, UnitType.MARINE, x, y);
        spec.moveSpeed = 0f;
        return sim.spawn(spec);
    }

    /**
     * A shooter with a squadmate on the line and an enemy beyond it, one tick
     * in so the spatial index and the occupancy map are populated.
     *
     * <p>That tick also runs the ordinary dispatch, which may hand the shooter
     * a path and a cooldown of its own. Both are cleared afterwards: the
     * fixture is "a marine standing to fire with somebody in the way", and a
     * test that inherited whichever plan the first tick happened to author
     * would be measuring that instead. {@link #aMarineAlreadyWalkingIsNotDivertedFromHisStep}
     * and {@link #aMarineWhoseRepositionCooldownIsRunningHoldsHisGround} put
     * each of them back deliberately.
     */
    private static Masked masked(BattleSimulation sim, int mateX, int mateY) {
        int squadId = sim.mintSquad(Faction.MARINE, UnitType.MARINE);
        long shooter = marine(sim, squadId, "m0", SHOOTER_X, ROW);
        long mate = marine(sim, squadId, "m1", mateX, mateY);
        long target = enemy(sim, "e0", ENEMY_X, ROW);
        sim.combat().setTargetId(shooter, target);
        sim.advance(BattleSimulation.TICK_DT);
        stand(sim, shooter, target);
        return new Masked(shooter, mate, target);
    }

    /** Puts one marine back on his feet, aiming, and free to act. */
    private static void stand(BattleSimulation sim, long shooter, long target) {
        sim.combat().setTargetId(shooter, target);
        sim.clearPath(shooter);
        sim.world().setRepositionCooldown(shooter, 0f);
        sim.world().setSidestepTimer(shooter, 0f);
    }

    private record Masked(long shooter, long mate, long target) {}

    /** True when {@code shooter}'s lane onto {@code target} has a friendly in it. */
    private static boolean laneBlocked(BattleSimulation sim, long shooter, long target) {
        FiringLane.Friendlies allies = new FiringLane.Friendlies();
        sim.getTacticalScoring().gatherFriendlies(shooter, 40f, new LongBucket(), allies);
        return FiringLane.blocked(allies, sim.world().x(shooter), sim.world().y(shooter),
                sim.world().x(target), sim.world().y(target));
    }

    private static boolean laneBlockedFromCell(BattleSimulation sim, long shooter,
                                               long target, int cellX, int cellY) {
        FiringLane.Friendlies allies = new FiringLane.Friendlies();
        sim.getTacticalScoring().gatherFriendlies(shooter, 40f, new LongBucket(), allies);
        return FiringLane.blocked(allies, cellX + 0.5f, cellY + 0.5f,
                sim.world().x(target), sim.world().y(target));
    }

    @Test
    public void aMaskedMarineStepsToACellWhoseLaneIsClear() {
        BattleSimulation sim = arena();
        Masked scene = masked(sim, MATE_X, ROW);
        assertTrue(laneBlocked(sim, scene.shooter(), scene.target()),
                "the lane really is blocked, or this test asserts nothing");

        assertTrue(LaneSidestep.stepOutOfLane(scene.shooter(), sim),
                "a marine shooting through his own man steps aside");

        int[] path = sim.world().path(scene.shooter());
        assertFalse(Paths.isEmpty(path), "and leaves the tick holding a path to somewhere");
        int toX = Paths.destX(path);
        int toY = Paths.destY(path);
        assertTrue(Math.max(Math.abs(toX - SHOOTER_X), Math.abs(toY - ROW))
                        <= LaneSidestep.SEARCH_RADIUS,
                "within a step: went to (" + toX + ", " + toY + ")");
        assertFalse(laneBlockedFromCell(sim, scene.shooter(), scene.target(), toX, toY),
                "and the cell it chose has a clear lane onto the same target");
    }

    @Test
    public void anUnmaskedMarineIsLeftAlone() {
        BattleSimulation sim = arena();
        // Six cells off a lane a cell and a half wide: nobody is in the way.
        Masked scene = masked(sim, MATE_X, ROW + 6);
        assertFalse(laneBlocked(sim, scene.shooter(), scene.target()));

        assertFalse(LaneSidestep.stepOutOfLane(scene.shooter(), sim));
        assertTrue(Paths.isEmpty(sim.world().path(scene.shooter())),
                "and is not moved");
    }

    /**
     * A marine already walking is being moved by his step, and his lane changes
     * every tick anyway. Two authors of one movement is the fault this avoids.
     */
    @Test
    public void aMarineAlreadyWalkingIsNotDivertedFromHisStep() {
        BattleSimulation sim = arena();
        Masked scene = masked(sim, MATE_X, ROW);
        sim.setPath(scene.shooter(), new int[]{SHOOTER_X, ROW + 1, SHOOTER_X, ROW + 2});

        assertFalse(LaneSidestep.stepOutOfLane(scene.shooter(), sim));
    }

    /** One cooldown for both short moves, so the two cannot fight over a marine. */
    @Test
    public void aMarineWhoseRepositionCooldownIsRunningHoldsHisGround() {
        BattleSimulation sim = arena();
        Masked scene = masked(sim, MATE_X, ROW);
        sim.world().setRepositionCooldown(scene.shooter(), 1f);

        assertFalse(LaneSidestep.stepOutOfLane(scene.shooter(), sim));
        assertTrue(Paths.isEmpty(sim.world().path(scene.shooter())));
    }

    /**
     * The reflex answers the tick on which the step would author fire intent,
     * so out of range there is nothing to answer: the marine is not shooting
     * anybody, through his own man or otherwise.
     */
    @Test
    public void aMarineOutOfRangeOfItsTargetHasNoLaneToClear() {
        BattleSimulation sim = arena();
        Masked scene = masked(sim, MATE_X, ROW);
        sim.combat().setAttackRange(scene.shooter(), 4f);

        assertFalse(LaneSidestep.stepOutOfLane(scene.shooter(), sim));
    }

    /**
     * Clearing your own lane by stepping into the lane of the man behind you is
     * the same fault one cell back, and a firing line is exactly the case where
     * everybody is shooting at the same thing.
     *
     * <p>The two rear marines are placed so that every cell which would clear
     * the shooter's own lane lies inside one of theirs: they sit level with the
     * north and south edges of the search, far enough back that the whole
     * two-cell box is within a cell of their lines onto the same enemy. So the
     * only moves that would help this marine hurt somebody else, and the right
     * answer is to stand still and let the discipline roll decide — which is
     * the status quo rather than a regression.
     */
    @Test
    public void aCellThatMasksTheManBehindIsRefused() {
        BattleSimulation sim = arena();
        int squadId = sim.mintSquad(Faction.MARINE, UnitType.MARINE);
        long shooter = marine(sim, squadId, "m0", SHOOTER_X, ROW);
        marine(sim, squadId, "m1", MATE_X, ROW);
        marine(sim, squadId, "m2", SHOOTER_X - 6, ROW - 2);
        marine(sim, squadId, "m3", SHOOTER_X - 6, ROW + 2);
        long target = enemy(sim, "e0", ENEMY_X, ROW);
        sim.combat().setTargetId(shooter, target);
        sim.advance(BattleSimulation.TICK_DT);
        stand(sim, shooter, target);
        assertTrue(laneBlocked(sim, shooter, target),
                "the shooter's own lane really is blocked, or this test asserts nothing");

        assertFalse(LaneSidestep.stepOutOfLane(shooter, sim),
                "no cell clears this marine's lane without entering a squadmate's");
        assertTrue(Paths.isEmpty(sim.world().path(shooter)), "and he is not moved");
    }

    /**
     * The shipped answer, and the seam that suspends it. Switched off — which
     * is how the reflex ships — a masked marine is left alone and the ballistic
     * model's discipline roll decides, exactly as it did before any of this
     * existed.
     */
    @Test
    public void theShippedDefaultLeavesAMaskedMarineWhereHeStands() {
        BattleSimulation sim = arena();
        Masked scene = masked(sim, MATE_X, ROW);
        LaneSidestep.setEnabledForEvidence(false);

        assertFalse(LaneSidestep.stepOutOfLane(scene.shooter(), sim));
        assertTrue(Paths.isEmpty(sim.world().path(scene.shooter())));
    }
}
