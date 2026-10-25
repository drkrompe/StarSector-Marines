package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.scene.LookGround;
import com.dillon.starsectormarines.battle.scene.LookReview;
import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.scene.SceneRegistries;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;

import java.util.ArrayList;
import java.util.List;

/**
 * The mother bore as the owner should judge it (`heart-nouns.md`, Mother bore): on real generated ground, the
 * heart's mother at maw with her creep matured round her, an ordinary satellite bore beside her for contrast, her
 * Matriarch standing at the rim and a marine squad for scale. Framed at play zoom and close on the mother.
 */
public final class MotherBoreLookSuite implements SnapshotSuite {

    static final int W = 64, H = 36;

    @Override public String id() { return "mother-bore-look"; }

    @Override public String label() { return "Mother bore: on matured creep, her Matriarch and a satellite bore beside her"; }

    @Override public boolean inAll() { return false; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        SceneRegistries.installArmoury(context.projectRoot());
        return new ArrayList<>(LookReview.record(context.modRoot(), shot()).stream()
                .filter(a -> !a.relativePath().toString().endsWith(".gif")).toList());
    }

    static LookReview.Shot shot() {
        return new LookReview.Shot("mother-bore", LookGround.verdant(), W, H,
                placement -> {
                    int x0 = placement.site().x0(), y0 = placement.site().y0();
                    SceneBuilder builder = placement.builder();
                    builder.squad("marines").faction(Faction.MARINE).size(6).at(W - 6, H / 2 + 3).stationary()
                            .primary("weapon.field-rifle").done();
                    BattleSimulation sim = builder.build().sim();
                    SwarmBore mother = sim.swarmBores().open(sim, 0, true, x0 + 20, y0 + H / 2);
                    SwarmBore satellite = sim.swarmBores().open(sim, 1, false, x0 + 42, y0 + H / 2 - 4);
                    if (mother == null || satellite == null) throw new IllegalStateException("open ground refused a bore");
                    for (SwarmBore bore : List.of(mother, satellite)) {
                        for (int guard = 0; bore.tier() != BoreTier.MAW && guard < 64; guard++) {
                            sim.swarmBores().feed(sim, bore, 1f);
                        }
                    }
                    sim.creepField();
                    sim.matureCreep();
                    sim.configureSwarmFromBores(20261025L);
                    long matriarch = SwarmStrains.spawn(sim, "Matriarch", UnitType.SWARM_MATRIARCH, x0 + 26, y0 + H / 2 + 3);
                    placement.subject("mother", mother.entityId());
                    placement.subject("matriarch", matriarch);
                    return sim;
                },
                placement -> List.of(LookReview.View.followAtPlay(placement, "mother"),
                        LookReview.View.follow("close", placement, 18f, "mother")),
                24, 6, 1, new int[]{0});
    }
}
