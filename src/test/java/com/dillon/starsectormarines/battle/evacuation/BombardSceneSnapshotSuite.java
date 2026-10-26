package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.creep.CreepGrowth;
import com.dillon.starsectormarines.battle.scene.BehaviorSceneSnapshotSuite;
import com.dillon.starsectormarines.battle.scene.LookGround;
import com.dillon.starsectormarines.battle.scene.LookReview;
import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.scene.SceneWorld;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;

import java.util.List;

/**
 * The Bombard loops, from the same run that produced their verdicts; and two staged shots on real generated ground. The
 * lob: a Bombard planted on its creep, a rifle squad holding twenty-eight cells off, played through the plant, the
 * wind-up, the glob's flight and its burst. Under fire: a Bombard planting on its creep with a rifle squad inside its
 * minimum range, its hits showing as ichor puffs and wear until it dies and falls apart. The walk: a Bombard off its
 * creep, which lies behind it, turning round and walking back to it, a tripod at a time, then planting.
 */
public final class BombardSceneSnapshotSuite extends BehaviorSceneSnapshotSuite {

    static final int W = 60, H = 24, TICKS = 360;
    /** Recorded frames (every 2 ticks) kept as stills: planted, winding up, released, in flight, burst, standing. */
    static final int[] STILLS = {46, 70, 92, 110, 130, 144, 150, 170};
    /** The fire shot: ticks, the squad's distance, and the Bombard's health, enough to last a few seconds of rifles. */
    static final int FIRE_TICKS = 300;
    /** The walk shot: ticks, and how far east of its creep the Bombard starts, cells. */
    static final int WALK_TICKS = 600;
    static final float WALK_CELLS = 13f;
    static final float FIRE_RANGE = 10f, FIRE_HEALTH = 60f;

    public BombardSceneSnapshotSuite() {
        super(new BombardScene(), 1920, 1080);
    }

    @Override protected boolean showImpactEffects() { return true; }

    @Override protected boolean showUnitShadows() { return true; }

    @Override protected List<LookReview.Shot> lookShots() {
        return List.of(new LookReview.Shot("bombard-lob", LookGround.verdant(), W, H,
                BombardSceneSnapshotSuite::stage,
                placement -> List.of(
                        LookReview.View.followAtPlay(placement, "line", "bombard"),
                        LookReview.View.follow("bombard", placement, 10f, "bombard"),
                        LookReview.View.follow("line", placement, 14f, "line")),
                TICKS, 2, 4, STILLS),
                new LookReview.Shot("bombard-fire", LookGround.verdant(), W, H,
                        BombardSceneSnapshotSuite::underFire,
                        placement -> List.of(
                                LookReview.View.followAtPlay(placement, "line", "bombard"),
                                LookReview.View.follow("bombard", placement, 10f, "bombard")),
                        FIRE_TICKS, 2, 6),
                new LookReview.Shot("bombard-walk", LookGround.verdant(), W, H,
                        BombardSceneSnapshotSuite::walking,
                        placement -> List.of(
                                LookReview.View.followAtPlay(placement, "bombard"),
                                LookReview.View.follow("bombard", placement, 10f, "bombard")),
                        WALK_TICKS, 2, 6));
    }

    /**
     * A Bombard {@link #WALK_CELLS} east of a small creep patch: it walks back to plant. An unarmed squad watches from
     * inside its minimum range, so the ground is seen (and heard) and nothing is thrown.
     */
    private static BattleSimulation walking(LookReview.Placement placement) {
        SceneBuilder builder = placement.builder();
        float[] watch = placement.openNear(20f + WALK_CELLS * 0.5f, 3f);
        builder.squad("watch").faction(Faction.MARINE).size(2)
                .at((int) watch[0] - placement.site().x0(), (int) watch[1] - placement.site().y0())
                .unarmed().health(5000f).stationary().done();
        SceneWorld world = builder.build();
        BattleSimulation sim = world.sim();
        float[] creep = placement.openNear(20f, 12f);
        sim.creepField().putSource(1L, (int) creep[0], (int) creep[1], new CreepGrowth(2f, 2f));
        sim.matureCreep();
        float[] at = placement.openNear(20f + WALK_CELLS, 12f);
        long bombard = sim.spawn(SwarmStrains.spec("Bombard", UnitType.SWARM_BOMBARD, (int) at[0], (int) at[1])
                .health(5000f));
        placement.subject("bombard", bombard);
        return sim;
    }

    /** A Bombard on its creep in the west, a rifle squad {@link #FIRE_RANGE} cells east, inside its minimum range. */
    private static BattleSimulation underFire(LookReview.Placement placement) {
        SceneBuilder builder = placement.builder();
        float[] line = placement.openNear(24f + FIRE_RANGE, 12f);
        builder.squad("line").faction(Faction.MARINE).size(8)
                .at((int) line[0] - placement.site().x0(), (int) line[1] - placement.site().y0())
                .primary("weapon.field-rifle").health(5000f).stationary().done();
        SceneWorld world = builder.build();
        BattleSimulation sim = world.sim();
        placement.subject("line", world.members("line")[0]);
        float[] at = placement.openNear(24f, 12f);
        sim.creepField().putSource(1L, (int) at[0], (int) at[1], new CreepGrowth(6f, 6f));
        sim.matureCreep();
        long bombard = sim.spawn(SwarmStrains.spec("Bombard", UnitType.SWARM_BOMBARD, (int) at[0], (int) at[1])
                .health(FIRE_HEALTH));
        placement.subject("bombard", bombard);
        return sim;
    }

    /** A Bombard on its creep in the west, a rifle squad twenty-eight cells east of it. */
    private static BattleSimulation stage(LookReview.Placement placement) {
        SceneBuilder builder = placement.builder();
        float[] line = placement.openNear(52f, 12f);
        builder.squad("line").faction(Faction.MARINE).size(6)
                .at((int) line[0] - placement.site().x0(), (int) line[1] - placement.site().y0())
                .primary("weapon.field-rifle").health(5000f).stationary().done();
        SceneWorld world = builder.build();
        BattleSimulation sim = world.sim();
        placement.subject("line", world.members("line")[0]);
        float[] at = placement.openNear(24f, 12f);
        sim.creepField().putSource(1L, (int) at[0], (int) at[1], new CreepGrowth(6f, 6f));
        sim.matureCreep();
        long bombard = sim.spawn(SwarmStrains.spec("Bombard", UnitType.SWARM_BOMBARD, (int) at[0], (int) at[1])
                .health(5000f));
        placement.subject("bombard", bombard);
        return sim;
    }
}
