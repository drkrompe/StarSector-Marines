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

import java.util.List;

/**
 * A fresh bloom growing its air (`colony-breach-nouns.md`, Brood spire: a fresh bloom grows them by eating): a satellite
 * bore just opened on new creep, fed a colonist every few seconds as its harvest would, and a marine line with flak off
 * in the corner. It reaches maw, and once its creep holds ground for one its spire's root digs out and thickens, the
 * spire rises at its end in a burst of creep, and before long it puts its first Harriers up. The first still is
 * before, the last after.
 */
public final class FedSpireLookSuite implements SnapshotSuite {

    static final int W = 100, H = 64;
    static final int TICKS = 2100;

    @Override public String id() { return "fed-spire-look"; }

    @Override public String label() { return "Fed spire: a fresh bore fed to maw raises a brood spire, root first, which then flies"; }

    @Override public boolean inAll() { return false; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        SceneRegistries.installArmoury(context.projectRoot());
        return LookReview.record(context.modRoot(), shot());
    }

    static LookReview.Shot shot() {
        int frames = TICKS / 6;
        float[] centre = new float[2];
        boolean[] latched = new boolean[1];
        SwarmBore[] bore = new SwarmBore[1];
        int feedTicks = Math.round(FedSpireScene.FEED_SECONDS / BattleSimulation.TICK_DT);
        return new LookReview.Shot("fed-spire", LookGround.verdant(), W, H,
                placement -> {
                    int x0 = placement.site().x0(), y0 = placement.site().y0();
                    SceneBuilder builder = placement.builder();
                    builder.squad("marines").faction(Faction.MARINE).size(4).at(x0 + W - 4, y0 + 4).stationary()
                            .primary("weapon.field-rifle").done();
                    BattleSimulation sim = builder.build().sim();
                    bore[0] = sim.swarmBores().open(sim, 1, false, x0 + W / 2 - 12, y0 + H / 2);
                    sim.creepField();
                    sim.spawn(MapTurret.create("flak", Faction.MARINE, BroodSpireScene.FLAK, x0 + W - 6, y0 + 6));
                    centre[0] = bore[0].x() + 0.5f;
                    centre[1] = bore[0].y() + 0.5f;
                    int[] fed = new int[1];
                    placement.beforeTick(s -> {
                        int tick = s.getSimTickIndex();
                        if (tick > 0 && tick % feedTicks == 0 && fed[0] < FedSpireScene.FEEDS) {
                            s.swarmBores().feed(s, bore[0], 1f);
                            fed[0]++;
                        }
                    });
                    placement.subject("bore", bore[0].entityId());
                    return sim;
                },
                placement -> List.of(new LookReview.View("rise", s -> {
                    // Between the bore and its spire's cell, once its root has begun: both in frame.
                    if (!latched[0] && !s.broodSpires().rising().isEmpty()) {
                        BroodSpires.Rising r = s.broodSpires().rising().get(0);
                        centre[0] = (centre[0] + r.x() + 0.5f) / 2f;
                        centre[1] = (centre[1] + r.y() + 0.5f) / 2f;
                        latched[0] = true;
                    }
                    return new float[]{centre[0], centre[1], 72f};
                })),
                TICKS, 6, 3, new int[]{0, frames * 3 / 5, frames * 2 / 3, frames * 3 / 4, frames - 1});
    }
}
