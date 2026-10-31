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
 * The allies' airbase lot in the rear ({@code cordon-nouns.md}, Air base), as the owner would see it: the Road
 * fixture's battle set up and drawn after its first tick (when the field parks its airframes) through the look preset over the lot, the wing's aircraft in the
 * sheds and on the berths, with no battle played. A seed whose map laid no lot is refused rather than drawn on the
 * fallback.
 *
 * <p>{@code -Dsnapshot.cordonAirBase.seed=7117} and {@code .density=rural} choose the battle.
 */
public final class CordonAirBaseLookSuite implements SnapshotSuite {

    @Override public String id() { return "cordon-airbase-look"; }

    @Override public String label() { return "Cordon: the allies' airbase lot, its aircraft on their berths"; }

    @Override public boolean inAll() { return false; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        SceneRegistries.installArmoury(context.projectRoot());
        long seed = Long.parseLong(System.getProperty("snapshot.cordonAirBase.seed", "7117").trim());
        String density = System.getProperty("snapshot.cordonAirBase.density", "rural").trim();
        BattleSimulation probe = battle(seed, density);
        CordonAirBase air = probe.getCordon().airBase();
        if (air == null) throw new IllegalStateException("no air base on seed " + seed);
        if (air.ground() != CordonAirBase.Ground.LOT) {
            throw new IllegalStateException("seed " + seed + " laid no airbase lot: the wing stands on open ground");
        }
        int[] lot = air.centre();
        // The field parks an airframe on each berth at its first tick: the still is that tick, one sixtieth of a second
        // in, before anything has moved.
        return LookReview.record(context.modRoot(), new LookReview.Shot("airbase",
                LookGround.of(SurfacePalette.VERDANT, seed), 40, 30, placement -> {
                    System.out.printf("    [look/airbase] seed %d %s: lot at (%d, %d)%n", seed, density, lot[0], lot[1]);
                    return battle(seed, density);
                }, placement -> List.of(
                        LookReview.View.close("lot", lot[0] + 0.5f, lot[1] + 0.5f, 48f),
                        LookReview.View.play(lot[0] + 0.5f, lot[1] + 0.5f)), 1, 1, 1)
                .withStills(1));
    }

    /** The battle: the Road fixture's company on this seed's map, set up and not yet played. */
    private static BattleSimulation battle(long seed, String density) {
        try {
            return HeadlessBattles.build("cordon:variant=road,density=" + density + ",biome=verdant,seed=" + seed);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
