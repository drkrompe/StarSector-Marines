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

import java.util.List;

/**
 * A nest growing in play (`colony-breach-nouns.md`, Nest, Roots grow): a satellite bore one tier short of maw on creep
 * matured as weeks old. A second into the clip it is fed across into maw: its new mouth colonies are begun, and its
 * creep, holding more outposts at maw, holds a cell for the next. Each new growth's root digs out from the bore first
 * and thickens, and only then does the colony or the outpost rise at its end. The first still is before, the last
 * after.
 */
public final class NestGrowthLookSuite implements SnapshotSuite {

    static final int W = 80, H = 50;
    /** The tick the bore is fed and the outpost's cell is held. */
    static final int BEGIN = 30;
    /** Long enough for the creep to hold its next outpost's cell, the hold, and a moment of the outpost standing. */
    static final int TICKS = BEGIN + Math.round((CreepOutposts.HOLD_SECONDS + 14f) / BattleSimulation.TICK_DT);

    @Override public String id() { return "nest-growth-look"; }

    @Override public String label() { return "Nest: new growths' roots dig out from the bore and the growths rise where they end"; }

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
        return new LookReview.Shot("nest-growth", LookGround.verdant(), W, H,
                placement -> {
                    int x0 = placement.site().x0(), y0 = placement.site().y0();
                    SceneBuilder builder = placement.builder();
                    builder.squad("marines").faction(Faction.MARINE).size(4).at(W - 3, 3).stationary()
                            .primary("weapon.field-rifle").done();
                    BattleSimulation sim = builder.build().sim();
                    bore[0] = sim.swarmBores().open(sim, 1, false, x0 + W / 2 - 10, y0 + H / 2);
                    for (int guard = 0; bore[0].tier() != BoreTier.BORE && guard < 64; guard++) sim.swarmBores().feed(sim, bore[0], 1f);
                    sim.creepField();
                    sim.matureCreep();
                    centre[0] = bore[0].x() + 0.5f;
                    centre[1] = bore[0].y() + 0.5f;
                    placement.beforeTick(s -> {
                        if (s.getSimTickIndex() != BEGIN) return;
                        for (int guard = 0; bore[0].tier() != BoreTier.MAW && guard < 64; guard++) s.swarmBores().feed(s, bore[0], 1f);
                    });
                    placement.subject("bore", bore[0].entityId());
                    return sim;
                },
                placement -> List.of(new LookReview.View("grow", s -> {
                    // Between the bore and its next outpost's cell, once the creep holds one: both in frame.
                    if (!latched[0] && bore[0] != null) {
                        int[] cell = s.creepOutposts().pendingCell(bore[0].entityId());
                        if (cell != null) {
                            centre[0] = (centre[0] + cell[0] + 0.5f) / 2f;
                            centre[1] = (centre[1] + cell[1] + 0.5f) / 2f;
                            latched[0] = true;
                        }
                    }
                    return new float[]{centre[0], centre[1], 72f};
                })),
                TICKS, 6, 3, new int[]{0, frames / 4, frames / 2, frames * 3 / 4, frames - 1});
    }
}
