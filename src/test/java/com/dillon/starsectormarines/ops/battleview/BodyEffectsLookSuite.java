package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.scene.LookGround;
import com.dillon.starsectormarines.battle.scene.LookReview;
import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.scene.SceneRegistries;
import com.dillon.starsectormarines.battle.scene.SceneWorld;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.BodyPileCap;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.NightLighting;
import com.dillon.starsectormarines.marine.SpecialEquipmentRegistry;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Per-quad body effects (law 27) on real generated ground through the look preset, drawn on the GPU, which is the
 * only drain that draws them: {@code lit-firefight}, a rifle section with grenades against a militia picket at night,
 * where every muzzle flash and blast lights the bodies round it and a hit flashes the body it lands on;
 * {@code burning-dead}, a row of the dead in grass a fire sweeps over, glowing with embers as it burns and charred
 * once it has burnt; {@code retiring-dead}, a pile past the body cap's limit for its ground, whose oldest dissolve
 * with a hot edge as the cap retires them. Opt-in: a look question, not part of {@code all}.
 */
public final class BodyEffectsLookSuite implements SnapshotSuite {

    private static final NightLighting NIGHT = new NightLighting(0.78f, List.of(), Set.of());

    @Override public String id() { return "body-effects-look"; }

    @Override public String label() { return "Body effects: lit by the fight, burnt by fire, dissolved by the pile cap"; }

    @Override public boolean inAll() { return false; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        SceneRegistries.installArmoury(context.projectRoot());
        String only = System.getProperty("snapshot.bodyEffects.shots", "lit-firefight,burning-dead,retiring-dead");
        List<SnapshotArtifact> out = new ArrayList<>();
        if (only.contains("lit-firefight")) out.addAll(LookReview.record(context.modRoot(), litFirefight()));
        if (only.contains("burning-dead")) out.addAll(LookReview.record(context.modRoot(), burningDead()));
        if (only.contains("retiring-dead")) out.addAll(LookReview.record(context.modRoot(), retiringDead()));
        return out;
    }

    /** Six marines with rifles and grenades walking onto six militia 22 cells off, at night. */
    static LookReview.Shot litFirefight() {
        return new LookReview.Shot("lit-firefight", LookGround.verdant(), 48, 28, placement -> {
            float[] own = placement.openNear(10f, 14f);
            float[] picket = placement.openNear(32f, 14f);
            int x0 = placement.site().x0(), y0 = placement.site().y0();
            int px = (int) picket[0], py = (int) picket[1];
            SceneBuilder builder = placement.builder();
            builder.squad("section").faction(Faction.MARINE).size(6)
                    .at((int) own[0] - x0, (int) own[1] - y0).primary("weapon.field-rifle")
                    .special(SpecialEquipmentRegistry.FRAG_GRENADE_ID, 2)
                    .assigned(id -> ObjectiveAssignment.attackMove(id, px, py)).done();
            builder.squad("picket").faction(Faction.DEFENDER).type(UnitType.MILITIA).size(6)
                    .at(px - x0, py - y0).primary("weapon.smg").stationary().done();
            SceneWorld world = builder.build();
            BattleSimulation sim = world.sim();
            sim.getTopology().setNightLighting(NIGHT);
            placement.subject("section", world.members("section")[0]);
            placement.subject("picket", world.members("picket")[0]);
            return sim;
        }, placement -> List.of(LookReview.View.follow("close", placement, 18f, "picket")), 900, 2, 6);
    }

    /**
     * Ten militia dead in a row on grass, each cell set alight in turn from the west, as a fire sweeping east would:
     * embers while each burns, char once it has burnt.
     */
    static LookReview.Shot burningDead() {
        return LookReview.Shot.of("burning-dead", 40, 24, placement -> {
            BattleSimulation sim = placement.builder().build().sim();
            List<int[]> cells = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                float[] at = placement.openNear(10f + i * 1.6f, 12f + (i % 3) - 1);
                long id = sim.spawn(new EntitySpec("Dead " + i, Faction.DEFENDER, UnitType.MILITIA,
                        (int) at[0], (int) at[1]));
                sim.applyDamage(id, 1_000_000f, 1000f);
                cells.add(new int[]{(int) at[0], (int) at[1]});
            }
            sim.fireField().setWind(1f, 0f, 0.4f);
            placement.beforeTick(s -> {
                int since = s.getSimTickIndex() - 30;
                int k = since / 45;
                if (since >= 0 && since % 45 == 0 && k < cells.size()) {
                    int[] c = cells.get(k);
                    s.fireField().addHeat(c[0], c[1], 3000);
                }
            });
            return sim;
        }, placement -> List.of(LookReview.View.close("close", placement.site().x(17f), placement.site().y(12f),
                18f)), 1200);
    }

    /**
     * 64 dead filling one of the pile cap's eight-cell areas row by row, then 16 more on its last two rows: past the
     * cap's limit of 64 an area, so the 16 oldest, the first two rows, are retired as the rest fall, with nothing
     * lying over them.
     */
    static LookReview.Shot retiringDead() {
        return LookReview.Shot.of("retiring-dead", 32, 20, placement -> {
            BattleSimulation sim = placement.builder().build().sim();
            int[] area = pileArea(placement);
            int cells = BodyPileCap.AREA_CELLS * BodyPileCap.AREA_CELLS;
            List<Long> bodies = new ArrayList<>();
            for (int i = 0; i < cells; i++) bodies.add(spawnBody(sim, area, i));
            placement.beforeTick(s -> {
                int t = s.getSimTickIndex();
                if (t == SECOND_WAVE_TICK) {
                    for (int i = cells - SECOND_WAVE; i < cells; i++) bodies.add(spawnBody(s, area, i));
                }
                if (t % 2 != 0) return;
                int k = t < SECOND_WAVE_TICK ? t / 2 : cells + (t - SECOND_WAVE_TICK) / 2;
                boolean firstWave = t < SECOND_WAVE_TICK;
                if (k < bodies.size() && firstWave == (k < cells)) s.applyDamage(bodies.get(k), 1_000_000f, 1000f);
            });
            return sim;
        }, placement -> {
            int[] area = pileArea(placement);
            float half = BodyPileCap.AREA_CELLS / 2f;
            return List.of(LookReview.View.close("close", area[0] + half, area[1] + half, 14f));
        }, 900);
    }

    private static final int SECOND_WAVE_TICK = 240, SECOND_WAVE = 16;

    private static long spawnBody(BattleSimulation sim, int[] area, int i) {
        return sim.spawn(new EntitySpec("Body " + i, Faction.DEFENDER, UnitType.MILITIA,
                area[0] + i % BodyPileCap.AREA_CELLS, area[1] + i / BodyPileCap.AREA_CELLS));
    }

    /** The pile cap's area the pile lies in: the one holding the open ground nearest the window's middle. */
    private static int[] pileArea(LookReview.Placement placement) {
        float[] at = placement.openNear(16f, 10f);
        return new int[]{Math.floorDiv((int) at[0], BodyPileCap.AREA_CELLS) * BodyPileCap.AREA_CELLS,
                Math.floorDiv((int) at[1], BodyPileCap.AREA_CELLS) * BodyPileCap.AREA_CELLS};
    }
}
