package com.dillon.starsectormarines.battle.air;

import com.dillon.starsectormarines.battle.air.engine.TurretSlotResolver;
import com.dillon.starsectormarines.battle.flyby.FighterProfile;
import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.scene.SceneRegistries;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.ops.battleview.BattleReviewFrameRenderer;
import com.dillon.starsectormarines.ops.battleview.ReviewAnnotations;
import com.dillon.starsectormarines.ops.battleview.ReviewImpactFx;
import com.dillon.starsectormarines.testsupport.InstalledHullEngines;
import com.dillon.starsectormarines.testsupport.InstalledHullSpecs;
import com.dillon.starsectormarines.testsupport.InstalledVanillaData;
import com.dillon.starsectormarines.tools.snapshot.CompressedFrameList;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Six fighter panels plus Mudskipper stills from production damage and crash simulation. */
public final class AircraftCrashSnapshotSuite implements SnapshotSuite {
    private static final int FPS = 15;
    private static final int FRAMES = 180;

    @Override
    public String id() { return "aircraft-crash"; }

    @Override
    public String label() { return "Aircraft crash / controlled lethal impact"; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        SceneRegistries.installArmoury(context.projectRoot());
        InstalledHullSpecs.install();
        InstalledHullEngines.install();
        InstalledVanillaData.install();
        Airframe[] profiles = {FighterProfile.TALON, FighterProfile.WASP, FighterProfile.THUNDER,
                FighterProfile.BROADSWORD, FighterProfile.LONGBOW, FighterProfile.DAGGER, ShuttleType.MUDSKIPPER};
        String[] names = {"Talon", "Wasp", "Thunder", "Broadsword", "Longbow", "Dagger", "Mudskipper"};
        List<BattleSimulation> sims = new ArrayList<>();
        List<BattleReviewFrameRenderer> renderers = new ArrayList<>();
        long[] craft = new long[profiles.length];
        AirBody[] bodies = new AirBody[profiles.length];
        ShuttleMission[] missions = new ShuttleMission[profiles.length];
        AirCrashImpact[] impacts = new AirCrashImpact[profiles.length];
        List<ReviewImpactFx> effects = new ArrayList<>();
        List<SnapshotArtifact> artifacts = new ArrayList<>();
        boolean[] mudskipperStills = new boolean[3];
        var mudskipperView = new BattleReviewFrameRenderer(context.modRoot(), 1920, 1080, 30, 35, 32)
                .withImpactEffects().withUnitShadows();
        try {
            for (int i = 0; i < profiles.length; i++) {
                var sim = SceneBuilder.openGround(384, 64).seed(20261015 + i)
                        .missionCompletion(false).build().sim();
                sims.add(sim);
                effects.add(new ReviewImpactFx(context.modRoot()));
                for (int y = 0; y < 64; y++) {
                    for (int x = 0; x < 384; x++) {
                        sim.getTopology().setGroundKind(x, y,
                                ((x / 8 + y / 8) % 5 == 0) ? GroundKind.DIRT : GroundKind.STONE);
                    }
                }
                craft[i] = sim.spawnSortie(profiles[i], Faction.MARINE, 360, 32, 30, 32, 380, 32, 0);
                var body = sim.world().kinematics(craft[i]);
                bodies[i] = body;
                body.facingDegrees = -90;
                body.vx = profiles[i].flight().maxSpeed() * .45f;
                missions[i] = sim.world().mission(craft[i]);
                missions[i].state = ShuttleState.INCOMING;
                sim.world().setAltitudeT(craft[i], 1);
                renderers.add(new BattleReviewFrameRenderer(context.modRoot(), 600, 414, 30, 35, 10)
                        .withImpactEffects().withUnitShadows());
            }
            for (int frame = 0; frame < FRAMES; frame++) {
                // Two production 30Hz ticks per displayed frame: exact real-time 15fps.
                for (int tick = 0; tick < 2; tick++) {
                    for (int i = 0; i < profiles.length; i++) {
                        var sim = sims.get(i);
                        if (frame * 2 + tick == 30) sim.applyAimedDamage(craft[i], 0L, 10000f, 100f);
                        sim.advance(BattleSimulation.TICK_DT);
                        effects.get(i).feed(sim);
                        var mission = missions[i];
                        if (mission.destroyed && mission.crashSeconds < AirCrashMotion.FALL_SECONDS
                                && mission.crashImpact != null) {
                            throw new IllegalStateException(names[i] + ": impact captured before ground contact");
                        }
                        if (mission.destroyed && mission.crashSeconds >= AirCrashMotion.FALL_SECONDS) {
                            if (mission.crashImpact == null) {
                                throw new IllegalStateException(names[i] + ": missing contact snapshot");
                            }
                            if (impacts[i] != null && impacts[i] != mission.crashImpact) {
                                throw new IllegalStateException(names[i] + ": contact snapshot was replaced");
                            }
                            impacts[i] = mission.crashImpact;
                        }
                        if (i == 6) {
                            int stage = mission.crashImpact == null && mission.destroyed
                                    && mission.crashSeconds >= AirCrashMotion.FALL_SECONDS - .12f ? 0
                                    : mission.crashImpact != null && mission.state != ShuttleState.GONE ? 1
                                    : mission.state == ShuttleState.GONE && frame >= 150 ? 2 : -1;
                            if (stage >= 0 && !mudskipperStills[stage]) {
                                var body = bodies[i];
                                float altitude = stage == 2 ? 0
                                        : AirAppearance.visualAltitudeOffsetCells(sim.world().altitudeT(craft[i]));
                                String[] files = {"mudskipper-before-contact.png", "mudskipper-contact.png", "mudskipper-settled.png"};
                                String[] captions = {"Mudskipper / falling before contact / original hull colors",
                                        "Mudskipper / ground contact / breakup and char", "Mudskipper / settled ground wreck"};
                                var still = mudskipperView.refocus(body.x, body.y + altitude, 32)
                                        .render(sim, captions[stage] + " / 60 px per cell", ReviewAnnotations.NONE, effects.get(i));
                                artifacts.add(new SnapshotArtifact(files[stage], still));
                                mudskipperStills[stage] = true;
                            }
                        }
                        var wrecks = sim.getAirfieldService().groundWrecks();
                        if (wrecks.size() > 1) {
                            throw new IllegalStateException(names[i] + ": duplicate permanent wreck");
                        }
                        if (!wrecks.isEmpty() && wrecks.get(0).crashImpact != impacts[i]) {
                            throw new IllegalStateException(names[i] + ": wreck lost original contact snapshot");
                        }
                    }
                }
                BufferedImage image = new BufferedImage(1920, 1080, BufferedImage.TYPE_3BYTE_BGR);
                Graphics2D g = image.createGraphics();
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g.setColor(new Color(20, 28, 33));
                g.fillRect(0, 0, 1920, 1080);
                g.setColor(new Color(234, 240, 242));
                g.setFont(new Font("Segoe UI", Font.PLAIN, 30));
                g.drawString("Aircraft breakup and sparks / controlled crash preview", 40, 49);
                g.setFont(new Font("Segoe UI", Font.PLAIN, 19));
                g.setColor(new Color(170, 187, 197));
                g.drawString("Production damage, fall and skid / 60 px per cell in every view / real time", 42, 82);
                for (int i = 0; i < 6; i++) {
                    int px = 30 + (i % 3) * 630;
                    int py = 110 + (i / 3) * 464;
                    var sim = sims.get(i);
                    var body = bodies[i];
                    float altitude = missions[i].state == ShuttleState.GONE ? 0
                            : AirAppearance.visualAltitudeOffsetCells(sim.world().altitudeT(craft[i]));
                    var view = renderers.get(i).refocus(body.x, body.y + altitude, 10);
                    g.setColor(new Color(234, 240, 242));
                    g.setFont(new Font("Segoe UI", Font.PLAIN, 23));
                    g.drawString(names[i], px + 10, py + 27);
                    String phase = !missions[i].destroyed ? "Approach"
                            : missions[i].state == ShuttleState.GONE ? "Settled wreck"
                            : missions[i].crashSeconds < AirCrashMotion.FALL_SECONDS ? "Falling" : "Impact and skid";
                    g.drawImage(view.render(sim, phase, ReviewAnnotations.NONE, effects.get(i)), px, py + 40, null);
                }
                g.setColor(new Color(170, 187, 197));
                g.setFont(new Font("Segoe UI", Font.PLAIN, 18));
                g.drawString(String.format(Locale.ROOT, "%.2f s / 12.00 s", (frame + 1) / (double) FPS), 42, 1061);
                g.drawString("Controlled damage trigger / production crash motion", 1170, 1061);
                g.dispose();
                // Each artifact keeps a compressed frame; the runner still owns every output write.
                CompressedFrameList packed = new CompressedFrameList();
                packed.add(image);
                artifacts.add(new SnapshotArtifact(
                        Path.of(String.format(Locale.ROOT, "frames/frame-%04d.png", frame)), packed, 67));
                if (frame == 60) artifacts.add(new SnapshotArtifact("representative.png", image));
                if ((frame + 1) % 30 == 0) System.out.println("AIRCRAFT_CRASH rendered " + (frame + 1) + "/" + FRAMES);
            }
            for (int i = 0; i < profiles.length; i++) {
                if (impacts[i] == null || sims.get(i).getAirfieldService().groundWrecks().size() != 1) {
                    throw new IllegalStateException(names[i] + ": fixture did not finish with one persistent crash wreck; "
                            + "state=" + missions[i].state + " position=" + bodies[i].x + "," + bodies[i].y
                            + " speed=" + bodies[i].speed());
                }
                System.out.println("AIRCRAFT_CRASH " + names[i] + " contact=" + impacts[i].atSeconds()
                        + " wrecks=1 sharedImpact=true");
            }
            for (boolean captured : mudskipperStills) {
                if (!captured) throw new IllegalStateException("Mudskipper lifecycle still was not captured");
            }
            return artifacts;
        } finally {
            for (var sim : sims) sim.close();
            InstalledHullEngines.uninstall();
            TurretSlotResolver.useHullSpecs(null);
        }
    }
}
