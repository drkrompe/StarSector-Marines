package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.scene.LookGround;
import com.dillon.starsectormarines.battle.scene.LookReview;
import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.scene.SceneRegistries;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;

import java.util.ArrayList;
import java.util.List;

/**
 * A bore's nest as the owner should judge it (`colony-breach-nouns.md`, Nest): on real generated ground, a satellite
 * bore at maw with its creep matured round it as weeks old, left half a minute so its outposts root, its spire grown
 * with a full roost, and nothing yet come to fight it. Framed at play zoom on the bore and close on its mouth.
 */
public final class NestLookSuite implements SnapshotSuite {

    static final int W = 96, H = 60;
    /** Ticks the nest is left to itself before the stills, long enough for its outposts to root. */
    static final int SETTLE_TICKS = Math.round(35f / BattleSimulation.TICK_DT);

    @Override public String id() { return "nest-look"; }

    @Override public String label() { return "Nest: a maw's crescent mouth, its roots to its outposts and spire, its roost"; }

    @Override public boolean inAll() { return false; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        SceneRegistries.installArmoury(context.projectRoot());
        return new ArrayList<>(LookReview.record(context.modRoot(), shot()).stream()
                .filter(a -> !a.relativePath().toString().endsWith(".gif")).toList());
    }

    static LookReview.Shot shot() {
        return new LookReview.Shot("nest", LookGround.verdant(), W, H,
                placement -> {
                    int x0 = placement.site().x0(), y0 = placement.site().y0();
                    SceneBuilder builder = placement.builder();
                    // Far off the nest's edge, so the bore has something to send at without anyone in its reach.
                    builder.squad("marines").faction(Faction.MARINE).size(4).at(W - 3, 3).stationary()
                            .primary("weapon.field-rifle").done();
                    BattleSimulation sim = builder.build().sim();
                    SwarmBore bore = BroodSpireScene.openMaw(sim, x0 + W / 2 - 6, y0 + H / 2, true);
                    for (int i = 0; i < SETTLE_TICKS; i++) sim.advance(BattleSimulation.TICK_DT);
                    placement.subject("bore", bore.entityId());
                    System.out.printf("    [nest] %d colonies, %d outposts, %d spires%n", bore.colonies().size(),
                            bore.outposts().size(), sim.broodSpires().spires().size());
                    return sim;
                },
                placement -> List.of(LookReview.View.followAtPlay(placement, "bore"),
                        LookReview.View.follow("close", placement, 26f, "bore")),
                30, 6, 1, new int[]{0});
    }
}
