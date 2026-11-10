package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.door.WallGate;
import com.dillon.starsectormarines.battle.flyby.FlybyRoster;
import com.dillon.starsectormarines.battle.setup.BattleSetup;
import com.dillon.starsectormarines.battle.setup.RaidWorksLayout;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.sim.World;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.gen.EconomicFunction;
import com.dillon.starsectormarines.battle.world.gen.SettlementLink;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.ops.battleview.BattleReviewFrameRenderer;
import com.dillon.starsectormarines.testsupport.HeadlessBattles;
import com.dillon.starsectormarines.testsupport.HeadlessStrings;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;
import com.dillon.starsectormarines.ui.Fonts;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;

/**
 * A raid garrison's main gate ({@code precincts.md}, Gates are leaf doors) in its three states, look preset,
 * 1920x1080: shut with nobody near ({@code gate-shut.png}), open for a vehicle driving through ({@code gate-open.png}),
 * and forced, its jamb blown ({@code gate-forced.png}). Nothing is played: the bodies are set where the shots need
 * them and the jamb is opened as a breach would.
 */
public final class RaidGateLookSuite implements SnapshotSuite {

    /** Cells across a gate shot. */
    private static final float ACROSS = 30f;

    @Override public String id() { return "raid-gate-look"; }

    @Override public String label() { return "A raid garrison's main gate shut, open and forced, look preset"; }

    @Override public boolean inAll() { return false; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        try (HeadlessStrings strings = HeadlessBattles.install(context.projectRoot())) {
            DustLedgerMissionPreviewSuite.installFonts(context, Fonts.ORBITRON_12_BOLD, Fonts.INSIGNIA_15_AA);
            TargetProfile market = new TargetProfile(7, 5, 5, 2, "independent",
                    EnumSet.of(EconomicFunction.HEAVY_INDUSTRY, EconomicFunction.SPACEPORT, EconomicFunction.HABITATION),
                    SurfacePalette.ROCK, SettlementLink.ROAD);
            List<SnapshotArtifact> out = new ArrayList<>();
            try (BattleSimulation sim = BattleSetup.createRaidWorks(141_418L, BattleSetup.defaultManifest(), false,
                    OperationTier.REINFORCED, RiskLevel.MEDIUM, market, FlybyRoster.EMPTY, FlybyRoster.EMPTY, null,
                    null, RaidWorksLayout.machinesFor(OperationTier.REINFORCED), "heavyindustry")) {
                WallGate gate = widest(sim.wallGates());
                if (gate == null) throw new IllegalStateException("the raid site hung no gates");
                float gx = gate.spanX() ? (gate.min() + gate.max() + 1) / 2f : gate.across() + .5f;
                float gy = gate.spanX() ? gate.across() + .5f : (gate.min() + gate.max() + 1) / 2f;
                System.out.printf(Locale.ROOT, "[%s] %d gates; main %s at %.1f,%.1f, %d wide%n", id(),
                        sim.wallGates().size(), gate.spanX() ? "across x" : "across y", gx, gy, gate.width());
                clear(sim, gx, gy);
                out.add(new SnapshotArtifact("gate-shut.png", shot(context, sim, gx, gy)));
                long driver = driver(sim);
                System.out.printf(Locale.ROOT, "[%s] driving %s through%n", id(),
                        sim.getRoster().identity().type(driver));
                sim.world().setPos(driver, gx, gy);
                out.add(new SnapshotArtifact("gate-open.png", shot(context, sim, gx, gy)));
                clear(sim, gx, gy);
                int[] jamb = gate.jambs()[0];
                int likeX = gate.spanX() ? gate.min() : gate.across(), likeY = gate.spanX() ? gate.across() : gate.min();
                sim.mapEditor().openPassage(jamb[0], jamb[1], likeX, likeY);
                out.add(new SnapshotArtifact("gate-forced.png", shot(context, sim, gx, gy)));
            }
            return out;
        }
    }

    private static BufferedImage shot(SnapshotContext context, BattleSimulation sim, float x, float y)
            throws Exception {
        return BattleReviewFrameRenderer.look(context.modRoot(), 1920, 1080, x, y, ACROSS).render(sim, "");
    }

    /** Moves every body near the gate well away from it, so nobody calls it. */
    private static void clear(BattleSimulation sim, float x, float y) {
        World world = sim.world();
        UnitRosterService roster = sim.getRoster();
        for (int k = 0; k < roster.liveCount(); k++) {
            long id = roster.get(k);
            if (!world.hasPosition(id)) continue;
            if (Math.abs(world.x(id) - x) < ACROSS && Math.abs(world.y(id) - y) < ACROSS) {
                world.setPos(id, world.x(id) + 3 * ACROSS, world.y(id));
            }
        }
    }

    /** What drives through the gate: a ground vehicle, else a mech, else anything that moves; never an emplacement. */
    private static long driver(BattleSimulation sim) {
        UnitRosterService roster = sim.getRoster();
        long mech = -1, mover = -1;
        for (int k = 0; k < roster.liveCount(); k++) {
            long id = roster.get(k);
            UnitType type = roster.identity().type(id);
            if (type.isGroundVehicle()) return id;
            if (type.isMech() && mech < 0) mech = id;
            if (!type.isStatic() && mover < 0) mover = id;
        }
        if (mech >= 0) return mech;
        if (mover >= 0) return mover;
        throw new IllegalStateException("nothing on the map moves");
    }

    private static WallGate widest(List<WallGate> gates) {
        WallGate best = null;
        for (WallGate g : gates) if (best == null || g.width() > best.width()) best = g;
        return best;
    }
}
