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
import com.dillon.starsectormarines.battle.turret.StructureDef;
import com.dillon.starsectormarines.battle.turret.TurretCatalogRegistry;
import com.dillon.starsectormarines.battle.unit.Faction;
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
    private static final List<FortAspect> BATTERY_BUILDINGS =
            List.of(FortAspect.FIRE_CONTROL, FortAspect.MAGAZINE, FortAspect.POWER);
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
                    var roofed = new BattleReviewFrameRenderer(context.modRoot(), WIDTH, HEIGHT, cx, cy, 84).withRoofs();
                    out.add(new SnapshotArtifact(String.format(Locale.ROOT, "seed-%d-fort-%d-roofs.png", seed, i),
                            roofed.render(sim, "Battery fort " + i + " roofed / seed " + seed)));
                    i++;
                }
                if (seed == SEEDS[0]) out.addAll(buildingStates(context, sim));
            }
        }
        out.addAll(shieldFort(context));
        return out;
    }

    /** The shield fort's two buildings wear the same art, turned to their own doors: one roofed close frame. */
    private static List<SnapshotArtifact> shieldFort(SnapshotContext context) throws Exception {
        TargetProfile profile = fixtureProfile().withDeclaredDefences(
                new GroundDefences(0, false).withForts(FortKind.SHIELD_FORT, 1));
        try (BattleSimulation sim = BattleSetup.createConquest(SEEDS[0],
                List.of(new ShuttleAssignment(ShuttleType.AEROSHUTTLE, 1, 6)), false,
                OperationTier.FIRST_CONTRACT, RiskLevel.LOW, profile)) {
            List<SnapshotArtifact> out = new ArrayList<>();
            for (Fort fort : sim.forts().forts()) {
                if (fort.kind() != FortKind.SHIELD_FORT) continue;
                long generator = fort.emitterId();
                var frame = new BattleReviewFrameRenderer(context.modRoot(), WIDTH, HEIGHT,
                        sim.world().x(generator), sim.world().y(generator) - 6, 60).withRoofs();
                out.add(new SnapshotArtifact("shield-fort-roofs.png", frame.render(sim, "Shield fort roofed")));
            }
            return out;
        }
    }

    /**
     * Each aspect building in each condition, at game scale: intact, damaged, destroyed (the first fort, whose core is
     * hurt and then killed in turn) and captured (the second fort, taken by the marines). Real production state: the
     * cores are damaged through the simulation's own damage path and the holder flips through the capture rule.
     */
    private static List<SnapshotArtifact> buildingStates(SnapshotContext context, BattleSimulation sim)
            throws Exception {
        List<SnapshotArtifact> out = new ArrayList<>();
        List<Fort> forts = sim.forts().forts();
        if (forts.size() < 2) return out;
        Fort ladder = forts.get(0);
        Fort taken = forts.get(1);
        for (FortAspect aspect : BATTERY_BUILDINGS) {
            out.add(stateFrame(context, sim, ladder, aspect, "intact"));
        }
        for (FortAspect aspect : BATTERY_BUILDINGS) {
            Fort.Place place = taken.place(aspect);
            for (int s = 0; s < 40; s++) {
                if (aspect == FortAspect.FIRE_CONTROL) {
                    taken.battery().fireControl().tick(1f, false, Faction.MARINE);
                } else {
                    place.control().tick(1f, false, Faction.MARINE);
                }
            }
            out.add(stateFrame(context, sim, taken, aspect, "captured"));
        }
        for (FortAspect aspect : BATTERY_BUILDINGS) {
            long core = ladder.place(aspect).coreId();
            for (int s = 0; s < 40 && damageFraction(sim, ladder, aspect) > 0.45f; s++) {
                sim.applyDamage(core, 40f, 60f);
            }
            out.add(stateFrame(context, sim, ladder, aspect, "damaged"));
        }
        float[][] at = new float[FortAspect.values().length][];
        for (FortAspect aspect : BATTERY_BUILDINGS) {
            long core = ladder.place(aspect).coreId();
            at[aspect.ordinal()] = new float[]{sim.world().x(core), sim.world().y(core)};
            for (int s = 0; s < 200 && sim.world().isAlive(core); s++) sim.applyDamage(core, 80f, 200f);
        }
        for (int tick = 0; tick < 6; tick++) sim.advance(BattleSimulation.TICK_DT);
        for (FortAspect aspect : BATTERY_BUILDINGS) {
            float[] p = at[aspect.ordinal()];
            var frame = new BattleReviewFrameRenderer(context.modRoot(), WIDTH, HEIGHT, p[0], p[1], 24).withRoofs();
            out.add(new SnapshotArtifact(String.format(Locale.ROOT, "state-%s-wrecked.png", slug(aspect)),
                    frame.render(sim, aspect.label() + " / wrecked")));
        }
        return out;
    }

    private static float damageFraction(BattleSimulation sim, Fort fort, FortAspect aspect) {
        long core = fort.place(aspect).coreId();
        StructureDef def = TurretCatalogRegistry.requireStructure(
                fort.place(aspect).structureId());
        return (sim.world().hp(core) + sim.world().armor(core)) / (def.maxStructure + def.armorCapacity);
    }

    private static SnapshotArtifact stateFrame(SnapshotContext context, BattleSimulation sim, Fort fort,
                                               FortAspect aspect, String state) throws Exception {
        long core = fort.place(aspect).coreId();
        var frame = new BattleReviewFrameRenderer(context.modRoot(), WIDTH, HEIGHT,
                sim.world().x(core), sim.world().y(core), 24).withRoofs();
        return new SnapshotArtifact(String.format(Locale.ROOT, "state-%s-%s.png", slug(aspect), state),
                frame.render(sim, aspect.label() + " / " + state));
    }

    private static String slug(FortAspect aspect) {
        return aspect.name().toLowerCase(Locale.ROOT).replace('_', '-');
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
