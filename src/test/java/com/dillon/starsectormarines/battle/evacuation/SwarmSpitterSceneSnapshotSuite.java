package com.dillon.starsectormarines.battle.evacuation;

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
 * The SwarmSpitter loops, from the same run that produced their verdicts; and for the owner, the Spitter's look on
 * real generated ground as a small staged encounter: two Spitters already in range of three Marines, two Runners
 * between them, a few seconds played, at play zoom on the fight, close on a Spitter as it braces and lobs, and close
 * on the Marines where the acid lands.
 */
public final class SwarmSpitterSceneSnapshotSuite extends BehaviorSceneSnapshotSuite {

    static final int W = 40, H = 24, TICKS = 150;
    /** Recorded frames (every 2 ticks) kept as stills: every 12 ticks, so a brace and a lob are among them. */
    static final int[] STILLS = {6, 12, 18, 24, 30, 36, 42, 48, 54, 60, 66, 72};

    public SwarmSpitterSceneSnapshotSuite() {
        super(new SwarmSpitterScene(), 1920, 1080);
    }

    @Override protected boolean showImpactEffects() { return true; }

    @Override protected boolean showUnitShadows() { return true; }

    @Override protected List<LookReview.Shot> lookShots() {
        return List.of(new LookReview.Shot("spitter-volley", LookGround.verdant(), W, H,
                SwarmSpitterSceneSnapshotSuite::stage,
                placement -> List.of(
                        LookReview.View.followAtPlay(placement, "line", "spitter"),
                        LookReview.View.follow("spitter", placement, 8f, "spitter"),
                        LookReview.View.follow("line", placement, 10f, "line")),
                TICKS, 2, 3, STILLS));
    }

    /** Three Marines holding in the west, two Spitters twelve cells east of them, two Runners between. */
    private static BattleSimulation stage(LookReview.Placement placement) {
        SceneBuilder builder = placement.builder();
        float[] line = placement.openNear(8f, 12f);
        builder.squad("line").faction(Faction.MARINE).size(3)
                .at((int) line[0] - placement.site().x0(), (int) line[1] - placement.site().y0())
                .primary("weapon.field-rifle").health(5000f).stationary().done();
        SceneWorld world = builder.build();
        BattleSimulation sim = world.sim();
        placement.subject("line", world.members("line")[0]);
        for (int i = 0; i < 2; i++) {
            float[] at = placement.openNear(15f, 10f + i * 4f);
            sim.spawn(SwarmStrains.spec("Runner", UnitType.SWARM_RUNNER, (int) at[0], (int) at[1]).health(5000f));
        }
        for (int i = 0; i < 2; i++) {
            float[] at = placement.openNear(20f, 9f + i * 6f);
            long spitter = sim.spawn(SwarmStrains.spec("Spitter", UnitType.SWARM_SPITTER, (int) at[0], (int) at[1])
                    .health(5000f));
            if (i == 0) placement.subject("spitter", spitter);
        }
        return sim;
    }
}
