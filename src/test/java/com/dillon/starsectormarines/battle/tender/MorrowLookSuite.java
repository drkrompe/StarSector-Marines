package com.dillon.starsectormarines.battle.tender;

import com.dillon.starsectormarines.battle.power.SpawnInOnslaught;
import com.dillon.starsectormarines.battle.scene.LookGround;
import com.dillon.starsectormarines.battle.scene.LookReview;
import com.dillon.starsectormarines.battle.scene.SceneRegistries;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.warship.ShipSupportSource;
import com.dillon.starsectormarines.battle.warship.Warship;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.testsupport.InstalledHullSpecs;
import com.dillon.starsectormarines.testsupport.InstalledVanillaData;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;

import java.util.List;

/**
 * Morrow in the look preset (`tender-ship.md`, Vulnerability): the recovering Shepherd in her Working Passage low hover
 * at the Onslaught power's presence, beside an Onslaught at that same presence for scale, flying across arid ground
 * with her defence drones out while her builders put up the two recovery guns, a heavy MG turret and a Locust battery.
 * A wide camera on both hulls, one riding Morrow, one on the two sites and one close on each gun. Logs the sites' progress every two seconds.
 */
public class MorrowLookSuite implements SnapshotSuite {

    private static final int W = 110, H = 60;
    private static final int SECONDS = 70, EVERY_TICKS = 3;

    @Override public String id() { return "morrow-look"; }

    @Override public String label() { return "Look: Morrow flying beside an Onslaught, her drones out, building a heavy MG and a Locust battery"; }

    @Override public boolean inAll() { return false; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        SceneRegistries.installArmoury(context.projectRoot());
        InstalledHullSpecs.install();
        InstalledVanillaData.install();
        Tender[] morrow = new Tender[1];
        Warship[] onslaught = new Warship[1];
        float[] sites = new float[4];
        LookReview.Shot shot = new LookReview.Shot("morrow", LookGround.of(SurfacePalette.ARID, 7L), W, H, placement -> {
            BattleSimulation sim = placement.holdouts(placement.builder()).build().sim();
            float[] start = placement.openNear(22f, 28f);
            float[] goal = placement.openNear(56f, 26f);
            float[] heavy = placement.openNear(30f, 44f);
            float[] locust = placement.openNear(46f, 46f);
            float[] station = placement.openNear(84f, 30f);
            sites[0] = heavy[0]; sites[1] = heavy[1]; sites[2] = locust[0]; sites[3] = locust[1];
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
                        System.out.println("    [morrow] Locust site refused");
                }
                if (tick % 60 == 0) {
                    ConstructionOrder o = tender.current();
                    System.out.printf("    [morrow] t=%4.1fs at %.0f,%.0f  %s%n", tick / 30f, tender.anchorX(),
                            tender.anchorY(), o == null ? "built " + tender.built()
                                    : o.option().label() + " " + o.phase() + " " + Math.round(o.fraction() * 100f) + "%"
                                    + (o.contested() ? " CONTESTED" : ""));
                }
            });
            System.out.printf("    [morrow] Morrow %.1f x %.1f cells, Onslaught %.1f cells long%n",
                    morrow[0].ship().lowHoverWidth(), morrow[0].ship().lowHoverHeight(), onslaught[0].lengthCells);
            return sim;
        }, placement -> List.of(
                new LookReview.View("wide", s -> new float[]{
                        (morrow[0].anchorX() + onslaught[0].x) / 2f, (morrow[0].anchorY() + onslaught[0].y) / 2f + 6f, 100f}),
                new LookReview.View("morrow", s -> new float[]{morrow[0].anchorX(), morrow[0].anchorY(), 36f}),
                new LookReview.View("sites", s -> new float[]{(sites[0] + sites[2]) / 2f, (sites[1] + sites[3]) / 2f, 30f}),
                new LookReview.View("heavy-mg", s -> new float[]{sites[0], sites[1], 12f}),
                new LookReview.View("locust", s -> new float[]{sites[2], sites[3], 12f})),
                30 * SECONDS, EVERY_TICKS, 4, new int[]{40, 160, 420, 699});
        return LookReview.record(context.modRoot(), shot);
    }
}
