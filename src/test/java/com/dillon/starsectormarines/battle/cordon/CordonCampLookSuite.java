package com.dillon.starsectormarines.battle.cordon;

import com.dillon.starsectormarines.battle.scene.LookGround;
import com.dillon.starsectormarines.battle.scene.LookReview;
import com.dillon.starsectormarines.battle.scene.SceneRegistries;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.testsupport.HeadlessBattles;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;

import java.util.List;

/**
 * The Road's safe camp behind the line ({@code cordon-nouns.md}, Camp), as the owner would see it: Ain Tafrant's battle
 * (town, arid, seed 7) set up and drawn after its first tick through the look preset over the camp, its sandbag ring,
 * crate stacks and guards on the relief road, with no battle played. A seed whose rear found no camp is refused.
 *
 * <p>{@code -Dsnapshot.cordonCamp.seed=7} chooses the seed.
 */
public final class CordonCampLookSuite implements SnapshotSuite {

    @Override public String id() { return "cordon-camp-look"; }

    @Override public String label() { return "Cordon Road: the safe camp the convoys bring the townspeople to"; }

    @Override public boolean inAll() { return false; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        SceneRegistries.installArmoury(context.projectRoot());
        long seed = Long.parseLong(System.getProperty("snapshot.cordonCamp.seed", "7").trim());
        BattleSimulation probe = battle(seed);
        CordonCamp camp = probe.getCordon().convoys() == null ? null : probe.getCordon().convoys().camp();
        if (camp == null) throw new IllegalStateException("no camp on seed " + seed);
        float x = camp.centreX() + 0.5f, y = camp.centreY() + 0.5f;
        CordonConvoys convoys = probe.getCordon().convoys();
        int[] drop = convoys.corridor().get(convoys.entry());
        System.out.printf("    [look/camp] seed %d: camp at (%d, %d), trucks stop at (%d, %d), corridor cell %d; gap at"
                        + " cell %d: safe leg %d, drive %d road cells%n", seed, camp.centreX(), camp.centreY(), drop[0],
                drop[1], convoys.entry(), convoys.gapIndex(), convoys.gapIndex() - convoys.entry(),
                convoys.corridor().size() - 1 - convoys.entry());
        return LookReview.record(context.modRoot(), new LookReview.Shot("camp",
                LookGround.of(SurfacePalette.ARID, seed), 40, 30, placement -> battle(seed), placement -> List.of(
                        LookReview.View.close("camp", x, y, 48f),
                        LookReview.View.close("camp-road", x, y, 120f)), 1, 1, 1)
                .withStills(1));
    }

    /** Ain Tafrant's battle on this seed's map, set up and not yet played. */
    private static BattleSimulation battle(long seed) {
        try {
            return HeadlessBattles.build("cordon:variant=road,density=town,biome=arid,seed=" + seed);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
