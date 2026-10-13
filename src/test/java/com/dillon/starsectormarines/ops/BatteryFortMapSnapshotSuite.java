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
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.gen.GroundDefences;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.ops.battleview.BattleReviewFrameRenderer;
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
 * Battery forts standing in real generated Conquest maps, at 1920x1080 and game scale: a close view of each fort with
 * its aspects labelled, a view of the fort in its surroundings, and the whole map with every fort marked. This is
 * layout review evidence: it shows where the production stage put the authored fort and whether the city around it
 * still reads as a city.
 */
public final class BatteryFortMapSnapshotSuite implements SnapshotSuite {

    private static final int WIDTH = 1920;
    private static final int HEIGHT = 1080;
    private static final long[] SEEDS = {91L, 20261013L};

    @Override public String id() { return "battery-fort-map"; }

    @Override public String label() {
        return "Battery forts in generated Conquest maps: close layout, surroundings and whole map";
    }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        TileRegistryTestInstaller.install(context.projectRoot());
        InstalledVanillaData.install();
        List<SnapshotArtifact> out = new ArrayList<>();
        TargetProfile profile = fixtureProfile().withDeclaredDefences(
                new GroundDefences(0, false).withForts(FortKind.BATTERY_FORT, 4));
        for (long seed : SEEDS) {
            try (BattleSimulation sim = BattleSetup.createConquest(seed,
                    List.of(new ShuttleAssignment(ShuttleType.AEROSHUTTLE, 1, 6)), false,
                    OperationTier.FIRST_CONTRACT, RiskLevel.LOW, profile)) {
                // One label per fort on the whole map; aspect labels only in the fort's own close frame, so
                // neighbouring forts' labels never pile up at the frame edge.
                ReviewAnnotations.Builder overview = ReviewAnnotations.builder();
                int n = 0;
                for (Fort fort : sim.forts().forts()) {
                    overview.label(Math.round(fort.battery().x()), Math.round(fort.battery().y()) + 4,
                            "FORT " + n, ReviewStyle.NOTE);
                    n++;
                }
                var whole = new BattleReviewFrameRenderer(context.modRoot(), WIDTH, HEIGHT);
                out.add(new SnapshotArtifact(String.format(Locale.ROOT, "seed-%d-whole-map.png", seed),
                        whole.render(sim, "Battery forts / seed " + seed + " / " + n + " forts", overview.build())));
                int i = 0;
                for (Fort fort : sim.forts().forts()) {
                    ReviewAnnotations.Builder labels = ReviewAnnotations.builder();
                    float cx = fort.battery().x();
                    float cy = fort.battery().y();
                    for (Fort.Place place : fort.places()) {
                        cx += sim.world().x(place.coreId());
                        cy += sim.world().y(place.coreId());
                        labels.label(Math.round(sim.world().x(place.coreId())),
                                Math.round(sim.world().y(place.coreId())) + 3,
                                place.aspect().label(), ReviewStyle.NOTE);
                    }
                    labels.label(Math.round(fort.battery().x()), Math.round(fort.battery().y()) + 3, "GUNS",
                            ReviewStyle.NOTE);
                    cx /= 4f;
                    cy /= 4f;
                    var close = new BattleReviewFrameRenderer(context.modRoot(), WIDTH, HEIGHT, cx, cy, 84);
                    out.add(new SnapshotArtifact(String.format(Locale.ROOT, "seed-%d-fort-%d-close.png", seed, i),
                            close.render(sim, "Battery fort " + i + " / seed " + seed, labels.build())));
                    var around = new BattleReviewFrameRenderer(context.modRoot(), WIDTH, HEIGHT, cx, cy, 150);
                    out.add(new SnapshotArtifact(String.format(Locale.ROOT, "seed-%d-fort-%d-surroundings.png", seed, i),
                            around.render(sim, "Battery fort " + i + " in its surroundings / seed " + seed)));
                    i++;
                }
            }
        }
        return out;
    }

    /** The market a real Conquest is generated against: without one the stock dense crossroad is used instead. */
    private static TargetProfile fixtureProfile() throws Exception {
        try (InputStream stream = BatteryFortMapSnapshotSuite.class.getResourceAsStream(
                "/battle-fixtures/conquest-reinforced-south-v3.json")) {
            BattleFixture loaded = BattleFixtureJson.fromJson(new JSONObject(
                    new String(stream.readAllBytes(), StandardCharsets.UTF_8)));
            if (loaded instanceof BattleLaunchFixture launch) loaded = launch.construction();
            return ((ConquestBattleFixture) loaded).targetProfile();
        }
    }
}
