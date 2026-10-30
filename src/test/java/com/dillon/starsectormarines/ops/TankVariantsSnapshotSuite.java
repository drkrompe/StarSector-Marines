package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.air.ShuttleAssignment;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.mech.MechWeaponComponent;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.setup.BattleSetup;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.turret.TurretMountGeometry;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.vehicle.VehicleEngine;
import com.dillon.starsectormarines.battle.vehicle.VehicleKit;
import com.dillon.starsectormarines.battle.vehicle.VehicleStockFit;
import com.dillon.starsectormarines.battle.vehicle.VehicleMission;
import com.dillon.starsectormarines.battle.vehicle.VehiclePlating;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import com.dillon.starsectormarines.battle.world.gen.SettlementLink;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.campaign.FreightRunProgress;
import com.dillon.starsectormarines.ops.battleview.HeadlessArmoryPreviewRenderer;
import com.dillon.starsectormarines.ops.battleview.BattleReviewFrameRenderer;
import com.dillon.starsectormarines.ops.battleview.ReviewAnnotation.Point;
import com.dillon.starsectormarines.ops.battleview.ReviewAnnotations;
import com.dillon.starsectormarines.ops.battleview.ReviewImpactFx;
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
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * The breakthrough hull and its variants side by side, live and wrecked, then the same
 * line in a short gun-range fight. Art review: the Parapet's interceptor kit, the laser
 * tank's ship-mounted emitter and the dozer must read as different tanks at game scale, and their
 * guns must leave from the drawn muzzle.
 */
public final class TankVariantsSnapshotSuite implements SnapshotSuite {
    private static final List<VehicleType> LINE = List.of(
            VehicleType.BREAKTHROUGH_TANK, VehicleType.BREAKTHROUGH_TANK, VehicleType.LASER_TANK,
            VehicleType.DOZER_BREACHER);
    /** The rack each hull of the line is set down with: the Parapet is the Onager with active protection. */
    private static final List<List<VehicleKit>> LINE_KITS = List.of(List.of(), VehicleStockFit.PARAPET.kits,
            List.of(), List.of());
    private static final List<String> NAMES = List.of(
            "ONAGER", "PARAPET (ONAGER + ACTIVE PROTECTION)", "PULSE-LASER TANK", "DOZER BREACHER");

    @Override public String id() { return "tank-variants"; }
    @Override public String label() { return "Tank variants side by side, wrecks and a battle frame"; }

    @Override public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        DiskRegistries.installMapGeneration(context.projectRoot());
        List<SnapshotArtifact> artifacts = new ArrayList<>();
        lineup(context, artifacts);
        laserLineup(context, artifacts);
        mountStyles(context, artifacts);
        legacyEnergy(context, artifacts);
        lances(context, artifacts);
        battle(context, artifacts);
        return List.copyOf(artifacts);
    }

    /** Live row above, wreck row below, guns posed forward; the cross marks each ballistic muzzle. */
    private static void lineup(SnapshotContext context, List<SnapshotArtifact> artifacts) throws Exception {
        try (BattleSimulation sim = scene(64, 40)) {
            sim.setMissionCompletionEnabled(false);
            ReviewAnnotations.Builder labels = ReviewAnnotations.builder();
            List<Long> live = new ArrayList<>();
            List<Long> wrecks = new ArrayList<>();
            for (int i = 0; i < LINE.size(); i++) {
                float x = 10 + i * 11;
                live.add(spawn(sim, LINE.get(i), LINE_KITS.get(i), Faction.MARINE, x, 26, 90, 90));
                wrecks.add(spawn(sim, LINE.get(i), LINE_KITS.get(i), Faction.MARINE, x, 13, 90, 90));
                labels.label(x, 21.5f, NAMES.get(i), ReviewStyle.NOTE);
                labels.label(x, 8.5f, NAMES.get(i) + " / WRECK", ReviewStyle.NOTE);
            }
            // An unarmed opponent keeps both sides on the map so the wrecks can be played out.
            long dummy = spawn(sim, VehicleType.CARGO_CARRIER, Faction.DEFENDER, 60, 37, 90, 0);
            for (long id : wrecks) sim.applyDamage(id, 10000, 10000);
            for (int tick = 0; tick < 60; tick++) sim.advance(BattleSimulation.TICK_DT);
            // Re-seat the live row: a deployed hull drifts while the wrecks play out.
            for (int i = 0; i < live.size(); i++) {
                sim.convoy().body(live.get(i)).teleport(10 + i * 11, 26, 90);
                sim.convoy().turret(live.get(i)).facingDeg = 90;
            }
            for (int i = 0; i < live.size(); i++) markMuzzle(sim, live.get(i), LINE.get(i), labels);
            sim.convoy().body(dummy).teleport(60, 37, 90);

            BattleReviewFrameRenderer renderer = new BattleReviewFrameRenderer(
                    context.modRoot(), 1920, 1080, 32.5f, 19.5f, 52);
            artifacts.add(new SnapshotArtifact("lineup.png", renderer.render(sim,
                    "Tank variants | Onager, Parapet (Onager + active protection kit), burst laser, dozer / live and wrecked / crosses mark the ballistic muzzle"
                            + " | 1920 x 1080, UI 100%", labels.build())));
            artifacts.add(new SnapshotArtifact("lineup-battle-scale.png", renderer.refocus(32, 19.5f, 60).render(sim,
                    "Tank variants | battle-scale camera, 60 cells across | 1920 x 1080, UI 100%", labels.build())));
        }
    }

    /** The laser tank with nothing in its gun card, then each laser of the ladder installed as its turret, as the game draws it. */
    private static final List<MechWeaponComponent> LASER_LINE = Arrays.asList(null,
            MechWeaponComponent.PL_3, MechWeaponComponent.PL_3_SWEEP, null,
            MechWeaponComponent.PL_2, MechWeaponComponent.BL_2,
            MechWeaponComponent.PL_1, MechWeaponComponent.BL_1);
    private static final List<String> LASER_NAMES = List.of(
            "NO PART: the bare ring, no turret", "PL-3 3x2: white head",
            "PL-3 (SWEEP) 3x2: red head", "",
            "PL-2 2x2", "BL-2 2x2", "PL-1 1x2", "BL-1 1x2");

    /** Two rows of laser tanks, the first row the three heads and the second the four lighter parts, guns posed forward. */
    private static void laserLineup(SnapshotContext context, List<SnapshotArtifact> artifacts) throws Exception {
        try (BattleSimulation sim = scene(48, 30)) {
            sim.setMissionCompletionEnabled(false);
            ReviewAnnotations.Builder labels = ReviewAnnotations.builder();
            ReviewAnnotations.Builder headLabels = ReviewAnnotations.builder();
            ReviewAnnotations.Builder partLabels = ReviewAnnotations.builder();
            List<float[]> places = new ArrayList<>();
            List<Long> tanks = new ArrayList<>();
            for (int i = 0; i < LASER_LINE.size(); i++) {
                if (LASER_NAMES.get(i).isEmpty()) continue;
                float x = 10 + 6.5f * (i % 4);
                float y = i < 4 ? 17 : 9;
                places.add(new float[] {x, y});
                tanks.add(spawnFitted(sim, VehicleType.LASER_TANK, LASER_LINE.get(i), x, y, 90, 90));
                labels.label(x, y - 2.5f, LASER_NAMES.get(i), ReviewStyle.NOTE);
                (i < 4 ? headLabels : partLabels).label(x, y - 2.5f, LASER_NAMES.get(i), ReviewStyle.NOTE);
            }
            long dummy = spawn(sim, VehicleType.CARGO_CARRIER, Faction.DEFENDER, 46, 28, 90, 0);
            for (int tick = 0; tick < 60; tick++) sim.advance(BattleSimulation.TICK_DT);
            for (int i = 0; i < tanks.size(); i++) {
                sim.convoy().body(tanks.get(i)).teleport(places.get(i)[0], places.get(i)[1], 90);
                if (sim.convoy().turret(tanks.get(i)) != null) sim.convoy().turret(tanks.get(i)).facingDeg = 90;
            }
            sim.convoy().body(dummy).teleport(46, 28, 90);
            BattleReviewFrameRenderer renderer = new BattleReviewFrameRenderer(
                    context.modRoot(), 1920, 1080, 19.75f, 12.8f, 26.5f);
            artifacts.add(new SnapshotArtifact("laser-lineup.png", renderer.render(sim,
                    "Laser tank turrets | every laser of the ladder installed in the gun card as the game draws it"
                            + " | 1920 x 1080, UI 100%", labels.build())));
            String title = "Laser tank turrets, close | 1920 x 1080, UI 100%";
            artifacts.add(new SnapshotArtifact("laser-lineup-heads.png",
                    renderer.refocus(16.5f, 17f, 19f).render(sim, title, headLabels.build())));
            artifacts.add(new SnapshotArtifact("laser-lineup-parts.png",
                    renderer.refocus(19.75f, 7.5f, 25f).render(sim, title, partLabels.build())));
        }
    }

    private static final List<VehicleType> STYLE_HULLS = List.of(VehicleType.HEAVY_APC, VehicleType.MILITIA_TECHNICAL,
            VehicleType.BREAKTHROUGH_TANK, VehicleType.HOVER_SCOUT, VehicleType.HEAVY_APC, VehicleType.LASER_TANK);
    private static final List<MechWeaponComponent> STYLE_GUNS = List.of(MechWeaponComponent.BL_1,
            MechWeaponComponent.PL_1, MechWeaponComponent.HC_20,
            MechWeaponComponent.BL_1, MechWeaponComponent.LIGHT_MACHINE_GUN,
            MechWeaponComponent.PL_3);
    private static final List<String> STYLE_NAMES = List.of("GREEN (low): ESCORT APC", "GREEN: MILITIA TECHNICAL",
            "GREEN: BREAKTHROUGH TANK", "SILVER (mid): HOVER SCOUT", "GREEN: UP-ARMORED APC", "WHITE (top): LASER TANK");

    /** One hull of each mount style, each carrying a part in its gun card, labelled by the style of its ring. */
    private static void mountStyles(SnapshotContext context, List<SnapshotArtifact> artifacts) throws Exception {
        try (BattleSimulation sim = scene(48, 30)) {
            sim.setMissionCompletionEnabled(false);
            ReviewAnnotations.Builder labels = ReviewAnnotations.builder();
            List<float[]> places = new ArrayList<>();
            List<Long> hulls = new ArrayList<>();
            for (int i = 0; i < STYLE_HULLS.size(); i++) {
                float x = 9 + 10 * (i % 3);
                float y = i < 3 ? 17 : 8;
                places.add(new float[] {x, y});
                hulls.add(spawnFitted(sim, STYLE_HULLS.get(i), STYLE_GUNS.get(i), x, y, 90, 90));
                labels.label(x, y - 3.4f, STYLE_NAMES.get(i), ReviewStyle.NOTE);
            }
            long dummy = spawn(sim, VehicleType.CARGO_CARRIER, Faction.DEFENDER, 46, 28, 90, 0);
            for (int tick = 0; tick < 60; tick++) sim.advance(BattleSimulation.TICK_DT);
            for (int i = 0; i < hulls.size(); i++) {
                sim.convoy().body(hulls.get(i)).teleport(places.get(i)[0], places.get(i)[1], 90);
                if (sim.convoy().turret(hulls.get(i)) != null) sim.convoy().turret(hulls.get(i)).facingDeg = 90;
            }
            sim.convoy().body(dummy).teleport(46, 28, 90);
            BattleReviewFrameRenderer renderer = new BattleReviewFrameRenderer(
                    context.modRoot(), 1920, 1080, 19f, 12.2f, 27f);
            artifacts.add(new SnapshotArtifact("mount-styles.png", renderer.render(sim,
                    "Turret mount styles | green ring (army hulls), silver ring (hover hulls), white ring (laser tank)"
                            + " | 1920 x 1080, UI 100%", labels.build())));
        }
    }

    private static final List<VehicleType> LEGACY_HULLS = List.of(VehicleType.LASER_TANK, VehicleType.LASER_TANK,
            VehicleType.LASER_TANK, VehicleType.HEAVY_APC, VehicleType.HOVER_SCOUT, VehicleType.MILITIA_TECHNICAL);
    private static final List<MechWeaponComponent> LEGACY_GUNS = List.of(MechWeaponComponent.PL_2_AP,
            MechWeaponComponent.LL_3, MechWeaponComponent.THERMAL_LANCE,
            MechWeaponComponent.THERMAL_LANCE, MechWeaponComponent.THERMAL_LANCE, MechWeaponComponent.THERMAL_LANCE);
    private static final List<String> LEGACY_NAMES = List.of("LASER TANK: PL-2 (AP) 2x2",
            "LASER TANK: LL-3 3x2", "LASER TANK: THERMAL LANCE 1x2", "ESCORT APC: THERMAL LANCE",
            "HOVER SCOUT: THERMAL LANCE", "MILITIA TECHNICAL: THERMAL LANCE");

    /** The older energy parts, still in the olive and red mech art, on the laser tank and the small hulls. */
    private static void legacyEnergy(SnapshotContext context, List<SnapshotArtifact> artifacts) throws Exception {
        try (BattleSimulation sim = scene(48, 30)) {
            sim.setMissionCompletionEnabled(false);
            ReviewAnnotations.Builder labels = ReviewAnnotations.builder();
            List<float[]> places = new ArrayList<>();
            List<Long> hulls = new ArrayList<>();
            for (int i = 0; i < LEGACY_HULLS.size(); i++) {
                float x = 9 + 10 * (i % 3);
                float y = i < 3 ? 17 : 8;
                places.add(new float[] {x, y});
                hulls.add(spawnFitted(sim, LEGACY_HULLS.get(i), LEGACY_GUNS.get(i), x, y, 90, 90));
                labels.label(x, y - 3.4f, LEGACY_NAMES.get(i), ReviewStyle.NOTE);
            }
            long dummy = spawn(sim, VehicleType.CARGO_CARRIER, Faction.DEFENDER, 46, 28, 90, 0);
            for (int tick = 0; tick < 60; tick++) sim.advance(BattleSimulation.TICK_DT);
            for (int i = 0; i < hulls.size(); i++) {
                sim.convoy().body(hulls.get(i)).teleport(places.get(i)[0], places.get(i)[1], 90);
                if (sim.convoy().turret(hulls.get(i)) != null) sim.convoy().turret(hulls.get(i)).facingDeg = 90;
            }
            sim.convoy().body(dummy).teleport(46, 28, 90);
            BattleReviewFrameRenderer renderer = new BattleReviewFrameRenderer(
                    context.modRoot(), 1920, 1080, 19f, 12.2f, 27f);
            artifacts.add(new SnapshotArtifact("legacy-energy.png", renderer.render(sim,
                    "Older energy parts | PL-2 (AP), LL-3 and Thermal Lance, still the olive and red art"
                            + " | 1920 x 1080, UI 100%", labels.build())));
        }
    }

    /**
     * The two laser tanks close up, every tick drawn: the burst laser's blue bursts beside the PD laser's held red
     * lance, each on unarmed hulls, so the grain, the throb, the shutter and the flux haze (and the burst laser
     * overloading and venting) can be read at play speed.
     */
    private static void lances(SnapshotContext context, List<SnapshotArtifact> artifacts) throws Exception {
        try (BattleSimulation sim = scene(40, 32)) {
            sim.setMissionCompletionEnabled(false);
            long burst = spawn(sim, VehicleType.LASER_TANK, Faction.MARINE, 14, 10, 90, 90);
            long pd = spawnFitted(sim, VehicleType.LASER_TANK, MechWeaponComponent.PL_3_SWEEP, 26, 10, 90, 90);
            for (int i = 0; i < 3; i++) {
                spawn(sim, VehicleType.CARGO_CARRIER, Faction.DEFENDER, 12 + i * 8, 21, 270, 270);
            }
            ReviewImpactFx impacts = new ReviewImpactFx(context.modRoot());
            BattleReviewFrameRenderer renderer = new BattleReviewFrameRenderer(
                    context.modRoot(), 1920, 1080, 20, 15.5f, 30).withImpactEffects();
            List<BufferedImage> frames = new ArrayList<>();
            BufferedImage busiest = null;
            int busiestBeams = -1;
            for (int tick = 0; tick < 450; tick++) {
                sim.advance(BattleSimulation.TICK_DT);
                impacts.feed(sim);
                // Hold the hulls still, as the lineup does: a deployed hull drifts, and only the guns are on review.
                sim.convoy().body(burst).teleport(14, 10, 90);
                sim.convoy().body(pd).teleport(26, 10, 90);
                if (tick < 30 || tick % 2 != 0) continue;
                ReviewAnnotations.Builder labels = ReviewAnnotations.builder();
                var a = sim.convoy().body(burst);
                var b = sim.convoy().body(pd);
                labels.label(a.x, a.y - 3.2f, "PULSE-LASER TANK", ReviewStyle.NOTE);
                labels.label(b.x, b.y - 3.2f, "LASER TANK / BL-2", ReviewStyle.NOTE);
                BufferedImage frame = renderer.render(sim,
                        "Laser lances | PL-3 (blue, bursts) beside BL-2 (red, held, throbbing)"
                                + " | flux haze on the rear vents, venting when overloaded | 1920 x 1080, UI 100%", labels.build(), impacts);
                frames.add(frame);
                int beams = sim.getActiveShots().size();
                if (beams > busiestBeams) {
                    busiestBeams = beams;
                    busiest = frame;
                }
            }
            artifacts.add(new SnapshotArtifact("lances.png", busiest));
            artifacts.add(SnapshotArtifact.colorAnimation("lances.gif", frames, 66));
        }
    }

    /**
     * The whole line on the production Thunder Run staging apron (seed 9301), facing a line of
     * unarmed defender hulls; real autonomous fire with impact effects. Spots are found on
     * the map rather than authored, so terrain changes cannot strand a hull in a wall.
     */
    private static void battle(SnapshotContext context, List<SnapshotArtifact> artifacts) throws Exception {
        HeadlessSvgAssets.install(context.modRoot());
        HeadlessArmoryPreviewRenderer.installCatalogs(context.modRoot());
        InstalledHullSpecs.install();
        InstalledVanillaData.install();
        var profile = new TargetProfile(0, 0, 0, 0, "", Set.of(),
                SurfacePalette.VERDANT, SettlementLink.ROAD);
        var manifest = List.of(new ShuttleAssignment(ShuttleType.AEROSHUTTLE, 2, 6),
                new ShuttleAssignment(ShuttleType.AEROSHUTTLE, 2, 6));
        var attempt = new FreightRunProgress("tank-variants-thunder-run").previewAttempt();
        try (BattleSimulation sim = BattleSetup.createThunderRun(9301, manifest, profile, attempt, 0)) {
            sim.setMissionCompletionEnabled(false);
            var lead = sim.convoy().body(sim.getThunderRun().heavyLead());
            float heading = lead.facingDegrees;
            float fx = -(float) Math.sin(Math.toRadians(heading)), fy = (float) Math.cos(Math.toRadians(heading));
            List<float[]> taken = new ArrayList<>();
            for (long id : sim.getConvoyVehicleIds()) {
                var body = sim.convoy().body(id);
                taken.add(new float[]{body.x, body.y});
            }
            List<Long> line = new ArrayList<>();
            List<float[]> posts = new ArrayList<>();
            for (int i = 0; i < LINE.size(); i++) {
                float[] at = openSpot(sim, lead.x - fx * 4, lead.y - fy * 4, taken);
                posts.add(at);
                line.add(spawn(sim, LINE.get(i), LINE_KITS.get(i), Faction.MARINE, at[0], at[1], heading, heading));
            }
            float cx = 0, cy = 0;
            for (float[] at : posts) { cx += at[0] / posts.size(); cy += at[1] / posts.size(); }
            List<float[]> targets = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                float[] at = openSpot(sim, cx + fx * 24, cy + fy * 24, taken);
                targets.add(at);
                long target = spawn(sim, VehicleType.BREAKTHROUGH_TANK, Faction.DEFENDER, at[0], at[1],
                        heading + 180, heading + 180);
                sim.convoy().turret(target).ammo = 0;
            }
            float tx = 0, ty = 0;
            for (float[] at : targets) { tx += at[0] / targets.size(); ty += at[1] / targets.size(); }
            ReviewImpactFx impacts = new ReviewImpactFx(context.modRoot());
            BattleReviewFrameRenderer renderer = new BattleReviewFrameRenderer(
                    context.modRoot(), 1920, 1080, (cx + tx) * .5f, (cy + ty) * .5f, 48).withImpactEffects();
            List<BufferedImage> frames = new ArrayList<>();
            BufferedImage busiest = null;
            int busiestShots = -1;
            int[] rounds = new int[LINE.size()];
            for (int tick = 0; tick < 360; tick++) {
                sim.advance(BattleSimulation.TICK_DT);
                impacts.feed(sim);
                for (var shot : sim.getShotsThisFrame()) {
                    int index = line.indexOf(shot.shooterId);
                    if (index >= 0) rounds[index]++;
                }
                if (tick % 3 != 0) continue;
                ReviewAnnotations.Builder labels = ReviewAnnotations.builder();
                for (int i = 0; i < line.size(); i++) {
                    var body = sim.convoy().body(line.get(i));
                    labels.label(body.x, body.y - 3.2f, NAMES.get(i), ReviewStyle.NOTE);
                }
                BufferedImage frame = renderer.render(sim,
                        "Tank variants in battle | THUNDER RUN staging apron, seed 9301 | breakthrough, "
                                + " Parapet, burst laser, dozer against unarmed hulls | autonomous fire | 1920 x 1080, UI 100%",
                        labels.build(), impacts);
                frames.add(frame);
                int inFlight = sim.getActiveShots().size();
                if (tick > 30 && inFlight > busiestShots) {
                    busiestShots = inFlight;
                    busiest = frame;
                }
            }
            for (int i = 0; i < rounds.length; i++) {
                if (rounds[i] == 0) throw new IllegalStateException(NAMES.get(i) + " never fired");
            }
            artifacts.add(new SnapshotArtifact("battle.png", busiest));
            artifacts.add(SnapshotArtifact.colorAnimation("battle.gif", frames, 100));
        }
    }

    /** Nearest cell to (x, y) whose surrounding 7x7 is walkable and clear of every hull already placed. */
    private static float[] openSpot(BattleSimulation sim, float x, float y, List<float[]> taken) {
        NavigationGrid grid = sim.getGrid();
        float[] best = null;
        float bestDistance = Float.MAX_VALUE;
        for (int cy = (int) y - 30; cy <= (int) y + 30; cy++) {
            for (int cx = (int) x - 30; cx <= (int) x + 30; cx++) {
                float distance = (cx + .5f - x) * (cx + .5f - x) + (cy + .5f - y) * (cy + .5f - y);
                if (distance >= bestDistance || !open(grid, cx, cy)) continue;
                boolean clear = true;
                for (float[] other : taken) {
                    float dx = other[0] - cx - .5f, dy = other[1] - cy - .5f;
                    if (dx * dx + dy * dy < 49f) { clear = false; break; }
                }
                if (!clear) continue;
                bestDistance = distance;
                best = new float[]{cx + .5f, cy + .5f};
            }
        }
        if (best == null) throw new IllegalStateException("No open ground near " + x + ", " + y);
        taken.add(best);
        return best;
    }

    private static boolean open(NavigationGrid grid, int x, int y) {
        for (int dy = -3; dy <= 3; dy++) {
            for (int dx = -3; dx <= 3; dx++) {
                int gx = x + dx, gy = y + dy;
                if (gx < 0 || gy < 0 || gx >= grid.getWidth() || gy >= grid.getHeight()
                        || !grid.isWalkable(gx, gy)) return false;
            }
        }
        return true;
    }

    private static BattleSimulation scene(int width, int height) {
        NavigationGrid grid = new NavigationGrid(width, height);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) grid.setWalkableFloor(x, y);
        }
        return new BattleSimulation(grid, new CellTopology(width, height));
    }

    private static long spawn(BattleSimulation sim, VehicleType type, Faction faction,
                              float x, float y, float hull, float turret) {
        long id = sim.convoy().spawn(type, faction, VehicleMission.deployed(x, y));
        sim.convoy().body(id).teleport(x, y, hull);
        if (type.hasTurretWeapon()) sim.convoy().turret(id).facingDeg = turret;
        return id;
    }

    /** As above, with {@code kits} on its rack and its own gun. */
    private static long spawn(BattleSimulation sim, VehicleType type, List<VehicleKit> kits, Faction faction,
                              float x, float y, float hull, float turret) {
        long id = sim.convoy().spawn(type, kits, faction, VehicleMission.deployed(x, y));
        sim.convoy().body(id).teleport(x, y, hull);
        if (type.hasTurretWeapon()) sim.convoy().turret(id).facingDeg = turret;
        return id;
    }

    /** A hull with a mech weapon fitted in its gun card, parked as {@link #spawn} parks one. */
    private static long spawnFitted(BattleSimulation sim, VehicleType type, MechWeaponComponent gun,
                                    float x, float y, float hull, float turret) {
        long id = sim.convoy().spawn(type, List.of(), VehiclePlating.FACTORY, VehicleEngine.STOCK, gun,
                Faction.MARINE, VehicleMission.deployed(x, y));
        sim.convoy().body(id).teleport(x, y, hull);
        if (sim.convoy().turret(id) != null) sim.convoy().turret(id).facingDeg = turret;
        return id;
    }

    private static void markMuzzle(BattleSimulation sim, long id, VehicleType type, ReviewAnnotations.Builder marks) {
        var body = sim.convoy().body(id);
        float c = (float) Math.cos(Math.toRadians(body.facingDegrees));
        float s = (float) Math.sin(Math.toRadians(body.facingDegrees));
        float mountX = body.x + type.turretMountX * c - type.turretMountY * s;
        float mountY = body.y + type.turretMountX * s + type.turretMountY * c;
        var muzzle = TurretMountGeometry.muzzle(mountX, mountY,
                sim.convoy().turret(id).facingDeg, type.turretStructure().mount, 0);
        // Polyline points address cell centers; physical muzzles are world positions.
        float x = muzzle.x() - .5f, y = muzzle.y() - .5f;
        marks.polyline(List.of(new Point(x - .15f, y), new Point(x + .15f, y)), "", ReviewStyle.NOTE);
        marks.polyline(List.of(new Point(x, y - .15f), new Point(x, y + .15f)), "", ReviewStyle.NOTE);
    }
}
