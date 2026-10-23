package com.dillon.starsectormarines.battle.mech;

import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.scene.LookGround;
import com.dillon.starsectormarines.battle.scene.LookReview;
import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.scene.SceneRegistries;
import com.dillon.starsectormarines.battle.scene.SceneWorld;
import com.dillon.starsectormarines.battle.sim.World;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;

import java.util.ArrayList;
import java.util.List;

/**
 * Worn plate on live mechs ({@code combat-durability-nouns.md}): a row of Hounds and a row of Bulwarks on real ground,
 * every section's armour at 100, 66, 33 and 10% (structure losing half as much), at play zoom and close. Owner-facing
 * look frames only; there is no verdict.
 */
public final class MechScuffsSnapshotSuite implements SnapshotSuite {

    /** Each mech's armour, as a share of its maximum, west to east. */
    static final float[] SHARES = {1f, 0.66f, 0.33f, 0.10f};
    private static final float SPACING = 6f;

    @Override public String id() { return "mech-scuffs"; }

    @Override public String label() { return "Mech scuffs (worn plate)"; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        SceneRegistries.installArmoury(context.projectRoot());
        return LookReview.record(context.modRoot(), shot());
    }

    static LookReview.Shot shot() {
        MechVariant[] rows = {MechVariant.HOUND, MechVariant.BULWARK};
        return new LookReview.Shot("mech-scuffs", LookGround.verdant(), 40, 20, placement -> {
            SceneBuilder builder = placement.holdouts(placement.builder());
            List<String> keys = new ArrayList<>();
            for (int row = 0; row < rows.length; row++) {
                for (int i = 0; i < SHARES.length; i++) {
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
                for (int i = 0; i < SHARES.length; i++) {
                    String key = keys.get(row * SHARES.length + i);
                    long id = world.members(key)[0];
                    placement.subject(key, id);
                    World w = world.sim().world();
                    // Sections are divided on a mech's first hit; a staged look divides them as that hit would.
                    w.mechLoadout(id).sectionsFor(w.hp(id), w.maxHp(id), w.armor(id), w.maxArmor(id))
                            .stageWear(SHARES[i], 1f - (1f - SHARES[i]) / 2f);
                }
            }
            return world.sim();
        }, placement -> List.of(
                LookReview.View.followAtPlay(placement, "hound-0", "bulwark-3"),
                LookReview.View.follow("close", placement, 28f, "hound-0", "bulwark-3")),
                12, 6, 1);
    }
}
