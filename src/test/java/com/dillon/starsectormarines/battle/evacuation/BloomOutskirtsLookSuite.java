package com.dillon.starsectormarines.battle.evacuation;

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

import java.util.ArrayList;
import java.util.List;

/**
 * The bloom coming in from the outskirts as the owner should judge it (`colony-breach-nouns.md`, Eruption site,
 * Budding, Harvest: forager packs): on real generated ground, a fissure on the edge beside a landed fire team and the
 * colony's people further in. Its pulses run past the team into the town after them, drag them home, and fed, the
 * bore buds: a root digs out a block or two further in and a new bore opens at its end. The view holds the bore, the
 * team and the town.
 */
public final class BloomOutskirtsLookSuite implements SnapshotSuite {

    static final int W = 110, H = 62;
    static final int TICKS = 4200;

    @Override public String id() { return "bloom-outskirts-look"; }

    @Override public String label() { return "Bloom from the outskirts: a fissure's pulses forage into the town, and fed, it buds further in"; }

    @Override public boolean inAll() { return false; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        SceneRegistries.installArmoury(context.projectRoot());
        return LookReview.record(context.modRoot(), shot());
    }

    static LookReview.Shot shot() {
        int frames = TICKS / 6;
        float[] centre = new float[2];
        return new LookReview.Shot("bloom-outskirts", LookGround.verdant(), W, H,
                placement -> {
                    int x0 = placement.site().x0(), y0 = placement.site().y0();
                    SceneBuilder builder = placement.builder();
                    builder.squad("team").faction(Faction.MARINE).size(4).at(14, H - 10).stationary()
                            .primary("weapon.field-rifle").done();
                    BattleSimulation sim = builder.build().sim();
                    SwarmBore bore = sim.swarmBores().open(sim, 1, false, x0 + 12, y0 + H / 2);
                    sim.creepField();
                    sim.configureSwarmFromBores(20261025L);
                    for (int i = 0; i < 10; i++) {
                        sim.spawn(new EntitySpec("Colonist " + (i + 1), Faction.CIVILIAN, UnitType.CIVILIAN,
                                x0 + 60 + (i % 5) * 4, y0 + H / 2 - 6 + (i / 5) * 12).role(UnitRole.FLEE));
                    }
                    List<int[]> places = new ArrayList<>();
                    for (int x = x0 + 20; x < x0 + W - 6; x += 8) {
                        for (int y = y0 + 8; y < y0 + H - 6; y += 10) places.add(new int[]{x, y, 0});
                    }
                    sim.swarmBores().budInward(places, x0 + W - 4, y0 + H / 2, 14, 2);
                    centre[0] = x0 + W / 2f;
                    centre[1] = y0 + H / 2f;
                    placement.subject("bore", bore.entityId());
                    return sim;
                },
                placement -> List.of(new LookReview.View("field", s -> new float[]{centre[0], centre[1], 100f})),
                TICKS, 6, 3, new int[]{frames / 6, frames / 3, frames * 3 / 4, frames * 7 / 8, frames - 1});
    }
}
