package com.dillon.starsectormarines.battle.evacuation.flier;

import com.dillon.starsectormarines.battle.scene.BehaviorSceneSnapshotSuite;
import com.dillon.starsectormarines.battle.scene.LookReview;
import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.scene.SceneWorld;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;

import java.util.ArrayList;
import java.util.List;

/**
 * The Harrier pack's frames: the {@code with-aa} and {@code no-aa} loops at 1920x1080 (verdict evidence, on a bare
 * arena), and for the owner one look recording per {@link HarrierLook}: the same gun line with its flak on real
 * generated ground, a pack of ten loitering out of reach and diving on it, at play zoom following the pack and close
 * on the line, plus the default look over the same line without its flak.
 */
public final class HarrierPackSceneSnapshotSuite extends BehaviorSceneSnapshotSuite {

    public HarrierPackSceneSnapshotSuite() {
        super(new HarrierPackScene(), 1920, 1080);
    }

    /** Recorded frames (every 6 ticks) kept as stills: circling, gathering, the dive, the pull-up, and after. */
    static final int[] STILLS = {6, 12, 18, 24, 30, 36, 44, 52, 60, 80};

    @Override protected boolean showImpactEffects() { return true; }

    @Override protected boolean showUnitShadows() { return true; }

    @Override protected List<LookReview.Shot> lookShots() {
        List<LookReview.Shot> shots = new ArrayList<>();
        for (HarrierLook look : HarrierLook.values()) {
            shots.add(LookReview.Shot.of("harrier-" + look.name().toLowerCase().replace('_', '-'), 70, 40,
                    placement -> stage(placement, look, true),
                    placement -> List.of(LookReview.View.followAtPlay(placement, "line", "pack"),
                            LookReview.View.follow("line", placement, 26f, "line")),
                    1200).withStills(STILLS));
        }
        // The control for the default look: the same line with no flak, so a pack's dives can be watched both ways.
        shots.add(LookReview.Shot.of("harrier-" + HarrierLook.DEFAULT.name().toLowerCase().replace('_', '-') + "-no-aa",
                70, 40, placement -> stage(placement, HarrierLook.DEFAULT, false),
                placement -> List.of(LookReview.View.followAtPlay(placement, "line", "pack"),
                        LookReview.View.follow("line", placement, 26f, "line")),
                1200).withStills(STILLS));
        return shots;
    }

    private static BattleSimulation stage(LookReview.Placement placement, HarrierLook look, boolean antiAir) {
        float[] line = placement.openNear(14f, 20f);
        int lx = (int) line[0], ly = (int) line[1];
        SceneBuilder builder = placement.builder();
        builder.squad("rifles").faction(Faction.MARINE).size(6)
                .at(lx + 4 - placement.site().x0(), ly - placement.site().y0())
                .primary("weapon.field-rifle").stationary().done();
        SceneWorld world = builder.build();
        BattleSimulation sim = world.sim();
        long[] guns = HarrierPackScene.spawnGuns(sim, lx, ly, antiAir);
        placement.subject("line", guns[1]);
        HarrierPack pack = sim.swarmFliers().release(sim, look, placement.site().x(54f), placement.site().y(20f),
                HarrierPackScene.PACK, 0L);
        placement.subject("pack", pack.members.get(0).id);
        return sim;
    }
}
