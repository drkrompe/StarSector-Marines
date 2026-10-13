package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.command.compound.CompoundService;
import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.fixture.BattleFixture;
import com.dillon.starsectormarines.battle.fixture.BattleFixtureJson;
import com.dillon.starsectormarines.battle.fort.Fort;
import com.dillon.starsectormarines.battle.fort.FortKind;
import com.dillon.starsectormarines.battle.shield.ShieldDomeService;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.ops.battleview.BattleReviewAnnotations;
import com.dillon.starsectormarines.ops.battleview.BattleReviewFrameRenderer;
import com.dillon.starsectormarines.ops.battleview.ReviewAnnotation;
import com.dillon.starsectormarines.ops.battleview.ReviewAnnotations;
import com.dillon.starsectormarines.ops.battleview.ReviewStyle;
import com.dillon.starsectormarines.testsupport.InstalledVanillaData;
import com.dillon.starsectormarines.testsupport.TileRegistryTestInstaller;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;
import org.json.JSONObject;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Where a Conquest mission's forts stand relative to the landing and the keep: the two production Conquest fixtures
 * (a south and a west approach) with two battery forts and one shield fort declared, once on leftover ground and once
 * sited as barriers on the approach. Whole map at 1920x1080 with the landing, keep and lanes marked, each fort labelled,
 * each battery's envelope and each dome drawn as a circle, so a reader can see whether the attack has to pass a fort.
 */
public final class FortSitingSnapshotSuite implements SnapshotSuite {

    private static final int WIDTH = 1920;
    private static final int HEIGHT = 1080;
    private static final String[] FIXTURES = {
            "/battle-fixtures/conquest-reinforced-south-v3.json",
            "/battle-fixtures/conquest-full-strength-west-v3.json"};
    private static final String[] NAMES = {"south", "west"};

    @Override public String id() { return "fort-siting"; }

    @Override public String label() {
        return "Conquest forts against the landing and the keep: leftover ground against sited as barriers";
    }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        TileRegistryTestInstaller.install(context.projectRoot());
        InstalledVanillaData.install();
        List<SnapshotArtifact> out = new ArrayList<>();
        for (int f = 0; f < FIXTURES.length; f++) {
            for (String siting : new String[]{"LEFTOVER_GROUND", "COVER_THE_APPROACH"}) {
                try (BattleSimulation sim = build(FIXTURES[f], siting)) {
                    String name = String.format(Locale.ROOT, "%s-%s.png", NAMES[f],
                            siting.equals("LEFTOVER_GROUND") ? "leftover" : "sited");
                    var renderer = new BattleReviewFrameRenderer(context.modRoot(), WIDTH, HEIGHT);
                    out.add(new SnapshotArtifact(name, renderer.render(sim,
                            NAMES[f] + " approach / " + siting + " / " + describe(sim), marks(sim))));
                }
            }
        }
        return out;
    }

    /** The fixture with two battery forts and one shield fort declared, sited as stated. */
    static BattleSimulation build(String resource, String siting) throws Exception {
        try (InputStream stream = FortSitingSnapshotSuite.class.getResourceAsStream(resource)) {
            String json = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            json = json.replace("\"targetProfile\": {", "\"targetProfile\": {\"declaredForts\": "
                    + "{\"BATTERY_FORT\": 2, \"SHIELD_FORT\": 1}, \"declaredFortSiting\": \"" + siting + "\",");
            BattleFixture fixture = BattleFixtureJson.fromJson(new JSONObject(json));
            return fixture.build();
        }
    }

    private static float[] keep(BattleSimulation sim) {
        for (CompoundService.Record record : sim.getCompoundService().getRecords()) {
            if (record.node.kind == TacticalNode.Kind.COMMAND_POST) return new float[]{record.node.anchorX, record.node.anchorY};
        }
        return null;
    }

    static float[] centre(BattleSimulation sim, Fort fort) {
        if (fort.kind() == FortKind.BATTERY_FORT) return new float[]{fort.battery().x(), fort.battery().y()};
        long emitter = fort.emitterId();
        return new float[]{sim.world().x(emitter), sim.world().y(emitter)};
    }

    private static String describe(BattleSimulation sim) {
        float[] keep = keep(sim);
        StringBuilder out = new StringBuilder();
        for (Fort fort : sim.forts().forts()) {
            float[] c = centre(sim, fort);
            if (out.length() > 0) out.append(", ");
            out.append(fort.kind() == FortKind.BATTERY_FORT ? "battery" : "shield");
            if (keep != null) {
                out.append(String.format(Locale.ROOT, " %.0f from keep", Math.hypot(c[0] - keep[0], c[1] - keep[1])));
            }
        }
        return out.toString();
    }

    private static ReviewAnnotations marks(BattleSimulation sim) {
        ReviewAnnotations.Builder marks = ReviewAnnotations.builder();
        for (ReviewAnnotation mark : BattleReviewAnnotations.forBattle(sim)) marks.add(mark);
        int battery = 0;
        for (Fort fort : sim.forts().forts()) {
            float[] c = centre(sim, fort);
            String label = fort.kind() == FortKind.BATTERY_FORT ? "BATTERY FORT " + battery++ : "SHIELD FORT";
            marks.label(c[0], c[1] + 5, label, ReviewStyle.NOTE);
            float radius = fort.kind() == FortKind.BATTERY_FORT ? fort.battery().envelopeCells() : domeRadius(sim, fort);
            marks.polyline(circle(c[0], c[1], radius), fort.kind() == FortKind.BATTERY_FORT ? "envelope" : "dome",
                    fort.kind() == FortKind.BATTERY_FORT ? ReviewStyle.NOTE : ReviewStyle.LANDING);
        }
        return marks.build();
    }

    /** The reach the fort's dome actually has in the battle, not a nominal figure. */
    private static float domeRadius(BattleSimulation sim, Fort fort) {
        for (ShieldDomeService.DomeView dome : sim.shieldDomes().domes()) {
            if (dome.entityId() == fort.emitterId()) return dome.radius();
        }
        return 0f;
    }

    private static List<ReviewAnnotation.Point> circle(float cx, float cy, float radius) {
        List<ReviewAnnotation.Point> points = new ArrayList<>();
        for (int i = 0; i <= 48; i++) {
            double a = i * Math.PI * 2 / 48;
            points.add(new ReviewAnnotation.Point(Math.round(cx + radius * Math.cos(a)),
                    Math.round(cy + radius * Math.sin(a))));
        }
        return points;
    }
}
