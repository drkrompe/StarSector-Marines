package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.creep.CreepField;
import com.dillon.starsectormarines.battle.fire.FireField;
import com.dillon.starsectormarines.battle.scene.LookGround;
import com.dillon.starsectormarines.battle.scene.LookReview;
import com.dillon.starsectormarines.battle.scene.SceneRegistries;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.forest.ForestBrush;
import com.dillon.starsectormarines.battle.world.gen.forest.ForestField;
import com.dillon.starsectormarines.battle.world.gen.forest.ForestPlan;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * The skitterers as the owner should judge them (`colony-breach-nouns.md`, Skitterer), on real generated ground, in two
 * shots. {@code swarm-workers}: a satellite bore at maw on creep matured weeks old, its brood spire grown off it and
 * its brood at work; the spire has just launched, so feeds go out to it; one of the bore's spore colonies is shot half
 * down, so it is mended; two Runners lie dead out on the creep, so they are hauled home. {@code swarm-workers-grove}: a
 * young nest on open grass with a grove ahead of its creep and a grass patch burnt out where the creep is about to
 * reach, played on until a skitterer sets out to re-seed the burn, then recorded through the stripping of the grove
 * and the pulp carried home. (Matured creep
 * would cover the burn's scar and kill the grove before the recording starts.) Both at play zoom and close on the nest.
 * The body plan drawn is whatever bone set stands in {@code mod/graphics/battle/rav-worker/}
 * ({@code art-source/rav-redesign/worker/export_worker_rig.py --option <look>}).
 */
public final class SwarmWorkerLookSuite implements SnapshotSuite {

    static final int W = 70, H = 40;
    /** The grove's centre off the young bore, cells, and its radius: across the mouth from the burn. */
    static final int GROVE_DX = 9, GROVE_DY = -6, GROVE_R = 4;
    /** Where the grass burn is lit off the young bore, cells (moved outward past any creep), and its radius. */
    static final int BURN_DX = -10, BURN_DY = 7;
    static final float BURN_R = 1.5f;
    /** No creep within this many cells of where the burn is lit, so the fire never reaches it; and how long it burns. */
    static final int BURN_CLEAR = 5, BURN_OUT_TICKS = 1800;
    /** Longest the young nest is played before recording, waiting for a grower to set out, ticks. */
    static final int GROVE_PRE_ROLL_TICKS = 12000;
    /** What each shot records at 15 frames a second: the nest 30 s, the grove a minute, re-seeding through stripping. */
    static final int RECORD_TICKS = 900, GROVE_RECORD_TICKS = 1800, EVERY_TICKS = 2;

    @Override public String id() { return "swarm-worker-look"; }

    @Override public String label() { return "Skitterers: a nest's workers busy round a maw, its spire, a grove and a burn"; }

    @Override public boolean inAll() { return false; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        SceneRegistries.installArmoury(context.projectRoot());
        List<SnapshotArtifact> out = new ArrayList<>(LookReview.record(context.modRoot(), shot()));
        out.addAll(LookReview.record(context.modRoot(), groveShot()));
        return out;
    }

    static LookReview.Shot shot() {
        return new LookReview.Shot("swarm-workers", LookGround.verdant(), W, H,
                placement -> {
                    int x0 = placement.site().x0(), y0 = placement.site().y0();
                    BattleSimulation sim = placement.builder().build().sim();
                    int bx = x0 + 26, by = y0 + H / 2;
                    SwarmBore bore = maw(sim, bx, by);
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
                    progress(placement, "swarm-workers");
                    return sim;
                },
                placement -> List.of(LookReview.View.followAtPlay(placement, "nest"),
                        LookReview.View.follow("close", placement, 18f, "nest")),
                RECORD_TICKS, EVERY_TICKS, 3, null);
    }

    static LookReview.Shot groveShot() {
        return new LookReview.Shot("swarm-workers-grove", LookGround.verdant(), W, H,
                placement -> {
                    int x0 = placement.site().x0(), y0 = placement.site().y0();
                    int bx = x0 + 30, by = y0 + H / 2;
                    BattleSimulation sim = placement.builder().doodads(grove(placement.map(), bx + GROVE_DX,
                            by + GROVE_DY)).build().sim();
                    SwarmBore bore = maw(sim, bx, by);
                    CreepField creep = sim.creepField();
                    sim.swarmWorkers().populate(sim);
                    // A grass fire goes out by itself, where a creep fire runs through the whole nest: it is lit
                    // clear of the young creep and burns out (only the fire stepping) before the battle starts, and
                    // the creep growing into its scar is refused there.
                    FireField fire = sim.fireField();
                    int burnX = bx + BURN_DX, burnY = by + BURN_DY;
                    for (int out = 0; out < 16 && creepWithin(creep, burnX, burnY, BURN_CLEAR); out++) {
                        burnX += Integer.signum(BURN_DX);
                        burnY += Integer.signum(BURN_DY);
                    }
                    fire.addHeat(burnX + 0.5f, burnY + 0.5f, BURN_R, 3000);
                    for (int t = 1; t <= BURN_OUT_TICKS && (t < 30 || fire.isActive()); t++) fire.tick(t);
                    // played until a skitterer sets out to re-seed the scar; the creep reaches the grove about half a
                    // minute later, so the stripping and the pulp fall in the recording too
                    int preRoll = 0;
                    boolean growing = false;
                    while (preRoll < GROVE_PRE_ROLL_TICKS && !growing) {
                        sim.advance(BattleSimulation.TICK_DT);
                        preRoll++;
                        for (SwarmWorkers.Worker w : sim.swarmWorkers().workers()) {
                            growing |= w.job() == SwarmWorkers.Job.REGROW;
                        }
                    }
                    placement.subject("nest", bore.entityId());
                    System.out.printf("    [swarm-workers-grove] played %d ticks first: burn lit at %d,%d off the "
                                    + "mouth, %d cells burnt, trees: %s%n", preRoll, burnX - bx, burnY - by,
                            fire.burntCount(), sim.creepVegetation().summary());
                    progress(placement, "swarm-workers-grove");
                    return sim;
                },
                placement -> List.of(LookReview.View.followAtPlay(placement, "nest"),
                        LookReview.View.follow("close", placement, 38f, "nest")),
                GROVE_RECORD_TICKS, EVERY_TICKS, 3, null);
    }

    /** A satellite bore at maw, asleep, so it neither surges nor collapses while it is filmed. */
    private static SwarmBore maw(BattleSimulation sim, int bx, int by) {
        SwarmBore bore = sim.swarmBores().open(sim, 1, false, bx, by);
        if (bore == null) throw new IllegalStateException("the look's ground refused a bore");
        for (int guard = 0; bore.tier() != BoreTier.MAW && guard < 64; guard++) {
            sim.swarmBores().feed(sim, bore, 1f);
        }
        sim.swarmBores().hold(bore);
        return bore;
    }

    /** Prints what the brood has done every five seconds of the recording, so a take can be judged before it is watched. */
    private static void progress(LookReview.Placement placement, String shot) {
        placement.beforeTick(new Consumer<>() {
            int tick;

            @Override
            public void accept(BattleSimulation s) {
                if (++tick % 150 == 0) {
                    System.out.printf("    [%s] %ds: %s%n", shot, tick / 30, s.swarmWorkers().summary(s));
                }
            }
        });
    }

    private static boolean creepWithin(CreepField creep, int x, int y, int r) {
        for (int dy = -r; dy <= r; dy++) {
            for (int dx = -r; dx <= r; dx++) {
                if (dx * dx + dy * dy <= r * r && creep.isCreeped(x + dx, y + dy)) return true;
            }
        }
        return false;
    }

    /** A few trees round {@code (cx, cy)}, stamped into the map before the battle is built over it. */
    private static List<Doodad> grove(MapResult map, int cx, int cy) {
        int w = map.grid.getWidth(), h = map.grid.getHeight();
        boolean[] paint = new boolean[w * h];
        for (int y = cy - GROVE_R; y <= cy + GROVE_R; y++) {
            for (int x = cx - GROVE_R; x <= cx + GROVE_R; x++) {
                if (x < 0 || y < 0 || x >= w || y >= h) continue;
                if ((x - cx) * (x - cx) + (y - cy) * (y - cy) <= GROVE_R * GROVE_R) paint[y * w + x] = true;
            }
        }
        ForestField field = ForestField.build(w, h, cx * 31L + cy, ForestPlan.Woodland.BROADLEAF, paint);
        List<Doodad> trees = new ArrayList<>();
        ForestBrush.stampTrees(field.trees(), map.grid, map.topology, trees, null, false);
        return trees;
    }
}
