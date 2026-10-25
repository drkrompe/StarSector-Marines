package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.scene.LookGround;
import com.dillon.starsectormarines.battle.scene.LookReview;
import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.scene.SceneRegistries;
import com.dillon.starsectormarines.battle.scene.SceneWorld;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.turret.MapTurret;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.ops.battleview.BattleSoundLog;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The brood spire's launch and collapse against the fight, for the owner rather than for a verdict: a spire grown off
 * a matured satellite bore, a rifle line with a flak position in its reach, the bore's surge launching a pack over
 * the line, and the spire destroyed some seconds later. Play zoom, a frame every tick, with every sound the battle
 * screen would start logged beside the frames ({@link BattleSoundLog}, {@code spire-sound.jsonl} under
 * {@code -Dsnapshot.swarmSound.logDir}, default {@code build/swarm-sound}) for an offline mix. Opt-in.
 */
public final class SpireSoundLookSuite implements SnapshotSuite {

    static final int W = 70, H = 40;
    static final int TICKS = 810;
    static final int SURGE_TICK = 45, KILL_TICK = 600;
    static final int[] STILLS = {40, 90, 300, 610, 700};

    @Override public String id() { return "spire-sound"; }

    @Override public String label() { return "Spire sound: a brood spire launching a pack over a gun line, then destroyed, with the sounds logged"; }

    @Override public boolean inAll() { return false; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        SceneRegistries.installArmoury(context.projectRoot());
        Path logs = Path.of(System.getProperty("snapshot.swarmSound.logDir",
                context.projectRoot().resolve("build/swarm-sound").toString()));
        return new ArrayList<>(LookReview.record(context.modRoot(), shot(logs.resolve("spire-sound.jsonl"))));
    }

    private static LookReview.Shot shot(Path log) {
        return new LookReview.Shot("spire-sound", LookGround.verdant(), W, H,
                placement -> {
                    int x0 = placement.site().x0(), y0 = placement.site().y0();
                    SceneBuilder builder = placement.builder();
                    builder.squad("rifles").faction(Faction.MARINE).size(6).at(40, H / 2 + 2).stationary()
                            .primary("weapon.field-rifle").health(600f).done();
                    SceneWorld world = builder.build();
                    BattleSimulation sim = world.sim();
                    SwarmBore bore = BroodSpireScene.openMaw(sim, x0 + 12, y0 + H / 2, true);
                    sim.spawn(MapTurret.create("flak", Faction.MARINE, BroodSpireScene.FLAK, x0 + 38, y0 + H / 2 + 2));
                    placement.subject("line", world.members("rifles")[0]);
                    long spire = sim.broodSpires().spires().isEmpty() ? 0L : sim.broodSpires().spires().get(0).entityId;
                    if (spire != 0L) placement.subject("spire", spire);
                    placement.beforeTick(s -> {
                        int tick = s.getSimTickIndex();
                        if (tick == SURGE_TICK) s.swarmBores().surgeNow(bore, tick);
                        if (tick == KILL_TICK && spire != 0L && s.world().isAlive(spire)) {
                            s.applyDamage(spire, 1_000_000f, 1_000_000f);
                        }
                    });
                    BattleSoundLog.attach(placement, LookReview.View.followAtPlay(placement, "line", "spire"), log);
                    System.out.printf("    [spire-sound] %d spires%n", sim.broodSpires().spires().size());
                    return sim;
                },
                placement -> List.of(LookReview.View.followAtPlay(placement, "line", "spire")),
                TICKS, 1, 2, STILLS);
    }
}
