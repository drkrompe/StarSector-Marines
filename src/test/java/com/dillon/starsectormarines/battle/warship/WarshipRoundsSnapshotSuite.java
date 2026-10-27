package com.dillon.starsectormarines.battle.warship;

import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.scene.SceneWorld;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.ops.battleview.BattleReviewFrameRenderer;
import com.dillon.starsectormarines.testsupport.InstalledHullSpecs;
import com.dillon.starsectormarines.testsupport.InstalledVanillaData;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;

import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Warship rounds in flight, posed beside the hull they are fired at: every weapon of the Black Sluice duel (Dormer's
 * Enforcer and Wolf, the Greyhook) as {@code WarshipRenderSystem} draws a round between hulls, one frame each and one
 * of them all in parallel lanes, each with an inset of the base game's own proportion (the hull's sprite and the
 * round at the spec's pixels, at one scale) for comparison. The missiles also get a short GIF of their run in, so the
 * contrail is seen being laid. 1920x1080 at 100%.
 *
 * <p>Posed rather than played: each round is placed on a straight run (a missile on its own hull-run flight) at a
 * chosen time, so the frame shows the drawing and nothing about who hits whom.
 */
public final class WarshipRoundsSnapshotSuite implements SnapshotSuite {

    /** The duel's weapons: weapon id and caption name. */
    private static final String[][] WEAPONS = {
            {"heavyac", "HEAVY AUTOCANNON"},
            {"arbalest", "ARBALEST AUTOCANNON"},
            {"heavymortar", "HEAVY MORTAR"},
            {"pulselaser", "PULSE LASER"},
            {"amblaster", "ANTIMATTER BLASTER"},
            {"harpoon", "HARPOON MRM"},
            {"reaper", "REAPER TORPEDO"},
    };

    /** Close-up width in target hull lengths. */
    private static final float ACROSS_LENGTHS = 3.2f;
    /** How far short of the target's near end the round's tip is posed, in its lengths. */
    private static final float SHORT_OF_HULL_LENGTHS = 0.35f;
    /** How far out the shooter is, in target lengths: off the frame, at hull-fight range. */
    private static final float SHOOTER_LENGTHS = 5f;
    private static final float GIF_STEP_SECONDS = 1f / 15f;
    private static final int GIF_FRAMES = 30;

    public String id() { return "warship-rounds"; }

    public String label() { return "Warship rounds in flight between hulls, at hull scale beside the vanilla proportion"; }

    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        InstalledHullSpecs.install();
        InstalledVanillaData.install();
        VanillaWeaponLooks.clearCache();

        Warship target = Warship.fit("enforcer", "enforcer_Balanced", Faction.DEFENDER, 0f, 0f, 0f);
        float hull = target.lengthCells;
        int width = (int) Math.ceil(hull * (SHOOTER_LENGTHS + 4f));
        int tall = (int) Math.ceil(hull * 3f);
        SceneBuilder builder = SceneBuilder.openGround(width, tall).missionCompletion(false);
        builder.squad("ally-ground").faction(Faction.MARINE).size(1).at(2, 2).unarmed().stationary().done();
        builder.squad("defender-ground").faction(Faction.DEFENDER).size(1).at(width - 3, tall - 3).unarmed()
                .stationary().done();
        SceneWorld world = builder.build();
        BattleSimulation sim = world.sim();
        for (int yy = 0; yy < tall; yy++) {
            for (int xx = 0; xx < width; xx++) sim.getTopology().setGroundKind(xx, yy, GroundKind.GRASS);
        }
        // The target lies across the line of fire, bow up, so the round comes at its beam.
        target.x = width - hull * 2f;
        target.y = tall * 0.5f;
        target.facingDegrees = 0f;
        target.stationTo(target.x, target.y);
        target.limitFlux();
        target.groundFire(Warship.GroundFire.HOLD);
        sim.addWarship(target);
        List<WarshipShell> shells = shellList(sim.warships());
        float hullPx = hull / WarshipScale.hullCells(1f);
        float nearEdge = target.centerX() - target.widthCells * 0.5f;

        List<SnapshotArtifact> out = new ArrayList<>();
        float across = hull * ACROSS_LENGTHS;
        BattleReviewFrameRenderer close = new BattleReviewFrameRenderer(context.modRoot(), 1920, 1080,
                target.centerX() - hull * 0.9f, target.centerY(), across);

        // All of them in lanes, abreast, each short of the hull's beam by the same share of its own length.
        shells.clear();
        float laneGap = hull * 0.9f / (WEAPONS.length - 1);
        StringBuilder all = new StringBuilder();
        for (int i = 0; i < WEAPONS.length; i++) {
            VanillaWeapon w = weapon(WEAPONS[i][0]);
            float y = target.centerY() - hull * 0.45f + laneGap * i;
            shells.add(pose(w, target, nearEdge - hull * 0.25f, y, hull, -1f));
            all.append(i == 0 ? "" : ", ").append(WEAPONS[i][0]);
        }
        out.add(new SnapshotArtifact("00-all-lanes.png", close.render(sim, String.format(Locale.ROOT,
                "WARSHIP ROUNDS / ALL DUEL WEAPONS ABREAST, BETWEEN HULLS, AT HULL SCALE, OFF AN ENFORCER'S BEAM "
                        + "(%.0f cells, %.0f px) / %s", hull, hullPx, all))));

        for (int i = 0; i < WEAPONS.length; i++) {
            String id = WEAPONS[i][0];
            VanillaWeapon w = weapon(id);
            VanillaRoundLook look = VanillaWeaponLooks.round(id);
            assertNotNull(look, id + " has a readable projectile spec");
            String slug = String.format(Locale.ROOT, "%02d-%s", i + 1, id);
            float roundCells = look.lengthCells(true);
            String measure = String.format(Locale.ROOT,
                    "%s, %s / round %.1f x %.1f cells against a %.0f-cell hull (1/%.1f; vanilla %.0f x %.1f px against "
                            + "%.0f px, 1/%.1f)%s",
                    WEAPONS[i][1], look.kind(), roundCells, look.widthCells(true), hull, hull / roundCells,
                    look.lengthPx(), look.widthPx(), hullPx, hullPx / look.lengthPx(),
                    look.glowRadiusPx() > 0f ? String.format(Locale.ROOT, " / glow r %.1f cells", look.glowRadiusCells(true)) : "");
            shells.clear();
            WarshipShell shell = pose(w, target, nearEdge - roundCells * SHORT_OF_HULL_LENGTHS - hull * 0.3f,
                    target.centerY(), hull, -1f);
            shells.add(shell);
            BufferedImage frame = close.render(sim, "WARSHIP ROUNDS / " + measure);
            inset(frame, context.modRoot(), target.spritePath, hullPx, look);
            out.add(new SnapshotArtifact(slug + ".png", frame));

            if (!w.missile()) continue;
            List<BufferedImage> run = new ArrayList<>();
            float end = shell.elapsed();
            for (int f = 0; f < GIF_FRAMES; f++) {
                float t = Math.max(0.05f, end - (GIF_FRAMES - 1 - f) * GIF_STEP_SECONDS);
                shells.clear();
                shells.add(pose(w, target, nearEdge - roundCells * SHORT_OF_HULL_LENGTHS - hull * 0.3f,
                        target.centerY(), hull, t));
                BufferedImage gifFrame = close.render(sim, String.format(Locale.ROOT,
                        "WARSHIP ROUNDS / %s / RUN IN, %.2f s of flight", measure, t));
                inset(gifFrame, context.modRoot(), target.spritePath, hullPx, look);
                run.add(gifFrame);
            }
            out.add(SnapshotArtifact.animation(slug + "-run.gif", run, Math.round(GIF_STEP_SECONDS * 1000f)));
        }
        shells.clear();
        return out;
    }

    private static VanillaWeapon weapon(String id) {
        VanillaWeapon w = VanillaWeapons.get(id);
        assertNotNull(w, id + " is in the weapon table");
        return w;
    }

    /**
     * A round of {@code w} fired at {@code target} from {@link #SHOOTER_LENGTHS} hull lengths to its west, along the
     * lane at {@code y}: a gun's on a straight run at its hull-fight speed, a missile's on its own hull run. Its tip
     * is posed at {@code tipX} unless {@code seconds} (when not negative) names the time in flight instead.
     */
    private static WarshipShell pose(VanillaWeapon w, Warship target, float tipX, float y, float hull, float seconds) {
        float fromX = target.centerX() - hull * SHOOTER_LENGTHS;
        float toX = target.centerX();
        MissilePath path;
        if (w.missile()) {
            path = MissilePath.atHull(w.flight(), fromX, y, toX, y, 7L, WarshipShipFire.HULL_MISSILE_SCALE, 0f,
                    MissileArchetype.of(w) == MissileArchetype.HEAVY);
        } else {
            path = MissilePath.straight(fromX, y, toX, y, Math.max(1f, WarshipScale.hullRoundSpeed(w.projectileSpeedPx())));
        }
        WarshipShell shell = new WarshipShell(w, Faction.MARINE, null, fromX, y, path.landX(), path.landY(), path,
                1f, 0f, 0f, 1f, null, null, 0f, null, 0f).atHull(target, toX, y, !w.missile());
        float t = seconds >= 0f ? seconds : timeAt(path, tipX);
        shell.advance(t);
        return shell;
    }

    /** The first time the flight reaches {@code x} (it flies east), by bisection. */
    private static float timeAt(MissilePath path, float x) {
        float lo = 0f, hi = path.totalSeconds();
        for (int i = 0; i < 40; i++) {
            float mid = 0.5f * (lo + hi);
            if (path.x(mid) < x) lo = mid;
            else hi = mid;
        }
        return lo;
    }

    @SuppressWarnings("unchecked")
    private static List<WarshipShell> shellList(WarshipSystem system) throws ReflectiveOperationException {
        Field f = WarshipSystem.class.getDeclaredField("shells");
        f.setAccessible(true);
        return (List<WarshipShell>) f.get(system);
    }

    /**
     * The base game's own proportion, bottom left: the hull's sprite and the round at the spec's pixels, at one
     * scale, side by side. A shell is its bullet sprite stretched to length by width, a streak a soft bolt in its
     * fringe and core colours, a missile its sprite at its size.
     */
    private static void inset(BufferedImage frame, Path modRoot, String hullSprite, float hullPx,
                              VanillaRoundLook look) throws Exception {
        int w = 420, h = 360, x0 = 24, y0 = frame.getHeight() - h - 24;
        Graphics2D g = frame.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setColor(new Color(10, 14, 20, 225));
        g.fillRect(x0, y0, w, h);
        g.setColor(new Color(140, 150, 160));
        g.setStroke(new BasicStroke(1f));
        g.drawRect(x0, y0, w, h);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        g.drawString("BASE GAME PROPORTION (spec pixels, one scale)", x0 + 12, y0 + 22);
        BufferedImage hullImage = image(modRoot, hullSprite);
        float k = (h - 70f) / hullPx;
        float cy = y0 + 40f + (h - 70f) * 0.5f;
        if (hullImage != null) {
            float hw = hullImage.getWidth() * k * hullPx / hullImage.getHeight();
            g.drawImage(hullImage, Math.round(x0 + 30), Math.round(cy - hullPx * k * 0.5f),
                    Math.round(hw), Math.round(hullPx * k), null);
        }
        float rx = x0 + w - 44f;
        float len = look.lengthPx() * k;
        float wid = Math.max(1f, look.widthPx() * k);
        AffineTransform saved = g.getTransform();
        if (look.glowColor() != null && look.glowRadiusPx() > 0f) {
            float r = look.glowRadiusPx() * k;
            Color c = look.glowColor();
            g.setPaint(new RadialGradientPaint(rx, cy, r, new float[]{0f, 1f},
                    new Color[]{c, new Color(c.getRed(), c.getGreen(), c.getBlue(), 0)}));
            g.fill(new Ellipse2D.Float(rx - r, cy - r, 2 * r, 2 * r));
        }
        if (look.kind() == VanillaRoundLook.Kind.STREAK) {
            Color f = look.fringe() != null ? look.fringe() : Color.ORANGE;
            Color c = look.core() != null ? look.core() : Color.WHITE;
            g.translate(rx, cy);
            g.scale(wid / len, 1f);
            g.setPaint(new RadialGradientPaint(0f, 0f, len * 0.5f, new float[]{0f, 0.6f, 1f},
                    new Color[]{c, f, new Color(f.getRed(), f.getGreen(), f.getBlue(), 0)}));
            g.fill(new Ellipse2D.Float(-len * 0.5f, -len * 0.5f, len, len));
        } else {
            BufferedImage round = image(modRoot, look.sprite());
            if (round != null) {
                g.setComposite(AlphaComposite.SrcOver);
                g.drawImage(round, Math.round(rx - wid * 0.5f), Math.round(cy - len * 0.5f), Math.round(wid),
                        Math.round(len), null);
            }
        }
        g.setTransform(saved);
        g.setColor(new Color(200, 210, 220));
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        g.drawString(String.format(Locale.ROOT, "hull %.0f px   round %.0f x %.1f px   1/%.1f", hullPx,
                look.lengthPx(), look.widthPx(), hullPx / look.lengthPx()), x0 + 12, y0 + h - 12);
        g.dispose();
    }

    /** A sprite by the game's path, from the mod first and the install second; null when neither has it. */
    private static BufferedImage image(Path modRoot, String path) throws Exception {
        if (path == null) return null;
        Path p = modRoot.resolve(path);
        if (!Files.isRegularFile(p)) {
            String dir = System.getProperty("starsectorDir");
            if (dir == null || dir.isBlank()) return null;
            p = Paths.get(dir, "starsector-core", path);
            if (!Files.isRegularFile(p)) return null;
        }
        return ImageIO.read(p.toFile());
    }
}
