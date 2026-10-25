package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.evacuation.flier.HarrierPack;
import com.dillon.starsectormarines.battle.scene.LookGround;
import com.dillon.starsectormarines.battle.scene.LookReview;
import com.dillon.starsectormarines.battle.scene.SceneRegistries;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.turret.MapTurret;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;

import java.util.List;

/**
 * A brood spire's patrol as the owner should judge it (`colony-breach-nouns.md`, Brood spire, Patrols): on real
 * generated ground, a satellite bore at maw on matured creep with its spire, quiet, the flak that answers fliers far
 * off. Three of its roost climb off the hooks, wheel round their loop over the nest and come home to land. One view
 * holds the whole nest, one rides with the patrol.
 */
public final class HarrierPatrolLookSuite implements SnapshotSuite {

    static final int W = 90, H = 80;
    /** The patrol's loop with a moment either side: up, round and down. */
    static final int TICKS = 1900;

    @Override public String id() { return "harrier-patrol-look"; }

    @Override public String label() { return "Harrier patrol: three off a quiet spire's roost fly a loop over the nest and land"; }

    @Override public boolean inAll() { return false; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        SceneRegistries.installArmoury(context.projectRoot());
        return LookReview.record(context.modRoot(), shot());
    }

    static LookReview.Shot shot() {
        int frames = TICKS / 6;
        BroodSpires.Spire[] spire = new BroodSpires.Spire[1];
        return new LookReview.Shot("harrier-patrol", LookGround.verdant(), W, H,
                placement -> {
                    int x0 = placement.site().x0(), y0 = placement.site().y0();
                    BattleSimulation sim = placement.builder().build().sim();
                    SwarmBore bore = HarrierPatrolScene.quietMaw(sim, x0 + W / 2, y0 + H / 2);
                    spire[0] = sim.broodSpires().spires().get(0);
                    spire[0].lastPatrolTick = -BroodSpires.patrolGapTicks(spire[0].entityId, 0);
                    // The flak across the bore from the spire and well out of its wake.
                    double a = Math.atan2(bore.y() - spire[0].y, bore.x() - spire[0].x);
                    int fx = (int) Math.max(1, Math.min(sim.getGrid().getWidth() - 2, bore.x() + Math.cos(a) * 60));
                    int fy = (int) Math.max(1, Math.min(sim.getGrid().getHeight() - 2, bore.y() + Math.sin(a) * 60));
                    sim.spawn(MapTurret.create("flak", Faction.MARINE, BroodSpireScene.FLAK, fx, fy));
                    placement.subject("bore", bore.entityId());
                    placement.subject("spire", spire[0].entityId);
                    System.out.printf("    [harrier-patrol] spire at %d,%d, flak at %d,%d%n", spire[0].x, spire[0].y, fx, fy);
                    return sim;
                },
                placement -> List.of(LookReview.View.follow("nest", placement, 78f, "bore"),
                        new LookReview.View("patrol", sim -> {
                            HarrierPack p = spire[0].patrol();
                            if (p == null || p.members.isEmpty()) return new float[]{spire[0].x + 0.5f, spire[0].y + 0.5f, 30f};
                            return new float[]{p.centreX(), p.centreY(), 30f};
                        })),
                TICKS, 6, 3, new int[]{0, frames / 3, frames * 2 / 3, frames - 8});
    }
}
