package com.dillon.starsectormarines.battle.deployable;

import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment.FireMission;
import com.dillon.starsectormarines.battle.scene.BehaviorSceneSnapshotSuite;
import com.dillon.starsectormarines.battle.scene.LookReview;
import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.scene.SceneWorld;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.marine.SpecialEquipmentRegistry;

import java.util.List;

/**
 * The field mortar's frames, from the same run that produced its verdicts: the {@code mortar-team} loop, the
 * {@code rushed} loop and the {@code section-march} loop (a twelve-marine squad halting a four-person crew while the rest
 * is leashed, then packing up and moving on) at 1920x1080 and 100% scale, whole map plus a close view on the section — the set-up, the
 * tube firing across the standoff, the shell in flight and the impacts on the nest — with every close frame written
 * as a PNG still.
 *
 * <p>Those loops are verdict evidence. The owner is shown {@code look/mortar-section-*}: a rifle section with its
 * mortar shelling a militia picket on real generated ground through the look preset.
 */
public final class FieldMortarSceneSnapshotSuite extends BehaviorSceneSnapshotSuite {

    private static final int WIDTH = 1920;
    private static final int HEIGHT = 1080;

    public FieldMortarSceneSnapshotSuite() {
        super(new FieldMortarScene(List.of("mortar-team", "rushed", "section-march"), 60, 1500, 15), WIDTH, HEIGHT);
    }

    @Override protected boolean showImpactEffects() { return true; }

    /** Midway between the section and the gate it is shelling, wide enough to hold both. */
    @Override protected float[] closeView(String loopId) {
        int[] s = FieldMortarScene.lastSection();
        return new float[] {(s[0] + s[2]) / 2f, (s[1] + s[3]) / 2f, 70f};
    }

    @Override protected float closeViewScale() { return 1f; }

    /**
     * A six-marine rifle section with one field mortar at the west of a 64x36 window, ordered onto its own ground with
     * a fire mission on a six-strong militia picket 32 cells east (beyond every rifle): the tube going down and
     * shelling, at play zoom following both, close on the section, and close on the picket.
     */
    @Override protected List<LookReview.Shot> lookShots() {
        return List.of(LookReview.Shot.of("mortar-section", 64, 36, placement -> {
            float[] picket = placement.openNear(44f, 18f);
            int px = (int) picket[0] - placement.site().x0();
            int py = (int) picket[1] - placement.site().y0();
            float[] own = placement.openNear(12f, 18f);
            int sx = (int) own[0];
            int sy = (int) own[1];
            FireMission mission = new FireMission((int) picket[0], (int) picket[1], 6);
            SceneBuilder builder = placement.builder();
            builder.squad("section").faction(Faction.MARINE).size(6)
                    .at(sx - placement.site().x0(), sy - placement.site().y0()).primary("weapon.field-rifle")
                    .special(SpecialEquipmentRegistry.FIELD_MORTAR_ID, 1)
                    .assigned(id -> ObjectiveAssignment.attackMove(id, sx, sy).withFireMission(mission)).done();
            builder.squad("picket").faction(Faction.DEFENDER).type(UnitType.MILITIA).size(6).at(px, py)
                    .primary("weapon.smg").stationary().done();
            SceneWorld world = builder.build();
            placement.subject("section", world.members("section")[0]);
            placement.subject("picket", world.members("picket")[0]);
            return world.sim();
        }, placement -> List.of(LookReview.View.followAtPlay(placement, "section", "picket"),
                LookReview.View.follow("section", placement, 24f, "section"),
                LookReview.View.follow("picket", placement, 24f, "picket")), 1800));
    }

    @Override protected int[] stillFrames(String loopId) {
        if (!loopId.endsWith("-close")) return new int[0];
        int[] all = new int[40];
        for (int i = 0; i < all.length; i++) all[i] = i;
        return all;
    }
}
