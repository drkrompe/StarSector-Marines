package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.scene.LookGround;
import com.dillon.starsectormarines.battle.scene.LookReview;
import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.scene.SceneRegistries;
import com.dillon.starsectormarines.battle.scene.SceneWorld;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.turret.MapTurret;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;

import java.util.ArrayList;
import java.util.List;

/**
 * Emergence (`colony-breach-nouns.md`, Emergence) for the owner, before against after: a maw's surge coming up out of
 * its hole, and a brood spire's pack coming out of its crown, each played once with emergence off (the bodies
 * appear whole and go at once) and once with it on. Close on the hole and the spire, a frame every tick (30 fps,
 * real time); {@code -PsnapshotRawFrames} keeps the frames for an MP4. Opt-in.
 */
public final class SwarmEmergenceLookSuite implements SnapshotSuite {

    static final int W = 70, H = 40;
    static final int SURGE_TICK = 30;
    static final int BORE_TICKS = 300, SPIRE_TICKS = 300;
    static final float CLOSE_CELLS = 34f;

    @Override public String id() { return "swarm-emergence"; }

    @Override public String label() { return "Swarm emergence: a bore surge and a spire launch, before and after"; }

    @Override public boolean inAll() { return false; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        SceneRegistries.installArmoury(context.projectRoot());
        List<SnapshotArtifact> out = new ArrayList<>();
        for (boolean on : new boolean[]{false, true}) {
            out.addAll(LookReview.record(context.modRoot(), boreSurge(on)));
            out.addAll(LookReview.record(context.modRoot(), spireLaunch(on)));
        }
        return out;
    }

    private static String when(boolean on) { return on ? "after" : "before"; }

    /** A maw on open ground with a rifle line far off down the field, surging once. */
    private static LookReview.Shot boreSurge(boolean on) {
        float[] centre = new float[2];
        return new LookReview.Shot("bore-surge-" + when(on), LookGround.verdant(), W, H,
                placement -> {
                    int x0 = placement.site().x0(), y0 = placement.site().y0();
                    SceneBuilder builder = placement.builder();
                    builder.squad("rifles").faction(Faction.MARINE).size(6).at(W - 6, H / 2).stationary()
                            .primary("weapon.field-rifle").health(5000f).done();
                    SceneWorld world = builder.build();
                    BattleSimulation sim = world.sim();
                    sim.swarmEmergence().setEnabled(on);
                    SwarmBore bore = BroodSpireScene.openMaw(sim, x0 + 12, y0 + H / 2, false);
                    centre[0] = bore.x() + 8f;
                    centre[1] = bore.y() + 0.5f;
                    placement.beforeTick(s -> {
                        if (s.getSimTickIndex() == SURGE_TICK) s.swarmBores().surgeNow(bore, s.getSimTickIndex());
                    });
                    return sim;
                },
                placement -> List.of(new LookReview.View("close",
                        sim -> new float[]{centre[0], centre[1], CLOSE_CELLS})),
                BORE_TICKS, 1, 4);
    }

    /** A matured maw's spire with a rifle line and a flak gun in its reach, launching on the bore's surge. */
    private static LookReview.Shot spireLaunch(boolean on) {
        return new LookReview.Shot("spire-launch-" + when(on), LookGround.verdant(), W, H,
                placement -> {
                    int x0 = placement.site().x0(), y0 = placement.site().y0();
                    SceneBuilder builder = placement.builder();
                    builder.squad("rifles").faction(Faction.MARINE).size(6).at(40, H / 2 + 2).stationary()
                            .primary("weapon.field-rifle").health(5000f).done();
                    SceneWorld world = builder.build();
                    BattleSimulation sim = world.sim();
                    sim.swarmEmergence().setEnabled(on);
                    SwarmBore bore = BroodSpireScene.openMaw(sim, x0 + 12, y0 + H / 2, true);
                    sim.spawn(MapTurret.create("flak", Faction.MARINE, BroodSpireScene.FLAK, x0 + 38, y0 + H / 2 + 2));
                    long spire = sim.broodSpires().spires().isEmpty() ? 0L : sim.broodSpires().spires().get(0).entityId;
                    if (spire != 0L) placement.subject("spire", spire);
                    placement.beforeTick(s -> {
                        if (s.getSimTickIndex() == SURGE_TICK) s.swarmBores().surgeNow(bore, s.getSimTickIndex());
                    });
                    System.out.printf("    [swarm-emergence] %s: %d spires%n", when(on), sim.broodSpires().spires().size());
                    return sim;
                },
                placement -> List.of(LookReview.View.follow("close", placement, CLOSE_CELLS, "spire")),
                SPIRE_TICKS, 1, 4);
    }
}
