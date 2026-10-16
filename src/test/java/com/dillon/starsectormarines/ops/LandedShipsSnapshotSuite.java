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
 * A Venture and two Hound (P) landed at true scale in the production Black Sluice docking compound, with Greyhook's
 * own crews standing beside them, and the same three hulls lifting out of their berths into flight scale
 * ({@code landed-ships-true-scale.md}). Art and scale evidence at 1920x1080 / 100%: the chapter is built without
 * its duel and the three hulls are placed here, so the hull shown does not depend on which ship the duel flies. Not
 * a played mission; the ground fight is left running only for the few seconds of the lift.
 */
public final class LandedShipsSnapshotSuite implements SnapshotSuite {

    /** Ticks between liftoff frames: a frame every second and a half, through the six-second lift and a little past. */
    private static final int LIFT_FRAME_EVERY = 45, LIFT_FRAMES = 6;

    public String id() { return "landed-ships"; }
    public String label() { return "Landed ships: Venture and Hounds at true scale in the Black Sluice dock, and their lift"; }

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
            marks.arrow(190, 56, 206, 56, "PRESSURE VESTIBULE", ReviewStyle.APPROACH)
                    .label(205, 69, "PRIZE CREW AT THE STERN GANTRY", ReviewStyle.NOTE);
            frames.add(frame(context, "landed-dock-annotated.png", sim, 172, 64, 192,
                    "LANDED SHIPS / BLACK SLUICE DOCKING COMPOUND / " + scale + " / observer annotations", marks.build()));
            frames.add(frame(context, "landed-dock.png", sim, 172, 64, 192,
                    "LANDED SHIPS / BLACK SLUICE DOCKING COMPOUND / " + scale, ReviewAnnotations.builder().build()));
            frames.add(frame(context, "landed-venture-stern-crew.png", sim, 214, 62, 56,
                    "LANDED SHIPS / VENTURE STERN AND HANGAR, PRIZE CREW ON THE GANTRY APRON / 56 cells across", ReviewAnnotations.builder().build()));
            frames.add(frame(context, "landed-venture-whole.png", sim, 232, 64, 104,
                    "LANDED SHIPS / VENTURE IN HER CRADLE, BERTHS EITHER SIDE / 104 cells across", ReviewAnnotations.builder().build()));
            frames.add(frame(context, "landed-hound-berth.png", sim, 228, 106, 44,
                    "LANDED SHIPS / NORTH HOUND BERTH, GANTRY ARM AND FUEL LINE / 44 cells across", ReviewAnnotations.builder().build()));

            // The lift: every hull leaves its berth at once and eases to flight scale where it stands.
            for (Warship ship : ships) ship.lift();
            float previous = Float.POSITIVE_INFINITY;
            var strip = new ArrayList<BufferedImage>();
            for (int frame = 0; frame <= LIFT_FRAMES; frame++) {
                float body = venture.bodyScale();
                assertTrue(body <= previous + 1e-6f, "the body only eases toward flight scale");
                previous = body;
                String caption = String.format(Locale.ROOT, "LANDED SHIPS / LIFT / %.1fs / body %.2fx flight scale (Venture %.1f cells)",
                        frame * LIFT_FRAME_EVERY / 30f, body, venture.lengthCells * body);
                var image = new BattleReviewFrameRenderer(context.modRoot(), 1920, 1080, 232, 64, 132).withUnitShadows()
                        .withImpactEffects().render(sim, caption, ReviewAnnotations.builder().build());
                frames.add(new SnapshotArtifact(String.format(Locale.ROOT, "landed-lift-%02d.png", frame), image));
                strip.add(image);
                for (int t = 0; t < LIFT_FRAME_EVERY && frame < LIFT_FRAMES; t++) sim.advance(BattleSimulation.TICK_DT);
            }
            assertEquals(1f, venture.bodyScale(), 1e-5f, "a lifted hull is at flight scale");
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
