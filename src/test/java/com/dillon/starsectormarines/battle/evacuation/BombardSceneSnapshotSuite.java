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
 * The Bombard loops, from the same run that produced their verdicts; and a staged lob on real generated ground: a
 * Bombard planted on its creep, a rifle squad holding twenty-eight cells off, played through the plant, the wind-up,
 * the glob's flight and its burst. The Bombard draws as a placeholder until its rig lands (`bombard.md`, step 4).
 */
public final class BombardSceneSnapshotSuite extends BehaviorSceneSnapshotSuite {

    static final int W = 60, H = 24, TICKS = 360;
    /** Recorded frames (every 2 ticks) kept as stills: planted, winding up, released, in flight, burst, standing. */
    static final int[] STILLS = {46, 70, 92, 110, 130, 144, 150, 170};

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
                TICKS, 2, 4, STILLS));
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
