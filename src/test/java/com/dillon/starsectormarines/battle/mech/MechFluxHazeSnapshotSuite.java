package com.dillon.starsectormarines.battle.mech;

import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.scene.LookGround;
import com.dillon.starsectormarines.battle.scene.LookReview;
import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.scene.SceneRegistries;
import com.dillon.starsectormarines.battle.scene.SceneWorld;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;

import java.util.ArrayList;
import java.util.List;

/**
 * Flux on live mechs ({@code FluxHaze}): a row of Hounds and a row of Bulwarks on real ground, west to east at 0, 60
 * and 85% of their flux capacity and venting from full, held there for four seconds at play zoom and close.
 * Owner-facing look frames only; there is no verdict.
 */
public final class MechFluxHazeSnapshotSuite implements SnapshotSuite {

    /** Each mech's flux, as a share of capacity, west to east; the last is venting. */
    static final float[] LEVELS = {0f, 0.6f, 0.85f, 1f};
    private static final float SPACING = 6f;

    @Override public String id() { return "mech-flux"; }

    @Override public String label() { return "Mech flux haze"; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        SceneRegistries.installArmoury(context.projectRoot());
        return LookReview.record(context.modRoot(), shot());
    }

    static LookReview.Shot shot() {
        MechVariant[] rows = {MechVariant.HOUND, MechVariant.BULWARK};
        return new LookReview.Shot("mech-flux", LookGround.verdant(), 40, 20, placement -> {
            SceneBuilder builder = placement.holdouts(placement.builder());
            List<String> keys = new ArrayList<>();
            for (int row = 0; row < rows.length; row++) {
                for (int i = 0; i < LEVELS.length; i++) {
                    float[] at = placement.openNear(20f + (i - 1.5f) * SPACING, 6f + row * 8f);
                    int x = (int) at[0] - placement.site().x0();
                    int y = (int) at[1] - placement.site().y0();
                    int holdX = (int) at[0];
                    int holdY = (int) at[1];
                    String key = rows[row].name().toLowerCase() + "-" + i;
                    builder.squad(key).faction(Faction.MARINE).size(1).at(x, y)
                            .mech(rows[row], MechRole.ASSAULT)
                            .assigned(id -> ObjectiveAssignment.defendArea(id, holdX, holdY))
                            .done();
                    keys.add(key);
                }
            }
            SceneWorld world = builder.build();
            for (int row = 0; row < rows.length; row++) {
                for (int i = 0; i < LEVELS.length; i++) {
                    String key = keys.get(row * LEVELS.length + i);
                    long id = world.members(key)[0];
                    placement.subject(key, id);
                    MechFlux flux = world.sim().world().mechLoadout(id).flux;
                    flux.stage(LEVELS[i] * flux.capacity(), i == LEVELS.length - 1);
                }
            }
            return world.sim();
        }, placement -> List.of(
                LookReview.View.followAtPlay(placement, "hound-0", "bulwark-3"),
                LookReview.View.follow("close", placement, 28f, "hound-0", "bulwark-3")),
                120, 3, 2);
    }
}
