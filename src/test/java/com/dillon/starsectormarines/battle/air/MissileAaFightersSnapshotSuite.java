package com.dillon.starsectormarines.battle.air;

import com.dillon.starsectormarines.battle.scene.FrameSink;
import com.dillon.starsectormarines.battle.scene.SceneRegistries;
import com.dillon.starsectormarines.battle.scene.SceneReport;
import com.dillon.starsectormarines.battle.setup.MissileAaTestSetup;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.ops.battleview.BattleReviewFrameRenderer;
import com.dillon.starsectormarines.ops.battleview.ReviewAnnotations;
import com.dillon.starsectormarines.ops.battleview.ReviewImpactFx;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;
import org.json.JSONArray;
import org.json.JSONObject;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Short production excerpt of the same native test factory played by the evidence scene. */
public final class MissileAaFightersSnapshotSuite implements SnapshotSuite {
    @Override public String id() { return "missile-aa-fighters"; }
    @Override public String label() { return "Missile AA / fighters evade in the native test battle"; }

    @Override public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        SceneRegistries.installArmoury(context.projectRoot());
        List<BattleReviewFrameRenderer> lanes=new ArrayList<>(),details=new ArrayList<>();
        for (var lane:MissileAaTestSetup.LANES) {
            lanes.add(new BattleReviewFrameRenderer(context.modRoot(),600,780,lane.x(),140,110).withImpactEffects());
            details.add(new BattleReviewFrameRenderer(context.modRoot(),600,260,lane.x(),lane.y(),10).withImpactEffects());
        }
        ReviewImpactFx effects=new ReviewImpactFx(context.modRoot());
        MissileAaFightersScene scene=new MissileAaFightersScene();
        List<SnapshotArtifact> artifacts=new ArrayList<>();
        List<SceneReport> reports=scene.play(new FrameSink() {
            private int frame;
            @Override public void advanced(String loop,BattleSimulation sim,int tick) { effects.feed(sim); }
            @Override public void frame(String loop,BattleSimulation sim,int tick,String caption) {
                try {
                    BufferedImage image=new BufferedImage(1920,1080,BufferedImage.TYPE_3BYTE_BGR);
                    Graphics2D g=image.createGraphics();
                    g.setColor(new Color(23,30,32));g.fillRect(0,0,1920,1080);
                    g.setColor(new Color(232,237,235));g.setFont(new Font("Segoe UI",Font.PLAIN,32));
                    g.drawString("MISSILE AA / FIGHTER EVASION",60,65);
                    g.setColor(new Color(166,181,178));g.setFont(new Font("Segoe UI",Font.PLAIN,20));
                    g.drawString("Native test battle / ordinary hull health / finite fighter waves / actual pilot steering",62,108);
                    for (int column=0;column<3;column++) {
                        int x=40+620*column;
                        g.setColor(new Color(39,48,49));g.fillRect(x,145,600,830);
                        g.setColor(Color.WHITE);g.setFont(new Font("Segoe UI",Font.PLAIN,24));
                        g.drawString(MissileAaTestSetup.LANES.get(column).id().toUpperCase(Locale.ROOT),x+20,180);
                        g.drawImage(lanes.get(column).render(sim,caption,ReviewAnnotations.NONE,effects),x,195,null);
                        g.drawImage(details.get(column).render(sim,"Mount detail / same native simulation",ReviewAnnotations.NONE,effects),x,715,null);
                    }
                    g.setColor(new Color(166,181,178));g.setFont(new Font("Segoe UI",Font.PLAIN,20));
                    g.drawString(String.format(Locale.ROOT,"Battle time %.2fs / excerpt %.2fs",tick/30d,frame/20d),60,1010);
                    g.drawString("Simulation owns motion, guidance and contact; the camera changes only the view.",750,1010);
                    g.dispose();
                    artifacts.add(new SnapshotArtifact(String.format(Locale.ROOT,"frames/frame-%04d.png",frame),image));
                    if (frame==20) artifacts.add(new SnapshotArtifact("overview.png",image));
                    frame++;
                } catch (IOException failure) { throw new UncheckedIOException(failure); }
            }
        });
        JSONArray summaries=new JSONArray();
        for (SceneReport report:reports) {
            System.out.println("MISSILE_AA_FIGHTERS "+report.loopId()+" "+(report.passed()?"PASS":"FAIL")
                    +" kills="+report.metrics().get("kills")+" exits="+report.metrics().get("exits")
                    +" unresolved="+report.metrics().get("unresolved")+" breaks="+report.metrics().get("evasionBreaks"));
            report.verdicts().stream().filter(v->!v.pass()).forEach(v->System.out.println("    "+v.name()+": "+v.detail()));
            summaries.put(new JSONObject().put("loop",report.loopId()).put("passed",report.passed())
                    .put("metrics",new JSONObject(report.metrics())));
        }
        Path output=context.projectRoot().resolve("build/reports/scenes/missile-aa-fighters");
        Files.createDirectories(output);
        Files.writeString(output.resolve("timeline.json"),scene.timeline().toString(2));
        Files.writeString(output.resolve("preview-metrics.json"),summaries.toString(2));
        System.out.println("MISSILE_AA_FIGHTER_FRAME_COUNT="+(artifacts.size()-1));
        return artifacts;
    }
}
