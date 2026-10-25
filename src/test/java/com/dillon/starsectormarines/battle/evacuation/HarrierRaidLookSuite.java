package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.evacuation.flier.HarrierPack;
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
 * A deep raid as the owner should judge it (`colony-breach-nouns.md`, Brood spire, Raids): on real generated ground, a
 * quiet maw's spire, a rifle line with flak well out from it, and one marine alone behind the line. Four go up off the
 * spire, swing wide of the flak, dive on him and fly home the same way to land. The camera rides with the raid.
 */
public final class HarrierRaidLookSuite implements SnapshotSuite {

    static final int W = 150, H = 80;
    static final int TICKS = 3500;

    @Override public String id() { return "harrier-raid-look"; }

    @Override public String label() { return "Harrier raid: four swing wide of the flak to a lone marine behind the line"; }

    @Override public boolean inAll() { return false; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        SceneRegistries.installArmoury(context.projectRoot());
        return LookReview.record(context.modRoot(), shot());
    }

    static LookReview.Shot shot() {
        int frames = TICKS / 6;
        BroodSpires.Spire[] spire = new BroodSpires.Spire[1];
        float[] last = {Float.NaN, Float.NaN};
        return new LookReview.Shot("harrier-raid", LookGround.verdant(), W, H,
                placement -> {
                    int x0 = placement.site().x0(), y0 = placement.site().y0();
                    SceneBuilder builder = placement.builder();
                    builder.squad("line").faction(Faction.MARINE).size(6).at(90, H / 2).primary("weapon.field-rifle")
                            .stationary().done();
                    builder.squad("rear").faction(Faction.MARINE).size(1).at(138, H / 2).primary("weapon.field-rifle")
                            .stationary().done();
                    BattleSimulation sim = builder.build().sim();
                    HarrierPatrolScene.quietMaw(sim, x0 + 15, y0 + H / 2);
                    sim.spawn(MapTurret.create("flak", Faction.MARINE, BroodSpireScene.FLAK, x0 + 92, y0 + H / 2));
                    spire[0] = sim.broodSpires().spires().get(0);
                    spire[0].lastRaidTick = -BroodSpires.raidGapTicks(spire[0].entityId, 0);
                    placement.subject("spire", spire[0].entityId);
                    return sim;
                },
                placement -> List.of(new LookReview.View("raid", sim -> {
                    HarrierPack p = spire[0].patrol();
                    if (p != null && !p.members.isEmpty()) {
                        last[0] = p.centreX();
                        last[1] = p.centreY();
                    } else if (Float.isNaN(last[0])) {
                        last[0] = spire[0].x + 0.5f;
                        last[1] = spire[0].y + 0.5f;
                    }
                    return new float[]{last[0], last[1], 40f};
                })),
                TICKS, 6, 3, new int[]{frames / 5, frames * 2 / 5, frames * 3 / 5, frames - 1});
    }
}
