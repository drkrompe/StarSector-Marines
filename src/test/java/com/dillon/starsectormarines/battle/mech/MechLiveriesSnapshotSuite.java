package com.dillon.starsectormarines.battle.mech;

import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.scene.LookGround;
import com.dillon.starsectormarines.battle.scene.LookReview;
import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.scene.SceneRegistries;
import com.dillon.starsectormarines.battle.scene.SceneWorld;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.ops.battleview.ReviewFactions;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;

import java.util.ArrayList;
import java.util.List;

/**
 * The three mech chassis in a faction's livery, in their stock fits: one shot for the Hegemony, whose low-tech bodies
 * are its own (art-source/mech-modular-topdown/sources/lowtech), and one for Tri-Tachyon, a repaint of the shared
 * outlines, to compare them against. Owner-facing look frames only; there is no verdict.
 */
public final class MechLiveriesSnapshotSuite implements SnapshotSuite {

    private static final MechVariant[] LINE = {MechVariant.BULWARK, MechVariant.HOUND, MechVariant.WARDEN};
    private static final float SPACING = 4.5f;

    @Override public String id() { return "mech-liveries"; }

    @Override public String label() { return "The Bulwark, Hound and Warden in the Hegemony and Tri-Tachyon liveries"; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        SceneRegistries.installArmoury(context.projectRoot());
        List<SnapshotArtifact> out = new ArrayList<>();
        for (String faction : new String[] {"hegemony", "tritachyon"}) {
            try (ReviewFactions ignored = ReviewFactions.bind(faction, "pirates")) {
                out.addAll(LookReview.record(context.modRoot(), shot("mech-liveries-" + faction)));
            }
        }
        return out;
    }

    static LookReview.Shot shot(String name) {
        return new LookReview.Shot(name, LookGround.verdant(), 32, 16, placement -> {
            SceneBuilder builder = placement.holdouts(placement.builder());
            for (int i = 0; i < LINE.length; i++) {
                float[] at = placement.openNear(16f + (i - 1) * SPACING, 8f);
                int holdX = (int) at[0];
                int holdY = (int) at[1];
                builder.squad(key(i)).faction(Faction.MARINE).size(1)
                        .at(holdX - placement.site().x0(), holdY - placement.site().y0())
                        .mech(LINE[i], MechRole.ASSAULT)
                        .assigned(id -> ObjectiveAssignment.defendArea(id, holdX, holdY))
                        .done();
            }
            SceneWorld world = builder.build();
            for (int i = 0; i < LINE.length; i++) placement.subject(key(i), world.members(key(i))[0]);
            return world.sim();
        }, placement -> List.of(
                LookReview.View.followAtPlay(placement, key(0), key(2)),
                LookReview.View.follow("close", placement, 16f, key(0), key(2))),
                12, 6, 1);
    }

    private static String key(int i) {
        return LINE[i].name().toLowerCase();
    }
}
