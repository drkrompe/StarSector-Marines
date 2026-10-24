package com.dillon.starsectormarines.battle.evacuation.flier;

import com.dillon.starsectormarines.battle.scene.BehaviorSceneSnapshotSuite;
import com.dillon.starsectormarines.battle.scene.LookGround;
import com.dillon.starsectormarines.battle.scene.LookReview;
import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.scene.SceneWorld;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;

import java.util.ArrayList;
import java.util.List;

/**
 * The Harrier pack's frames: the {@code with-aa} and {@code no-aa} loops at 1920x1080 (verdict evidence, on a bare
 * arena), and for the owner look recordings on real generated ground: per {@link HarrierLook}, a gun line with its
 * flak and a pack of ten loitering out of reach and diving on it, at play zoom following the pack and close on the
 * line; the default look over the same line without its flak; and a close camera riding the pack's lead flier at a
 * frame every tick, so its wingbeat and its fold into the dive can be watched frame by frame; and the pack over the line
 * with its flak at play zoom, also a frame every tick.
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
        // The animation close-up: no flak, so the lead lives through the loiter, a dive and the climb out.
        shots.add(new LookReview.Shot("harrier-" + HarrierLook.DEFAULT.name().toLowerCase().replace('_', '-') + "-wings",
                LookGround.verdant(), 70, 40,
                placement -> stage(placement, HarrierLook.DEFAULT, false),
                placement -> List.of(LookReview.View.follow("close", placement, WINGS_CELLS, "pack")),
                WINGS_TICKS, 1, 4, WINGS_STILLS));
        // The wingbeat at play zoom under flak: a frame every tick too, since a beat is a dozen ticks and the
        // ordinary frame every six would alias it away.
        shots.add(new LookReview.Shot("harrier-" + HarrierLook.DEFAULT.name().toLowerCase().replace('_', '-') + "-flak-beat",
                LookGround.verdant(), 70, 40,
                placement -> stage(placement, HarrierLook.DEFAULT, true),
                placement -> List.of(LookReview.View.followAtPlay(placement, "line", "pack")),
                FLAK_TICKS, 1, 4, FLAK_STILLS));
        return shots;
    }

    /** The wing close-up: cells across, ticks played, and its stills (frames are ticks here). */
    static final float WINGS_CELLS = 9f;
    static final int WINGS_TICKS = 720;
    static final int[] WINGS_STILLS = {60, 150, 240, 330, 420, 510, 600};
    /** The play-zoom wingbeat under flak: ticks played, and its stills (frames are ticks here). */
    static final int FLAK_TICKS = 900;
    static final int[] FLAK_STILLS = {90, 240, 390, 540, 690, 840};

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
