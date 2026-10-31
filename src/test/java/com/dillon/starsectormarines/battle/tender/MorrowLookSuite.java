package com.dillon.starsectormarines.battle.tender;

import com.dillon.starsectormarines.battle.power.SpawnInOnslaught;
import com.dillon.starsectormarines.battle.scene.LookGround;
import com.dillon.starsectormarines.battle.scene.LookReview;
import com.dillon.starsectormarines.battle.scene.SceneRegistries;
import com.dillon.starsectormarines.battle.scene.SceneWorld;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.ui.highlight.BuildPreview;
import com.dillon.starsectormarines.battle.ui.highlight.HighlightOverlay;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.warship.ShipSupportSource;
import com.dillon.starsectormarines.battle.warship.Warship;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.ops.battleview.BattleReviewFrameRenderer;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import com.dillon.starsectormarines.testsupport.InstalledHullSpecs;
import com.dillon.starsectormarines.testsupport.InstalledVanillaData;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;

import java.util.List;

/**
 * Morrow in the look preset (`tender-ship.md`, Vulnerability): the recovering Shepherd in her Working Passage low hover
 * at the Onslaught power's presence, beside an Onslaught at that same presence for scale, flying across arid ground
 * with her defence drones out while her builders put up her three recovery builds: a heavy MG turret, then the staged
 * Locust nest and the Locust fire base (`tender-ship.md`, Staged builds), each plot's pad, rim and stores going down as the
 * work passes their shares. A crate stack stands on the fire base's rim, for its pad to clear, and a marine stands on
 * its gun cell until 92 s, so the finished base holds its gun until he goes. For the first 25 s two placement ghosts
 * show at play zoom: a nest where it may be laid and a fire base refused over the heavy MG's site. A wide camera on
 * both hulls, one riding Morrow, one on the sites, one close on each build and the ghosts at play zoom. Logs the sites'
 * progress and stages every two seconds.
 */
public class MorrowLookSuite implements SnapshotSuite {

    private static final int W = 110, H = 60;
    private static final int SECONDS = 120, EVERY_TICKS = 3;
    /** Frames (a tenth of a second each) for the stills: setting out, then through each build's stages. */
    private static final int[] STILLS = {40, 200, 300, 400, 550, 650, 750, 880, 1000, 1199};
    /** When the marine on the fire base's gun cell steps off it, in ticks. */
    private static final int MARINE_LEAVES = 30 * 92;
    /** How long the placement ghosts show, in ticks. */
    private static final int GHOSTS_UNTIL = 30 * 25;

    @Override public String id() { return "morrow-look"; }

    @Override public String label() { return "Look: Morrow flying beside an Onslaught, her drones out, building a heavy MG, a Locust nest and a Locust fire base"; }

    @Override public boolean inAll() { return false; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        SceneRegistries.installArmoury(context.projectRoot());
        InstalledHullSpecs.install();
        InstalledVanillaData.install();
        Tender[] morrow = new Tender[1];
        Warship[] onslaught = new Warship[1];
        float[] sites = new float[6];
        float[] ghosts = new float[2];
        LookReview.Shot shot = new LookReview.Shot("morrow", LookGround.of(SurfacePalette.ARID, 7L), W, H, placement -> {
            float[] start = placement.openNear(22f, 28f);
            float[] goal = placement.openNear(56f, 26f);
            float[] heavy = placement.openNear(30f, 44f);
            float[] locust = placement.openNear(46f, 46f);
            float[] base = placement.openNear(62f, 44f);
            float[] station = placement.openNear(84f, 30f);
            float[] ghost = placement.openNear(40f, 30f);
            ghosts[0] = ghost[0]; ghosts[1] = ghost[1];
            // With no hostile in sight a plot faces away from the hull, so the base's gun cell is known before the lay.
            int bx = (int) Math.floor(base[0]), by = (int) Math.floor(base[1]);
            int[] facing = BuildPlot.facing(base[0] - start[0], base[1] - start[1]);
            int[] gun = BuildPlot.FIRE_BASE.gun(bx, by, facing[0], facing[1]);
            SceneWorld world = placement.holdouts(placement.builder())
                    .unit("plot-marine", Faction.MARINE, UnitType.MARINE, gun[0] - placement.site().x0(),
                            gun[1] - placement.site().y0(), spec -> spec.moveSpeed(0f))
                    .build();
            BattleSimulation sim = world.sim();
            long marine = world.unit("plot-marine");
            sim.addDoodad(new Doodad(bx + 2, by + 2, TileRegistry.installed().doodad("doodad.industrial-crate-stack")));
            sites[0] = heavy[0]; sites[1] = heavy[1]; sites[2] = locust[0]; sites[3] = locust[1];
            sites[4] = base[0]; sites[5] = base[1];
            try {
                Tender tender = Tender.lowHover(Faction.MARINE, start[0], start[1], SpawnInOnslaught.PRESENCE);
                sim.tenders().add(sim, tender);
                tender.ship().stationTo(goal[0], goal[1]);
                morrow[0] = tender;
                Warship ship = Warship.fit(ShipSupportSource.stockFor("onslaught"), Faction.MARINE,
                        station[0], station[1], 180f, SpawnInOnslaught.PRESENCE);
                sim.addWarship(ship);
                ship.stationTo(station[0], station[1]);
                onslaught[0] = ship;
            } catch (Exception e) {
                throw new IllegalStateException("Morrow or the Onslaught could not be fitted", e);
            }
            placement.beforeTick(s -> {
                long tick = s.getSimTickIndex();
                Tender tender = morrow[0];
                if (tick == 1) {
                    if (tender.lay(BuildOption.HEAVY_MG_TURRET, sites[0], sites[1], s.getGrid()) == null)
                        System.out.println("    [morrow] heavy MG site refused");
                    if (tender.lay(BuildOption.LOCUST_TURRET, sites[2], sites[3], s.getGrid()) == null)
                        System.out.println("    [morrow] Locust nest site refused");
                    if (tender.lay(BuildOption.LOCUST_FIRE_BASE, sites[4], sites[5], s.getGrid()) == null)
                        System.out.println("    [morrow] Locust fire base site refused");
                }
                if (tick == 2) {
                    ConstructionOrder fireBase = tender.orders().get(tender.orders().size() - 1);
                    int[] at = TenderSystem.gunCell(fireBase);
                    System.out.printf("    [morrow] fire base gun cell %d,%d (marine on %d,%d)%n", at[0], at[1], gun[0], gun[1]);
                }
                if (tick == MARINE_LEAVES) s.takeOffTheField(marine);
                HighlightOverlay highlights = placement.highlights();
                if (tick == 1) {
                    int gx = (int) Math.floor(ghosts[0]), gy = (int) Math.floor(ghosts[1]);
                    highlights.setBuildPreview("ghost-valid", BuildPreview.of(s, tender, BuildOption.LOCUST_TURRET,
                            gx, gy, tender.siteOpen(BuildOption.LOCUST_TURRET, gx + 0.5f, gy + 0.5f, s.getGrid())));
                    int rx = (int) Math.floor(sites[0]) + 1, ry = (int) Math.floor(sites[1]);
                    highlights.setBuildPreview("ghost-refused", BuildPreview.of(s, tender, BuildOption.LOCUST_FIRE_BASE,
                            rx, ry, tender.siteOpen(BuildOption.LOCUST_FIRE_BASE, rx + 0.5f, ry + 0.5f, s.getGrid())));
                }
                if (tick == GHOSTS_UNTIL) highlights.clearBuildPreviews();
                if (tick % 60 == 0) {
                    ConstructionOrder o = tender.current();
                    System.out.printf("    [morrow] t=%4.1fs at %.0f,%.0f  %s%n", tick / 30f, tender.anchorX(),
                            tender.anchorY(), o == null ? "built " + tender.built()
                                    : o.option().label() + " " + o.phase() + " " + Math.round(o.fraction() * 100f) + "%"
                                    + " stages " + o.stagesLaid()
                                    + (o.contested() ? " CONTESTED" : "") + (o.gunHeld() ? " GUN HELD" : ""));
                }
            });
            System.out.printf("    [morrow] Morrow %.1f x %.1f cells, Onslaught %.1f cells long%n",
                    morrow[0].ship().lowHoverWidth(), morrow[0].ship().lowHoverHeight(), onslaught[0].lengthCells);
            return sim;
        }, placement -> List.of(
                new LookReview.View("wide", s -> new float[]{
                        (morrow[0].anchorX() + onslaught[0].x) / 2f, (morrow[0].anchorY() + onslaught[0].y) / 2f + 6f, 100f}),
                new LookReview.View("morrow", s -> new float[]{morrow[0].anchorX(), morrow[0].anchorY(), 36f}),
                new LookReview.View("sites", s -> new float[]{(sites[0] + sites[4]) / 2f,
                        (sites[1] + sites[3] + sites[5]) / 3f, 44f}),
                new LookReview.View("heavy-mg", s -> new float[]{sites[0], sites[1], 12f}),
                new LookReview.View("nest", s -> new float[]{sites[2], sites[3], 14f}),
                new LookReview.View("fire-base", s -> new float[]{sites[4], sites[5], 18f}),
                new LookReview.View("ghosts", s -> new float[]{(ghosts[0] + sites[0]) / 2f, (ghosts[1] + sites[1]) / 2f,
                        BattleReviewFrameRenderer.PLAY_ZOOM_CELLS})),
                30 * SECONDS, EVERY_TICKS, STILLS.length, STILLS);
        return LookReview.record(context.modRoot(), shot);
    }
}
