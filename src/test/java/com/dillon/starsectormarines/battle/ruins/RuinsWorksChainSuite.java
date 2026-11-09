package com.dillon.starsectormarines.battle.ruins;

import com.dillon.starsectormarines.battle.flyby.FlybyRoster;
import com.dillon.starsectormarines.battle.ruins.PowerGraph.Branch;
import com.dillon.starsectormarines.battle.salvage.SalvageCache;
import com.dillon.starsectormarines.battle.setup.BattleSetup;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.ui.picking.Selection;
import com.dillon.starsectormarines.battle.ui.picking.SelectionOrders;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.gen.EconomicFunction;
import com.dillon.starsectormarines.battle.world.gen.GroundDefences;
import com.dillon.starsectormarines.battle.world.gen.SettlementLink;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.campaign.ruins.RuinsGrade;
import com.dillon.starsectormarines.campaign.ruins.RuinsOccupant;
import com.dillon.starsectormarines.ops.OperationTier;
import com.dillon.starsectormarines.ops.RiskLevel;
import com.dillon.starsectormarines.ops.battleview.BattleReviewFrameRenderer;
import com.dillon.starsectormarines.testsupport.DiskRegistries;
import com.dillon.starsectormarines.testsupport.TileRegistryTestInstaller;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * The works' chain played on an empty scattered site ({@code ruins-works.md}), through the look preset at 1920x1080: one
 * company squad is walked to the power hall as the player walks it (a move order), holds the console through the works'
 * own prompt verb, is walked to the first junction and throws it to the store, then is sent to the open store's door
 * and holds there until the store's find is recovered. Nothing is set on the works directly; each step waits on the
 * battle. The company is the player's ({@code playerCommandsCompany}), so mission command does not take the squad
 * back between orders. One log line per step goes to stdout.
 * {@code -Dsnapshot.ruinsWorksChain.seed} picks the site (default 31). Opt-in: {@code -Psnapshot=ruins-works-chain}.
 */
public final class RuinsWorksChainSuite implements SnapshotSuite {

    private static final int W = 1920, H = 1080;
    /** The longest any one walk may take before the chain is reported stuck. */
    private static final float WALK_LIMIT_SECONDS = 150f;

    @Override public String id() { return "ruins-works-chain"; }

    @Override public String label() { return "Ruins works: restart the hall, throw a junction, open a store, played"; }

    @Override public boolean inAll() { return false; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        DiskRegistries.installMapGeneration(context.projectRoot());
        TileRegistryTestInstaller.install(context.projectRoot());
        long seed = Long.getLong("snapshot.ruinsWorksChain.seed", 31L);
        List<SnapshotArtifact> out = new ArrayList<>();
        try (BattleSimulation sim = BattleSetup.createRuins(seed, BattleSetup.defaultManifest(), false,
                OperationTier.FIRST_CONTRACT, RiskLevel.MEDIUM, profile(), FlybyRoster.EMPTY, FlybyRoster.EMPTY,
                RuinsGrade.SCATTERED, RuinsOccupant.NOBODY)) {
            RuinsWorks works = sim.getRuinsWorks();
            // The company is the player's, as in a played battle: mission command gives its squads no objective.
            sim.playerCommandsCompany();
            sim.installFieldSalvage();
            advance(sim, 25f);
            Squad squad = nearestSquad(sim, works.hall().x(), works.hall().y());
            log(sim, "landed; squad " + squad.id + " walks to the hall at " + works.hall().x() + "," + works.hall().y()
                    + "; defenders alive " + defenders(sim));

            walk(sim, squad, works.hall().x(), works.hall().y());
            out.add(frame(context, sim, "01-at-the-cold-hall.png", works.hall().x(), works.hall().y()));
            works.choose(RuinsWorks.HALL, "restart");
            float t = 0f;
            while (!works.graph().running() && t < RuinsWorks.RESTART_SECONDS + 3f) {
                advance(sim, BattleSimulation.TICK_DT);
                t += BattleSimulation.TICK_DT;
            }
            log(sim, "hall running " + works.graph().running() + " after " + t + " s; load " + works.graph().load()
                    + " of " + works.graph().capacity());
            out.add(frame(context, sim, "02-hall-restarted.png", works.hall().x(), works.hall().y()));

            RuinsWorks.Site box = works.junctions().get(0);
            walk(sim, squad, box.x(), box.y());
            works.choose(box.key(), Branch.STORE.name());
            advance(sim, RuinsWorks.THROW_SECONDS + 0.5f);
            SealDoor store = works.doors().get(0);
            log(sim, "junction 1 feeds " + works.graph().position(0) + "; " + store.name() + " open " + works.isOpen(0));
            int[] door = store.middle();
            out.add(frame(context, sim, "03-junction-thrown-store-open.png", door[0], door[1]));

            int[] in = store.cells()[0];
            walk(sim, squad, in[0] - Integer.signum(store.likeX() - in[0]), in[1] - Integer.signum(store.likeY() - in[1]));
            // The squad holds at the open door, as a player sends it, until the store's find is recovered.
            SalvageCache cache = works.storeCaches().get(0);
            t = 0f;
            while (!cache.secured() && t < SalvageCache.SECURE_SECONDS + 10f) {
                advance(sim, 1f);
                t += 1f;
            }
            log(sim, "store held " + t + " s; recovered " + sim.fieldSalvage().recovered());
            out.add(frame(context, sim, "04-store-held-find-recovered.png", door[0], door[1]));
        }
        return out;
    }

    private static void walk(BattleSimulation sim, Squad squad, int x, int y) {
        Selection selection = new Selection();
        selection.selectSquad(squad.id);
        SelectionOrders.requestMove(sim, selection, x, y);
        float t = 0f;
        float still = 0f;
        int lastX = -1, lastY = -1;
        while (t < WALK_LIMIT_SECONDS) {
            advance(sim, 1f);
            t += 1f;
            long leader = squad.leaderId;
            if (sim.world().isAlive(leader) && Math.hypot(sim.world().x(leader) - (x + .5f),
                    sim.world().y(leader) - (y + .5f)) <= RuinsWorks.WORK_CELLS) break;
            // Arrived where the order holds it, which for a building is its places, not the cell clicked.
            int cx = sim.world().cellX(leader), cy = sim.world().cellY(leader);
            still = cx == lastX && cy == lastY ? still + 1f : 0f;
            lastX = cx;
            lastY = cy;
            if (still >= 4f) break;
        }
        log(sim, "walked to " + x + "," + y + " in " + t + " s; leader at " + sim.world().cellX(squad.leaderId) + ","
                + sim.world().cellY(squad.leaderId));
    }

    private static Squad nearestSquad(BattleSimulation sim, int x, int y) {
        Squad best = null;
        double bestD = Double.MAX_VALUE;
        for (Squad squad : sim.getSquads()) {
            if (squad.faction != Faction.MARINE || squad.isMechSquad() || !sim.world().isAlive(squad.leaderId)) continue;
            double d = Math.hypot(sim.world().x(squad.leaderId) - x, sim.world().y(squad.leaderId) - y);
            if (d < bestD) {
                bestD = d;
                best = squad;
            }
        }
        if (best == null) throw new IllegalStateException("no company squad on the ground");
        return best;
    }

    private static int defenders(BattleSimulation sim) {
        int n = 0;
        for (int i = 0, c = sim.liveUnitCount(); i < c; i++) {
            if (sim.identity().faction(sim.liveUnitAt(i)) == Faction.DEFENDER) n++;
        }
        return n;
    }

    private static void advance(BattleSimulation sim, float seconds) {
        for (int i = 0; i < Math.round(seconds / BattleSimulation.TICK_DT); i++) sim.advance(BattleSimulation.TICK_DT);
    }

    private static void log(BattleSimulation sim, String line) {
        System.out.println("[ruins-works-chain] tick " + sim.getSimTickIndex() + ": " + line);
    }

    private static SnapshotArtifact frame(SnapshotContext context, BattleSimulation sim, String name, float cx, float cy)
            throws Exception {
        return new SnapshotArtifact(name, BattleReviewFrameRenderer.look(context.modRoot(), W, H, cx, cy,
                BattleReviewFrameRenderer.PLAY_ZOOM_CELLS).withoutRoofs().render(sim, ""));
    }

    private static TargetProfile profile() {
        return new TargetProfile(0, 0, 0, 0, "", EnumSet.noneOf(EconomicFunction.class), SurfacePalette.ARID,
                SettlementLink.NONE, 0f, 0f, false, false, false, GroundDefences.NONE);
    }
}
