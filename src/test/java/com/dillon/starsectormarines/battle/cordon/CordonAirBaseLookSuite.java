package com.dillon.starsectormarines.battle.cordon;

import com.dillon.starsectormarines.battle.air.AirfieldService;
import com.dillon.starsectormarines.battle.airsupport.AirPoolService;
import com.dillon.starsectormarines.battle.airsupport.FieldAirBase;
import com.dillon.starsectormarines.battle.scene.LookGround;
import com.dillon.starsectormarines.battle.scene.LookReview;
import com.dillon.starsectormarines.battle.scene.SceneRegistries;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.testsupport.HeadlessBattles;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;

import java.util.List;

/**
 * The allies' air base behind the line ({@code cordon-nouns.md}, Air base), as the owner would see it: the Road
 * fixture's battle played once unrecorded to find the first sortie off the apron, then played again to just before it
 * and recorded through the look preset on the apron, as the aircraft lifts off its stand, and later comes home to it.
 *
 * <p>{@code -Dsnapshot.cordonAirBase.seed=7117}, {@code .density=rural} and {@code .ticks=600} choose the battle and the
 * length. Opt-in: it plays a battle twice before it draws.
 */
public final class CordonAirBaseLookSuite implements SnapshotSuite {

    /** The latest tick the first launch is waited for. */
    static final int BY = 9000;
    /** Ticks of the apron at rest the shot keeps before the launch. */
    static final int LEAD = 60;
    /** Ticks between recorded frames. */
    static final int EVERY = 3;

    @Override public String id() { return "cordon-airbase-look"; }

    @Override public String label() { return "Cordon: the allies' air base, an aircraft lifting off its stand"; }

    @Override public boolean inAll() { return false; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        SceneRegistries.installArmoury(context.projectRoot());
        long seed = Long.parseLong(System.getProperty("snapshot.cordonAirBase.seed", "7117").trim());
        String density = System.getProperty("snapshot.cordonAirBase.density", "rural").trim();
        int ticks = Integer.parseInt(System.getProperty("snapshot.cordonAirBase.ticks", "600").trim());
        BattleSimulation probe = battle(seed, density);
        if (probe.getCordon().airBase() == null) throw new IllegalStateException("no air base on seed " + seed);
        while (!launched(probe)) {
            if (probe.getSimTickIndex() >= BY) throw new IllegalStateException("no sortie off the apron by tick " + BY);
            probe.advance(BattleSimulation.TICK_DT);
        }
        int from = Math.max(1, probe.getSimTickIndex() - LEAD);
        int[] apron = probe.getCordon().airBase().centre();
        int frames = ticks / EVERY;
        return LookReview.record(context.modRoot(), new LookReview.Shot("airbase",
                LookGround.of(SurfacePalette.VERDANT, seed), 40, 30, placement -> {
                    BattleSimulation sim = battle(seed, density);
                    while (sim.getSimTickIndex() < from) sim.advance(BattleSimulation.TICK_DT);
                    System.out.printf("    [look/airbase] seed %d %s: apron at (%d, %d), recorded from tick %d%n",
                            seed, density, apron[0], apron[1], sim.getSimTickIndex());
                    return sim;
                }, placement -> List.of(
                        LookReview.View.close("apron", apron[0] + 0.5f, apron[1] + 0.5f, 48f),
                        LookReview.View.play(apron[0] + 0.5f, apron[1] + 0.5f)), ticks, EVERY, 3)
                .withStills(0, LEAD / EVERY + 10, LEAD / EVERY + 40, frames - 1));
    }

    /** The battle: the Road fixture's company on this seed's map. */
    private static BattleSimulation battle(long seed, String density) {
        try {
            return HeadlessBattles.build("cordon:variant=road,density=" + density + ",biome=verdant,seed=" + seed);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** Whether some stand's aircraft is away: the first sortie has left the apron. */
    private static boolean launched(BattleSimulation sim) {
        AirPoolService pool = sim.airPool();
        if (pool == null || !(pool.base(Faction.ALLY) instanceof FieldAirBase base)) return false;
        for (FieldAirBase.Stand s : base.stands()) {
            if (s.berth().state == AirfieldService.BerthState.AWAY) return true;
        }
        return false;
    }
}
