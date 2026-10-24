package com.dillon.starsectormarines.battle.evacuation.flier;

import com.dillon.starsectormarines.battle.combat.ShotEvent;
import com.dillon.starsectormarines.battle.scene.BehaviorSceneSnapshotSuite;
import com.dillon.starsectormarines.battle.scene.LookGround;
import com.dillon.starsectormarines.battle.scene.LookReview;
import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.scene.SceneWorld;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.ops.battleview.HarrierAudio;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The Harrier pack's frames: the {@code with-aa} and {@code no-aa} loops at 1920x1080 (verdict evidence, on a bare
 * arena), and for the owner look recordings on real generated ground: per {@link HarrierLook}, a gun line with its
 * flak and a pack of ten loitering out of reach and diving on it, at play zoom following the pack and close on the
 * line; the default look over the same line without its flak; and a close camera riding the pack's lead flier at a
 * frame every tick, so its wingbeat and its fold into the dive can be watched frame by frame; and the pack over the line
 * with its flak at play zoom, also a frame every tick; and close on the line as the claws land.
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
        String soundLog = System.getProperty("snapshot.harrierPack.soundLog", "").trim();
        if (!soundLog.isEmpty()) return List.of(soundShot(Path.of(soundLog)));
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
        // The wingbeat at play zoom under flak, and close on the line where the shot fliers fall and lie: a frame
        // every tick too, since a beat is a dozen ticks and the ordinary frame every six would alias it away.
        shots.add(new LookReview.Shot("harrier-" + HarrierLook.DEFAULT.name().toLowerCase().replace('_', '-') + "-flak-beat",
                LookGround.verdant(), 70, 40,
                placement -> stage(placement, HarrierLook.DEFAULT, true),
                placement -> List.of(LookReview.View.followAtPlay(placement, "line", "pack"),
                        LookReview.View.follow("line", placement, 26f, "line")),
                FLAK_TICKS, 1, 4, FLAK_STILLS));
        // The strike close on the line: dust, sparks and blood as each claw lands, and a throw or two. No flak, so
        // the pack lives to dive again, and the lift chance raised from its rare rule so a short clip catches one.
        shots.add(new LookReview.Shot("harrier-" + HarrierLook.DEFAULT.name().toLowerCase().replace('_', '-') + "-strike",
                LookGround.verdant(), 70, 40,
                placement -> {
                    BattleSimulation sim = stage(placement, HarrierLook.DEFAULT, false);
                    sim.swarmFliers().liftChance = STRIKE_LIFT_CHANCE;
                    return sim;
                },
                placement -> List.of(LookReview.View.follow("close", placement, STRIKE_CELLS, "line")),
                WINGS_TICKS, 1, 4, WINGS_STILLS));
        // The blood close up, at the rule's own throw rate: the spray as the bites land, then the spatter left on
        // the ground once the pack has climbed out.
        shots.add(new LookReview.Shot("harrier-" + HarrierLook.DEFAULT.name().toLowerCase().replace('_', '-') + "-blood",
                LookGround.verdant(), 70, 40,
                placement -> stage(placement, HarrierLook.DEFAULT, false),
                placement -> List.of(LookReview.View.follow("close", placement, BLOOD_CELLS, "line")),
                BLOOD_TICKS, 1, 4, BLOOD_STILLS));
        return shots;
    }

    /**
     * The sound shot, in place of the review set when {@code -Dsnapshot.harrierPack.soundLog} is set: the pack over the line under flak at play zoom, a frame every tick, with every sound
     * {@link HarrierAudio} would start written as a JSON line per sound ({@code frame, id, x, y, pitch, volume,
     * listenerX, listenerY}), and the line's fire, to that file for the review's soundtrack. The listener is
     * the camera's centre, as it is the screen's in a battle. The throw chance is raised so a short clip hears one.
     */
    private static LookReview.Shot soundShot(Path log) {
        String look = "harrier-" + HarrierLook.DEFAULT.name().toLowerCase().replace('_', '-');
        return new LookReview.Shot(look + "-sound", LookGround.verdant(), 70, 40,
                placement -> {
                    BattleSimulation sim = stage(placement, HarrierLook.DEFAULT, true);
                    sim.swarmFliers().liftChance = STRIKE_LIFT_CHANCE;
                    LookReview.View ear = LookReview.View.followAtPlay(placement, "line", "pack");
                    HarrierAudio.Mix mix = new HarrierAudio.Mix();
                    int[] frame = {0};
                    try {
                        Files.createDirectories(log.toAbsolutePath().getParent());
                        Files.writeString(log, "");
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                    placement.beforeTick(s -> {
                        float[] at = ear.focus().apply(s);
                        StringBuilder lines = new StringBuilder();
                        for (HarrierAudio.Play p : mix.hear(s, at[0], at[1])) {
                            lines.append(String.format(Locale.ROOT,
                                    "{\"frame\": %d, \"id\": \"%s\", \"x\": %.2f, \"y\": %.2f, \"pitch\": %.4f,"
                                            + " \"volume\": %.3f, \"listenerX\": %.2f, \"listenerY\": %.2f}%n",
                                    frame[0], p.soundId(), p.x(), p.y(), p.pitch(), p.volume(), at[0], at[1]));
                        }
                        // The line's fire too, at BattleShotAudio's levels, so the mix sets the fliers against it.
                        for (ShotEvent shot : s.getShotsThisFrame()) {
                            String id = shot.turretStructureDef != null ? shot.turretStructureDef.mount.weapon.fireSoundId
                                    : shot.primaryWeaponDef != null ? shot.primaryWeaponDef.fireSoundId : null;
                            if (id == null) continue;
                            lines.append(String.format(Locale.ROOT,
                                    "{\"frame\": %d, \"id\": \"%s\", \"x\": %.2f, \"y\": %.2f, \"pitch\": 1,"
                                            + " \"volume\": %.3f, \"listenerX\": %.2f, \"listenerY\": %.2f}%n",
                                    frame[0], id, shot.fromX, shot.fromY, shot.turretStructureDef != null ? 1f : 0.85f,
                                    at[0], at[1]));
                        }
                        frame[0]++;
                        try {
                            Files.writeString(log, lines, StandardOpenOption.APPEND);
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    });
                    return sim;
                },
                placement -> List.of(LookReview.View.followAtPlay(placement, "line", "pack")),
                SOUND_TICKS, 1, 2, new int[]{SOUND_TICKS / 2});
    }

    static final int SOUND_TICKS = 900;

    /** The wing close-up: cells across, ticks played, and its stills (frames are ticks here). */
    static final float WINGS_CELLS = 9f;
    static final int WINGS_TICKS = 720;
    static final int[] WINGS_STILLS = {60, 150, 240, 330, 420, 510, 600};
    /** The blood close-up: cells across, ticks played, and its stills (the bites land near tick 216). */
    static final float BLOOD_CELLS = 8f;
    static final int BLOOD_TICKS = 360;
    static final int[] BLOOD_STILLS = {216, 300};
    /** The strike close-up: cells across, and the lift chance it plays at so a throw shows. */
    static final float STRIKE_CELLS = 14f;
    static final float STRIKE_LIFT_CHANCE = 0.35f;
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
