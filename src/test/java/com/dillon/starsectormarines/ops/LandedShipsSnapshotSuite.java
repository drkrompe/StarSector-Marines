package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.air.ShuttleAssignment;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.setup.BattleSetup;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.warship.LandedScale;
import com.dillon.starsectormarines.battle.warship.Warship;
import com.dillon.starsectormarines.battle.world.gen.BlackSluice;
import com.dillon.starsectormarines.battle.world.gen.SettlementLink;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.ops.battleview.BattleReviewFrameRenderer;
import com.dillon.starsectormarines.ops.battleview.HeadlessArmoryPreviewRenderer;
import com.dillon.starsectormarines.ops.battleview.ReviewAnnotations;
import com.dillon.starsectormarines.ops.battleview.ReviewStyle;
import com.dillon.starsectormarines.testsupport.DiskRegistries;
import com.dillon.starsectormarines.testsupport.HeadlessSvgAssets;
import com.dillon.starsectormarines.testsupport.InstalledHullSpecs;
import com.dillon.starsectormarines.testsupport.InstalledVanillaData;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A Venture and two Hound (P) landed at true scale on the production Black Sluice southern pads, below the ridge
 * from the ground fight, and the same three hulls flying their lift runs into flight scale
 * ({@code landed-ships-true-scale.md}). Art and scale evidence at 1920x1080 / 100%: the chapter is built without
 * its duel and the three hulls are placed here, so the hull shown does not depend on which ship the duel flies. Not
 * a played mission; the ground fight is left running only for the half-minute of the lift.
 */
public final class LandedShipsSnapshotSuite implements SnapshotSuite {

    /** Ticks between liftoff frames (two seconds), and the most frames the strip takes before the lift must be through. */
    private static final int LIFT_FRAME_EVERY = 60, LIFT_FRAMES = 32;
    /** Where the lift runs turn toward: west over the rock massif, the way Black Sluice's rig climbs out. */
    private static final float TURN_X = BlackSluice.APPROACH + 126f, TURN_Y = 20f;

    public String id() { return "landed-ships"; }
    public String label() { return "Landed ships: Venture and Hounds at true scale on the Black Sluice pads, and their lift run"; }

    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        DiskRegistries.installMapGeneration(context.projectRoot()); HeadlessSvgAssets.install(context.modRoot());
        HeadlessArmoryPreviewRenderer.installCatalogs(context.modRoot()); InstalledHullSpecs.install(); InstalledVanillaData.install();
        var frames = new ArrayList<SnapshotArtifact>();
        var lift = List.of(new ShuttleAssignment(ShuttleType.AEROSHUTTLE, 1, 6), new ShuttleAssignment(ShuttleType.AEROSHUTTLE, 1, 6));
        try (var sim = BattleSetup.createSmallWar(15305, lift,
                new TargetProfile(0, 0, 0, 0, "pirates", Set.of(), SurfacePalette.ROCK, SettlementLink.ROAD), true, false)) {
            var cradle = BlackSluice.VENTURE_CRADLE;
            var ships = new ArrayList<Warship>();
            ships.add(berth(sim, "venture", "venture_Balanced", cradle));
            for (var berth : BlackSluice.HOUND_BERTHS) ships.add(berth(sim, "hound_d_pirates", "hound_d_pirates_Standard", berth));
            float factor = LandedScale.berthFactor();
            for (Warship ship : ships) {
                assertTrue(ship.berthed(), ship.hullId + " sits in its berth");
                assertEquals(factor, ship.bodyScale(), 1e-5f, ship.hullId + " is drawn at the berth factor");
            }
            Warship venture = ships.get(0);
            float ventureLength = venture.lengthCells * venture.bodyScale();
            assertEquals(190f * LandedScale.CELLS_PER_HULL_PX, ventureLength, 0.5f, "a landed Venture is about fifty cells long");
            String scale = String.format(Locale.ROOT, "Venture %.1f cells, Hound %.1f cells long (true scale)",
                    ventureLength, ships.get(1).lengthCells * ships.get(1).bodyScale());

            var marks = ReviewAnnotations.builder()
                    .box(cradle.left(), cradle.bottom(), cradle.right(), cradle.top(), "VENTURE CRADLE", ReviewStyle.OBJECTIVE);
            for (var berth : BlackSluice.HOUND_BERTHS)
                marks.box(berth.left(), berth.bottom(), berth.right(), berth.top(), "HOUND BERTH", ReviewStyle.OBJECTIVE);
            marks.arrow(BlackSluice.BERTH_ROAD_X, 100, BlackSluice.BERTH_ROAD_X, 74, "BERTH ROAD FROM THE GATE", ReviewStyle.APPROACH)
                    .label(BlackSluice.APPROACH + 150, 84, "RIDGE: NO WAY THROUGH", ReviewStyle.NOTE)
                    .label(BlackSluice.APPROACH + 60, 40, "ROCK MASSIF", ReviewStyle.NOTE);
            frames.add(frame(context, "landed-dock-annotated.png", sim, BlackSluice.APPROACH + 196, 44, 192,
                    "LANDED SHIPS / BLACK SLUICE SOUTHERN BERTHS / " + scale + " / observer annotations", marks.build()));
            frames.add(frame(context, "landed-dock.png", sim, BlackSluice.APPROACH + 196, 44, 192,
                    "LANDED SHIPS / BLACK SLUICE SOUTHERN BERTHS / " + scale, ReviewAnnotations.builder().build()));
            frames.add(frame(context, "landed-venture-stern-crew.png", sim, BlackSluice.APPROACH + 222, 36, 56,
                    "LANDED SHIPS / VENTURE STERN, CONCRETE PAD, SCORCH AND TIE-DOWNS / 56 cells across", ReviewAnnotations.builder().build()));
            frames.add(frame(context, "landed-venture-whole.png", sim, BlackSluice.APPROACH + 196, 34, 104,
                    "LANDED SHIPS / VENTURE IN HER CRADLE, BERTHS EITHER SIDE / 104 cells across", ReviewAnnotations.builder().build()));
            frames.add(frame(context, "landed-hound-berth.png", sim, BlackSluice.APPROACH + 142, 34, 44,
                    "LANDED SHIPS / WEST HOUND BERTH, GANTRY ARMS AND KERB / 44 cells across", ReviewAnnotations.builder().build()));

            // The lift: every hull flies its lift run at once, ahead off the pad and round toward the west, its body
            // easing to flight scale with the share of the run it has flown rather than where it sits.
            for (Warship ship : ships) ship.lift(TURN_X, TURN_Y);
            float startX = venture.x, startY = venture.y, berthBody = venture.bodyScale(), previousBody = berthBody;
            float previousProgress = 0f;
            var strip = new ArrayList<BufferedImage>();
            for (int frame = 0; frame <= LIFT_FRAMES; frame++) {
                float body = venture.bodyScale(), progress = venture.liftProgress();
                assertTrue(progress >= previousProgress, "the lift only goes forward");
                assertTrue((body - previousBody) * (1f - berthBody) >= -1e-6f, "the body only eases toward flight scale");
                previousBody = body;
                previousProgress = progress;
                float moved = (float) Math.hypot(venture.x - startX, venture.y - startY);
                String caption = String.format(Locale.ROOT,
                        "LANDED SHIPS / LIFT RUN / %.0fs / %.0f%% of the run / body %.2fx flight scale / %.0f cells off the pad",
                        frame * LIFT_FRAME_EVERY / 30f, progress * 100f, body, moved);
                var image = new BattleReviewFrameRenderer(context.modRoot(), 1920, 1080, BlackSluice.APPROACH + 176, 40, 200).withUnitShadows()
                        .withImpactEffects().render(sim, caption, ReviewAnnotations.builder().build());
                frames.add(new SnapshotArtifact(String.format(Locale.ROOT, "landed-lift-%02d.png", frame), image));
                strip.add(image);
                if (progress >= 1f) break;
                for (int t = 0; t < LIFT_FRAME_EVERY; t++) sim.advance(BattleSimulation.TICK_DT);
            }
            assertEquals(1f, venture.liftProgress(), 0f, "the lift run is through inside the strip");
            assertEquals(1f, venture.bodyScale(), 1e-5f, "a lifted hull is at flight scale");
            float travelled = (float) Math.hypot(venture.x - startX, venture.y - startY);
            assertTrue(travelled > 0.5f * venture.lengthCells, "the scale change was carried by travel: " + travelled + " cells");
            frames.add(SnapshotArtifact.animation("landed-lift.gif", strip, 400));
        }
        return frames;
    }

    private static Warship berth(BattleSimulation sim, String hull, String variant, BlackSluice.Berth berth) throws Exception {
        Warship ship = Warship.fit(hull, variant, Faction.DEFENDER, berth.x(), berth.y(), berth.facing());
        ship.berth(berth.x(), berth.y());
        ship.limitFlux();
        ship.groundFire(Warship.GroundFire.HOLD);
        sim.addWarship(ship);
        return ship;
    }

    private static SnapshotArtifact frame(SnapshotContext context, String name, BattleSimulation sim, int x, int y, int across,
                                          String caption, ReviewAnnotations annotations) throws Exception {
        return new SnapshotArtifact(name, new BattleReviewFrameRenderer(context.modRoot(), 1920, 1080, x, y, across)
                .withUnitShadows().render(sim, caption, annotations));
    }
}
