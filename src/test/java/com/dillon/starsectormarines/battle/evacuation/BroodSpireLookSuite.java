package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.scene.LookGround;
import com.dillon.starsectormarines.battle.scene.LookReview;
import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.scene.SceneRegistries;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.turret.MapTurret;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;

import java.util.ArrayList;
import java.util.List;

/**
 * The brood spire as the owner should judge it (`colony-breach-nouns.md`, Brood spire): on real generated ground, a
 * satellite bore at maw with its creep matured round it as weeks old, the spire grown off it, a marine squad with
 * a flak position standing in its reach, and the bore's surge launching a pack off it. Framed at play zoom and
 * close; the first still is the spire before anything rises, the later ones the pack climbing out over it.
 */
public final class BroodSpireLookSuite implements SnapshotSuite {

    static final int W = 70, H = 40;

    @Override public String id() { return "brood-spire-look"; }

    @Override public String label() { return "Brood spire: on matured creep beside its bore, launching a pack"; }

    @Override public boolean inAll() { return false; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        SceneRegistries.installArmoury(context.projectRoot());
        return new ArrayList<>(LookReview.record(context.modRoot(), shot()).stream()
                .filter(a -> !a.relativePath().toString().endsWith(".gif")).toList());
    }

    static LookReview.Shot shot() {
        return new LookReview.Shot("brood-spire", LookGround.verdant(), W, H,
                placement -> {
                    int x0 = placement.site().x0(), y0 = placement.site().y0();
                    SceneBuilder builder = placement.builder();
                    builder.squad("marines").faction(Faction.MARINE).size(6).at(44, H / 2 + 2).stationary()
                            .primary("weapon.field-rifle").done();
                    BattleSimulation sim = builder.build().sim();
                    SwarmBore bore = BroodSpireScene.openMaw(sim, x0 + 14, y0 + H / 2, true);
                    sim.spawn(MapTurret.create("flak", Faction.MARINE, BroodSpireScene.FLAK, x0 + 41, y0 + H / 2 + 2));
                    placement.beforeTick(s -> {
                        if (s.getSimTickIndex() == 30) s.swarmBores().surgeNow(bore, s.getSimTickIndex());
                    });
                    if (!sim.broodSpires().spires().isEmpty()) {
                        placement.subject("spire", sim.broodSpires().spires().get(0).entityId);
                    }
                    System.out.printf("    [brood-spire] %d spires%n", sim.broodSpires().spires().size());
                    return sim;
                },
                placement -> List.of(LookReview.View.followAtPlay(placement, "spire"),
                        LookReview.View.follow("close", placement, 22f, "spire")),
                210, 6, 3, new int[]{0, 12, 22});
    }
}
