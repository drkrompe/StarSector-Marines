package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.scene.LookGround;
import com.dillon.starsectormarines.battle.scene.LookReview;
import com.dillon.starsectormarines.battle.scene.SceneRegistries;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;

import java.util.ArrayList;
import java.util.List;

/**
 * The skitterers as the owner should judge them (`colony-breach-nouns.md`, Skitterer): on real generated ground, a
 * satellite bore at maw on creep matured weeks old, its brood spire grown off it, and its brood at work. The spire
 * has just launched, so feeds go out to it; one of the bore's spore colonies is shot half down, so it is mended; two
 * Runners lie dead out on the creep, so they are hauled home. Framed at play zoom and close on the nest. The body plan
 * drawn is whatever bone set stands in {@code mod/graphics/battle/rav-worker/}
 * ({@code art-source/rav-redesign/worker/export_worker_rig.py --option <look>}).
 */
public final class SwarmWorkerLookSuite implements SnapshotSuite {

    static final int W = 70, H = 40;

    @Override public String id() { return "swarm-worker-look"; }

    @Override public String label() { return "Skitterers: a nest's workers busy round a maw and its spire on old creep"; }

    @Override public boolean inAll() { return false; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        SceneRegistries.installArmoury(context.projectRoot());
        return new ArrayList<>(LookReview.record(context.modRoot(), shot()).stream()
                .filter(a -> !a.relativePath().toString().endsWith(".gif")).toList());
    }

    static LookReview.Shot shot() {
        return new LookReview.Shot("swarm-workers", LookGround.verdant(), W, H,
                placement -> {
                    int x0 = placement.site().x0(), y0 = placement.site().y0();
                    BattleSimulation sim = placement.builder().build().sim();
                    int bx = x0 + 26, by = y0 + H / 2;
                    SwarmBore bore = sim.swarmBores().open(sim, 1, false, bx, by);
                    if (bore == null) throw new IllegalStateException("the look's ground refused a bore");
                    for (int guard = 0; bore.tier() != BoreTier.MAW && guard < 64; guard++) {
                        sim.swarmBores().feed(sim, bore, 1f);
                    }
                    sim.swarmBores().hold(bore);
                    sim.creepField();
                    sim.matureCreep();
                    if (!bore.colonies().isEmpty()) {
                        long colony = bore.colonies().get(0);
                        sim.world().setHp(colony, sim.world().maxHp(colony) * 0.45f);
                    }
                    for (BroodSpires.Spire s : sim.broodSpires().spires()) s.lastReleaseTick = 0;
                    for (int[] at : new int[][]{{bx + 9, by + 4}, {bx - 7, by - 6}}) {
                        long dead = SwarmStrains.spawn(sim, "Fallen Runner", UnitType.SWARM_RUNNER, at[0], at[1]);
                        sim.applyDamage(dead, 1e6f, 1e6f);
                    }
                    placement.subject("nest", bore.entityId());
                    System.out.printf("    [swarm-workers] %d skitterers, %d spires%n",
                            sim.swarmWorkers().workers().size(), sim.broodSpires().spires().size());
                    return sim;
                },
                placement -> List.of(LookReview.View.followAtPlay(placement, "nest"),
                        LookReview.View.follow("close", placement, 18f, "nest")),
                360, 6, 3, new int[]{15, 35, 59});
    }
}
