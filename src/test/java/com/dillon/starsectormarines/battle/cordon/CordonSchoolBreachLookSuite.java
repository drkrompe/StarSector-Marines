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
 * A built redoubt under the swarm, as the owner would see it: the town Line the evidence plays, played once
 * unrecorded to find the tick the chosen redoubt first calls for help, then played again to before it and recorded
 * through the look preset with the camera held on the redoubt, as the swarm comes over its face and in.
 *
 * <p>{@code -Dsnapshot.cordonBreach.battle=cordon:variant=line,density=town,biome=rock,seed=8}, {@code .redoubt=4}
 * and {@code .ticks=1800} choose the battle, the redoubt and the length; the shot opens {@link #LEAD} ticks before the
 * redoubt calls. Opt-in: it plays a battle twice before it draws.
 */
public final class CordonSchoolBreachLookSuite implements SnapshotSuite {

    /** The latest tick the call is waited for. */
    static final int BY = 27000;
    /** Ticks of the swarm's approach the shot keeps before the redoubt calls. */
    static final int LEAD = 300;
    /** Ticks between recorded frames. */
    static final int EVERY = 2;

    @Override public String id() { return "cordon-school-breach-look"; }

    @Override public String label() { return "Cordon: a built redoubt (a school by default) as the swarm breaks in"; }

    @Override public boolean inAll() { return false; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        SceneRegistries.installArmoury(context.projectRoot());
        String source = System.getProperty("snapshot.cordonBreach.battle",
                "cordon:variant=line,density=town,biome=rock,seed=8").trim();
        int number = Integer.parseInt(System.getProperty("snapshot.cordonBreach.redoubt", "4").trim());
        int ticks = Integer.parseInt(System.getProperty("snapshot.cordonBreach.ticks", "1800").trim());
        BattleSimulation probe = battle(source);
        while (redoubt(probe, number).state() == CordonRedoubt.State.HOLDING) {
            if (probe.getSimTickIndex() >= BY) throw new IllegalStateException("redoubt " + number + " never called by " + BY);
            probe.advance(BattleSimulation.TICK_DT);
        }
        int from = Math.max(1, probe.getSimTickIndex() - LEAD);
        CordonRedoubt r = redoubt(probe, number);
        float cx = r.centreX() + 0.5f, cy = r.centreY() + 0.5f;
        // Stills are counted in recorded frames, one every EVERY ticks.
        int frames = ticks / EVERY;
        return LookReview.record(context.modRoot(), new LookReview.Shot("breach", LookGround.of(palette(source), seed(source)),
                40, 30, placement -> {
                    BattleSimulation sim = battle(source);
                    while (sim.getSimTickIndex() < from) sim.advance(BattleSimulation.TICK_DT);
                    System.out.printf("    [look/breach] %s redoubt %d (%s), recorded from tick %d%n", source, number,
                            redoubt(sim, number).construction(), sim.getSimTickIndex());
                    return sim;
                }, placement -> List.of(LookReview.View.play(cx, cy), LookReview.View.close("close", cx, cy, 40f)),
                ticks, EVERY, 3)
                .withStills(0, frames / 4, frames / 2, 3 * frames / 4, frames - 1));
    }

    private static BattleSimulation battle(String source) {
        try {
            return HeadlessBattles.build(source);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static long seed(String source) {
        int at = source.indexOf("seed=");
        if (at < 0) return 0L;
        String rest = source.substring(at + 5);
        int end = rest.indexOf(',');
        return Long.parseLong(end < 0 ? rest : rest.substring(0, end));
    }

    private static SurfacePalette palette(String source) {
        for (SurfacePalette p : SurfacePalette.values()) if (source.contains("biome=" + p.name().toLowerCase())) return p;
        return SurfacePalette.ARID;
    }

    private static CordonRedoubt redoubt(BattleSimulation sim, int number) {
        for (CordonRedoubt r : sim.getCordon().redoubts()) if (r.number() == number) return r;
        throw new IllegalStateException("no redoubt " + number);
    }
}
