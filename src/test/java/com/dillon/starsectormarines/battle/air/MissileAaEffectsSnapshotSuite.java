package com.dillon.starsectormarines.battle.air;

import com.dillon.starsectormarines.battle.scene.FrameSink;
import com.dillon.starsectormarines.battle.scene.SceneRegistries;
import com.dillon.starsectormarines.battle.scene.SceneReport;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.ops.battleview.BattleReviewFrameRenderer;
import com.dillon.starsectormarines.ops.battleview.ReviewAnnotations;
import com.dillon.starsectormarines.ops.battleview.ReviewImpactFx;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Opt-in 1920x1080 comparison; every body, trail and blast is taken from the production simulation. */
public final class MissileAaEffectsSnapshotSuite implements SnapshotSuite {
    @Override public String id() { return "missile-aa-effects"; }
    @Override public String label() { return "Missile AA / production flight and effects comparison"; }

    @Override public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        SceneRegistries.installArmoury(context.projectRoot());
        MissileAaEffectsScene scene = new MissileAaEffectsScene();
        BattleReviewFrameRenderer renderer = new BattleReviewFrameRenderer(context.modRoot(), 600, 780,
                42, 26, 40).withImpactEffects().withUnitShadows();
        BattleReviewFrameRenderer mountDetail = new BattleReviewFrameRenderer(context.modRoot(), 600, 260,
                40,25,10).withImpactEffects();
        Map<String, List<BufferedImage>> loops = new LinkedHashMap<>();
        Map<String, ReviewImpactFx> effects = new LinkedHashMap<>();
        List<SceneReport> reports = scene.play(new FrameSink() {
            private ReviewImpactFx fx(String loop) {
                return effects.computeIfAbsent(loop, ignored -> new ReviewImpactFx(context.modRoot()));
            }
            @Override public void advanced(String loop, BattleSimulation sim, int tick) { fx(loop).feed(sim); }
            @Override public void frame(String loop, BattleSimulation sim, int tick, String caption) {
                try {
                    BufferedImage rendered = renderer.render(sim, caption, ReviewAnnotations.NONE, fx(loop));
                    BufferedImage compact = new BufferedImage(600, 780, BufferedImage.TYPE_3BYTE_BGR);
                    Graphics2D g = compact.createGraphics();
                    g.drawImage(rendered, 0, 0, null);
                    g.drawImage(mountDetail.render(sim,"Mount detail / same simulation",ReviewAnnotations.NONE,fx(loop)),0,520,null);
                    g.dispose();
                    loops.computeIfAbsent(loop, ignored -> new ArrayList<>()).add(compact);
                } catch (IOException failure) { throw new UncheckedIOException(failure); }
            }
        });
        Path timeline = context.projectRoot().resolve("build/reports/scenes/missile-aa-effects/timeline.json");
        Files.createDirectories(timeline.getParent());
        Files.writeString(timeline, scene.timeline().toString(2));
        for (SceneReport report : reports) {
            System.out.println("MISSILE_AA_REPORT " + report.loopId() + " "
                    + (report.passed() ? "PASS" : "FAIL") + " " + report.metrics());
            report.verdicts().stream().filter(v -> !v.pass()).forEach(v ->
                    System.out.println("    " + v.name() + ": " + v.detail()));
        }
        List<SnapshotArtifact> artifacts = new ArrayList<>();
        int count = loops.get("locust").size();
        for (int frame = 0; frame < count; frame++) {
            BufferedImage image = new BufferedImage(1920, 1080, BufferedImage.TYPE_3BYTE_BGR);
            Graphics2D g = image.createGraphics();
            g.setColor(new Color(23,30,32)); g.fillRect(0,0,1920,1080);
            g.setColor(new Color(232,237,235)); g.setFont(new Font("Segoe UI",Font.PLAIN,32));
            g.drawString("MISSILE AA / LIVE FLIGHT AND EFFECTS",60,65);
            g.setFont(new Font("Segoe UI",Font.PLAIN,20));
            g.setColor(new Color(166,181,178));
            g.drawString("Actual catalog weapons / orbiting aircraft / 20000 HP fixture for salvo visibility",62,108);
            for (int column = 0; column < 3; column++) {
                String weapon = MissileAaEffectsScene.WEAPONS.get(column);
                int x=40+620*column;
                g.setColor(new Color(39,48,49)); g.fillRect(x,145,600,830);
                g.setColor(Color.WHITE); g.setFont(new Font("Segoe UI",Font.PLAIN,24));
                g.drawString(weapon.toUpperCase(Locale.ROOT),x+20,180);
                g.drawImage(loops.get(weapon).get(frame),x,195,null);
            }
            g.setColor(new Color(166,181,178)); g.setFont(new Font("Segoe UI",Font.PLAIN,20));
            g.drawString(String.format(Locale.ROOT,"t %.2f / 7.00 s",frame/20f),60,1010);
            g.drawString("Production guidance, collision and proximity fuse / camera only enlarged / no gameplay timing edits",400,1010);
            g.setColor(new Color(64,77,78)); g.fillRect(60,1032,1800,5);
            g.setColor(new Color(192,204,197)); g.fillRect(60,1032,Math.round(1800f*frame/(count-1)),5);
            g.dispose();
            artifacts.add(new SnapshotArtifact(String.format(Locale.ROOT,"frames/frame-%04d.png",frame),image));
            if (frame == 35) artifacts.add(new SnapshotArtifact("representative.png",image));
            // Release the three smaller frames as soon as the comparison owns their pixels.
            for (String weapon : MissileAaEffectsScene.WEAPONS) loops.get(weapon).set(frame,null);
        }
        System.out.println("MISSILE_AA_FRAME_COUNT=" + count);
        return artifacts;
    }
}
