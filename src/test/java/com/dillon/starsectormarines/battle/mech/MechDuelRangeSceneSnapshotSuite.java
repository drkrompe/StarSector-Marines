package com.dillon.starsectormarines.battle.mech;

import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.scene.BehaviorSceneSnapshotSuite;
import com.dillon.starsectormarines.battle.scene.LookReview;
import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.scene.SceneWorld;
import com.dillon.starsectormarines.battle.unit.Faction;

import java.util.List;

/**
 * {@link MechDuelRangeScene} recorded at 1920x1080, 100% scale: both pairings with the duel-range
 * rule on and off, from the same plays the verdicts are read from, whole map and a close view of the
 * fighting ground. The caption carries the range,
 * each side's rounds and its remaining integrity.
 *
 * <p>Those loops are verdict evidence on a bare grass grid. The owner is shown {@code look/mech-duel-*}: a Warden
 * walking in on a Hound holding its ground, on real generated ground through the look preset.
 */
public final class MechDuelRangeSceneSnapshotSuite extends BehaviorSceneSnapshotSuite {

    public MechDuelRangeSceneSnapshotSuite() {
        super(new MechDuelRangeScene(), 1920, 1080);
    }

    @Override protected boolean showImpactEffects() { return true; }

    @Override
    protected float[] closeView(String loopId) {
        // The ground the fights happen on, between the attacker's stop and the defender's leash.
        return new float[] {74f, 20.5f, 56f};
    }

    @Override protected float closeViewScale() { return 1f; }

    @Override
    protected int[] stillFrames(String loopId) {
        // Frames every 15 ticks. The unordered tank meets its Hound early (first round near tick 265) and the
        // ordered one near tick 630; the mech pairs near tick 950-1150. First rounds, mid-fight, late.
        if (loopId.startsWith("tank-hound")) return new int[] {18, 28, 38};
        if (loopId.equals("tank-ordered")) return new int[] {44, 58, 72};
        return new int[] {80, 110, 145};
    }

    /**
     * A Warden (long-range support) attack-moving from the west of a 64x30 window onto a Hound (assault) defending
     * 50 cells east: the duel at play zoom following both, and close on the pair, 30 cells across.
     */
    @Override protected List<LookReview.Shot> lookShots() {
        return List.of(LookReview.Shot.of("mech-duel", 64, 30, placement -> {
            int houndX = placement.site().x(57);
            int houndY = placement.site().y(15);
            SceneBuilder builder = placement.builder();
            builder.squad("warden").faction(Faction.MARINE).size(1).at(7, 15)
                    .mech(MechVariant.WARDEN, MechRole.LR_SUPPORT)
                    .assigned(id -> ObjectiveAssignment.attackMove(id, houndX, houndY))
                    .done();
            builder.squad("hound").faction(Faction.DEFENDER).size(1).at(57, 15)
                    .mech(MechVariant.HOUND, MechRole.ASSAULT)
                    .assigned(id -> ObjectiveAssignment.defendArea(id, houndX, houndY))
                    .done();
            SceneWorld world = builder.build();
            placement.subject("warden", world.members("warden")[0]);
            placement.subject("hound", world.members("hound")[0]);
            return world.sim();
        }, placement -> List.of(LookReview.View.followAtPlay(placement, "warden", "hound"),
                LookReview.View.follow("duel", placement, 30f, "warden", "hound")), 1800));
    }
}
