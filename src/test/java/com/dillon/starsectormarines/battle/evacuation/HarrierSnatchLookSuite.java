package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.evacuation.flier.HarrierFlight;
import com.dillon.starsectormarines.battle.evacuation.flier.HarrierLook;
import com.dillon.starsectormarines.battle.evacuation.flier.HarrierPack;
import com.dillon.starsectormarines.battle.scene.LookGround;
import com.dillon.starsectormarines.battle.scene.LookReview;
import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.scene.SceneRegistries;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;

import java.util.List;

/**
 * Harriers snatching the living as the owner should judge it (`colony-breach-nouns.md`, Harrier, Snatching the
 * living): on real generated ground, a quiet maw on matured creep, a handful of colonists out on the field, and a pack
 * released over them. It sends carriers down to snatch them off their feet and fly them, alive, into the mouth. One
 * view holds the field and the bore, one rides with a carrier.
 */
public final class HarrierSnatchLookSuite implements SnapshotSuite {

    static final int W = 60, H = 40;
    /** Cells out from the bore the colonists stand. */
    static final int OUT = 20;
    static final int TICKS = 2700;

    @Override public String id() { return "harrier-snatch-look"; }

    @Override public String label() { return "Harrier snatch: a pack snatches colonists off their feet and flies them alive into the bore"; }

    @Override public boolean inAll() { return false; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        SceneRegistries.installArmoury(context.projectRoot());
        return LookReview.record(context.modRoot(), shot());
    }

    static LookReview.Shot shot() {
        int frames = TICKS / 6;
        HarrierPack[] pack = new HarrierPack[1];
        float[] last = {Float.NaN, Float.NaN};
        return new LookReview.Shot("harrier-snatch", LookGround.verdant(), W, H,
                placement -> {
                    int x0 = placement.site().x0(), y0 = placement.site().y0();
                    int bx = x0 + 15, by = y0 + H / 2, tx = bx + OUT, ty = by;
                    SceneBuilder builder = placement.builder();
                    BattleSimulation sim = builder.build().sim();
                    SwarmBore bore = HarrierPatrolScene.quietMaw(sim, bx, by);
                    for (int i = 0; i < 5; i++) {
                        sim.spawn(new EntitySpec("Colonist " + (i + 1), Faction.CIVILIAN, UnitType.CIVILIAN,
                                tx + (i % 3) * 3, ty - 3 + (i / 3) * 5).role(UnitRole.FLEE));
                    }
                    placement.beforeTick(s -> {
                        if (s.getSimTickIndex() == 30) pack[0] = s.swarmFliers().release(s, HarrierLook.DEFAULT, tx, ty - 6, 6, 0L);
                    });
                    placement.subject("bore", bore.entityId());
                    return sim;
                },
                placement -> List.of(LookReview.View.follow("field", placement, 44f, "bore"),
                        new LookReview.View("carrier", sim -> {
                            HarrierPack p = pack[0];
                            if (p != null) {
                                for (HarrierFlight m : p.members) {
                                    if (m.carry == null) continue;
                                    last[0] = m.body.x;
                                    last[1] = m.body.y;
                                    return new float[]{m.body.x, m.body.y, 24f};
                                }
                                if (Float.isNaN(last[0])) return new float[]{p.centreX(), p.centreY(), 24f};
                            }
                            return Float.isNaN(last[0]) ? new float[]{0f, 0f, 24f} : new float[]{last[0], last[1], 24f};
                        })),
                TICKS, 6, 3, new int[]{frames / 4, frames / 2, frames * 3 / 4, frames - 1});
    }
}
