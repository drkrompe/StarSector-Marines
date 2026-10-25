package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.creep.CreepField;
import com.dillon.starsectormarines.battle.scene.LookGround;
import com.dillon.starsectormarines.battle.scene.LookReview;
import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.scene.SceneRegistries;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;

import java.util.List;

/**
 * A nest's root growing (`colony-breach-nouns.md`, Nest): a satellite bore at maw on creep matured as weeks old,
 * whose roots are grown, roots a new outpost out on its creep as the recording starts; the clip follows the new root
 * as its vein digs out from the bore and then thickens into rope. The first still is before, the last after.
 */
public final class NestGrowthLookSuite implements SnapshotSuite {

    static final int W = 80, H = 50;
    /** Cells out from the bore the new outpost roots. */
    static final float OUT = 18f;
    /** Long enough for the root to dig the whole way and thicken, with a moment of rope at the end. */
    static final int TICKS = Math.round((OUT / NestTrails.DIG_CELLS_PER_SECOND + NestTrails.THICKEN_SECONDS + 3f)
            / BattleSimulation.TICK_DT);

    @Override public String id() { return "nest-growth-look"; }

    @Override public String label() { return "Nest: a new outpost's root digs out from the bore and thickens into rope"; }

    @Override public boolean inAll() { return false; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        SceneRegistries.installArmoury(context.projectRoot());
        return LookReview.record(context.modRoot(), shot());
    }

    static LookReview.Shot shot() {
        int frames = TICKS / 6;
        return new LookReview.Shot("nest-growth", LookGround.verdant(), W, H,
                placement -> {
                    int x0 = placement.site().x0(), y0 = placement.site().y0();
                    SceneBuilder builder = placement.builder();
                    builder.squad("marines").faction(Faction.MARINE).size(4).at(W - 3, 3).stationary()
                            .primary("weapon.field-rifle").done();
                    BattleSimulation sim = builder.build().sim();
                    SwarmBore bore = BroodSpireScene.openMaw(sim, x0 + W / 2 - 10, y0 + H / 2, true);
                    long outpost = rootOutpost(sim, bore);
                    placement.subject("bore", bore.entityId());
                    if (outpost != 0L) placement.subject("outpost", outpost);
                    System.out.printf("    [nest-growth] outpost %s, %d ticks%n", outpost != 0L ? "rooted" : "MISSING", TICKS);
                    return sim;
                },
                placement -> List.of(LookReview.View.follow("grow", placement, 40f, "bore", "outpost")),
                TICKS, 6, 3, new int[]{0, frames / 2, frames - 1});
    }

    /** Roots a fresh outpost of the bore's grade on open, creeped ground {@link #OUT} cells out; its id, or 0. */
    private static long rootOutpost(BattleSimulation sim, SwarmBore bore) {
        CreepField creep = sim.creepFieldIfPresent();
        for (int i = 0; i < 24; i++) {
            double a = i * Math.PI * 2 / 24;
            int x = (int) Math.floor(bore.x() + 0.5 + Math.cos(a) * OUT), y = (int) Math.floor(bore.y() + 0.5 + Math.sin(a) * OUT);
            if (!sim.getGrid().inBounds(x, y) || !sim.getGrid().isWalkable(x, y)) continue;
            if (creep == null || creep.coverageAt(x, y) < 200 || SwarmBores.nearBody(sim, x, y, 3f)) continue;
            long id = sim.spawn(SwarmBores.bodySpec("New Outpost", bore.tier().sporeColonyStructure(), x, y));
            bore.outposted(id, sim.getSimTickIndex());
            return id;
        }
        return 0L;
    }
}
