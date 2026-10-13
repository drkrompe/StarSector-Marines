package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.air.ShuttleAssignment;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.fixture.BattleFixture;
import com.dillon.starsectormarines.battle.fixture.BattleFixtureJson;
import com.dillon.starsectormarines.battle.fixture.BattleLaunchFixture;
import com.dillon.starsectormarines.battle.fixture.ConquestBattleFixture;
import com.dillon.starsectormarines.battle.fort.Fort;
import com.dillon.starsectormarines.battle.fort.FortAspect;
import com.dillon.starsectormarines.battle.fort.FortKind;
import com.dillon.starsectormarines.battle.setup.BattleSetup;
import com.dillon.starsectormarines.battle.shield.ShieldDomeService;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.gen.GroundDefences;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
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
 * Shield forts standing in real generated Conquest maps, at 1920x1080 and game scale: a close view of each fort with
 * its aspects labelled, a view of the fort in its surroundings, and the whole map with every fort marked. This is
 * layout review evidence: it shows where the production stage put the authored fort and whether the city around it
 * still reads as a city.
 */
public final class ShieldFortMapSnapshotSuite implements SnapshotSuite {

    private static final int WIDTH = 1920;
    private static final int HEIGHT = 1080;
    private static final long[] SEEDS = {91L, 20261013L};

    @Override public String id() { return "shield-fort-map"; }

    @Override public String label() {
        return "Shield forts in generated Conquest maps: close layout, surroundings and whole map";
    }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        TileRegistryTestInstaller.install(context.projectRoot());
        InstalledVanillaData.install();
        List<SnapshotArtifact> out = new ArrayList<>();
        TargetProfile profile = fixtureProfile().withDeclaredDefences(
                new GroundDefences(0, false).withForts(FortKind.SHIELD_FORT, 2));
        for (long seed : SEEDS) {
            try (BattleSimulation sim = BattleSetup.createConquest(seed,
                    List.of(new ShuttleAssignment(ShuttleType.AEROSHUTTLE, 1, 6)), false,
                    OperationTier.FIRST_CONTRACT, RiskLevel.LOW, profile)) {
                // One label per fort on the whole map; aspect labels only in the fort's own close frame, so
                // neighbouring forts' labels never pile up at the frame edge.
                ReviewAnnotations.Builder overview = ReviewAnnotations.builder();
                int n = 0;
                for (Fort fort : sim.forts().forts()) {
                    Fort.Place emitter = fort.place(FortAspect.EMITTER);
                    overview.label(Math.round(sim.world().x(emitter.coreId())), Math.round(sim.world().y(emitter.coreId())) + 4, "SHIELD FORT " + n,
                            ReviewStyle.NOTE);
                    overview.polyline(domeRing(sim, emitter.coreId()), "dome", ReviewStyle.LANDING);
                    n++;
                }
                var whole = new BattleReviewFrameRenderer(context.modRoot(), WIDTH, HEIGHT);
                out.add(new SnapshotArtifact(String.format(Locale.ROOT, "seed-%d-whole-map.png", seed),
                        whole.render(sim, "Shield forts / seed " + seed + " / " + n + " forts", overview.build())));
                int i = 0;
                for (Fort fort : sim.forts().forts()) {
                    ReviewAnnotations.Builder labels = ReviewAnnotations.builder();
                    Fort.Place emitter = fort.place(FortAspect.EMITTER);
                    float cx = sim.world().x(emitter.coreId());
                    float cy = sim.world().y(emitter.coreId());
                    for (Fort.Place place : fort.places()) {
                        labels.label(Math.round(sim.world().x(place.coreId())),
                                Math.round(sim.world().y(place.coreId())) + 3,
                                place.aspect().label(), ReviewStyle.NOTE);
                    }
                    var close = new BattleReviewFrameRenderer(context.modRoot(), WIDTH, HEIGHT, cx, cy, 84);
                    out.add(new SnapshotArtifact(String.format(Locale.ROOT, "seed-%d-fort-%d-close.png", seed, i),
                            close.render(sim, "Shield fort " + i + " / seed " + seed, labels.build())));
                    var around = new BattleReviewFrameRenderer(context.modRoot(), WIDTH, HEIGHT, cx, cy, 150);
                    ReviewAnnotations.Builder dome = ReviewAnnotations.builder();
                    dome.polyline(domeRing(sim, emitter.coreId()), "dome", ReviewStyle.LANDING);
                    out.add(new SnapshotArtifact(String.format(Locale.ROOT, "seed-%d-fort-%d-surroundings.png", seed, i),
                            around.render(sim, "Shield fort " + i + " in its surroundings / seed " + seed, dome.build())));
                    i++;
                }
            }
        }
        return out;
    }

    /** The reach of the fort's dome as it stands in the battle, drawn as a ring about the generator. */
    private static List<ReviewAnnotation.Point> domeRing(BattleSimulation sim, long emitter) {
        float cx = sim.world().x(emitter);
        float cy = sim.world().y(emitter);
        float radius = 0f;
        for (ShieldDomeService.DomeView dome : sim.shieldDomes().domes()) {
            if (dome.entityId() == emitter) radius = dome.radius();
        }
        List<ReviewAnnotation.Point> points = new ArrayList<>();
        for (int k = 0; k <= 64; k++) {
            double a = k * Math.PI * 2 / 64;
            points.add(new ReviewAnnotation.Point(Math.round(cx + radius * Math.cos(a)), Math.round(cy + radius * Math.sin(a))));
        }
        return points;
    }

    /** The market a real Conquest is generated against: without one the stock dense crossroad is used instead. */
    private static TargetProfile fixtureProfile() throws Exception {
        try (InputStream stream = ShieldFortMapSnapshotSuite.class.getResourceAsStream(
                "/battle-fixtures/conquest-reinforced-south-v3.json")) {
            BattleFixture loaded = BattleFixtureJson.fromJson(new JSONObject(
                    new String(stream.readAllBytes(), StandardCharsets.UTF_8)));
            if (loaded instanceof BattleLaunchFixture launch) loaded = launch.construction();
            return ((ConquestBattleFixture) loaded).targetProfile();
        }
    }
}
