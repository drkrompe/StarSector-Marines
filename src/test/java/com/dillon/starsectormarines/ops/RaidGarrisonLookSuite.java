package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.flyby.FlybyRoster;
import com.dillon.starsectormarines.battle.setup.BattleSetup;
import com.dillon.starsectormarines.battle.setup.RaidWorksLayout;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.gen.EconomicFunction;
import com.dillon.starsectormarines.battle.world.gen.SettlementLink;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.gen.fortress.Cantonment;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import com.dillon.starsectormarines.ops.battleview.BattleReviewFrameRenderer;
import com.dillon.starsectormarines.testsupport.HeadlessBattles;
import com.dillon.starsectormarines.testsupport.HeadlessStrings;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;
import com.dillon.starsectormarines.ui.Fonts;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The Raid site's garrison district laid as a cantonment ({@code raid-mission.md}, Garrison district), one look per
 * {@link Cantonment.Plan} across market sizes and security grades: the garrison framed whole
 * ({@code <plan>-<case>-garrison.png}) and the whole map ({@code <plan>-<case>-whole.png}), look preset, 1920x1080.
 * The plan is chosen through {@link Cantonment#PLAN_PROPERTY} for each set; nothing is played.
 */
public final class RaidGarrisonLookSuite implements SnapshotSuite {

    private record Case(String id, long seed, int marketSize, int defence, OperationTier tier) { }

    private static final List<Case> CASES = List.of(
            new Case("size4-open", 141_418L, 4, 1, OperationTier.ESTABLISHED),
            new Case("size5-guarded", 7L, 5, 3, OperationTier.ESTABLISHED),
            new Case("size7-fortified", 141_418L, 7, 5, OperationTier.REINFORCED));

    @Override public String id() { return "raid-garrison-look"; }

    @Override public String label() { return "The Raid site's garrison district in rows, each plan, look preset"; }

    @Override public boolean inAll() { return false; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        String only = System.getProperty(Cantonment.PLAN_PROPERTY, "");
        String before = System.getProperty(Cantonment.PLAN_PROPERTY);
        try (HeadlessStrings strings = HeadlessBattles.install(context.projectRoot())) {
            DustLedgerMissionPreviewSuite.installFonts(context, Fonts.ORBITRON_12_BOLD, Fonts.INSIGNIA_15_AA);
            List<SnapshotArtifact> out = new ArrayList<>();
            for (Cantonment.Plan plan : Cantonment.Plan.values()) {
                if (!only.isBlank() && !plan.name().equalsIgnoreCase(only.trim())) continue;
                System.setProperty(Cantonment.PLAN_PROPERTY, plan.name());
                String key = plan.name().toLowerCase(Locale.ROOT);
                for (Case c : CASES) {
                    TargetProfile market = new TargetProfile(c.marketSize(), 5, c.defence(), 2, "independent",
                            EnumSet.of(EconomicFunction.HEAVY_INDUSTRY, EconomicFunction.SPACEPORT,
                                    EconomicFunction.HABITATION),
                            SurfacePalette.ROCK, SettlementLink.ROAD);
                    try (BattleSimulation sim = BattleSetup.createRaidWorks(c.seed(), BattleSetup.defaultManifest(),
                            false, c.tier(), RiskLevel.MEDIUM, market, FlybyRoster.EMPTY, FlybyRoster.EMPTY, null,
                            null, RaidWorksLayout.machinesFor(c.tier()), "heavyindustry")) {
                        int w = sim.getGrid().getWidth(), h = sim.getGrid().getHeight();
                        float[] hq = headquarters(sim.getTopology(), w, h);
                        System.out.printf(Locale.ROOT, "[%s] %s %s headquarters at %.0f,%.0f%n", id(), key, c.id(),
                                hq[0], hq[1]);
                        out.add(new SnapshotArtifact(key + "-" + c.id() + "-garrison.png",
                                BattleReviewFrameRenderer.look(context.modRoot(), 1920, 1080, hq[2], hq[3],
                                        hq[4]).withMissionMarkers().render(sim, "")));
                        out.add(new SnapshotArtifact(key + "-" + c.id() + "-whole.png",
                                BattleReviewFrameRenderer.look(context.modRoot(), 1920, 1080, w / 2f, h / 2f,
                                        Math.max(w, h * 16f / 9f) + 4f).withMissionMarkers().render(sim, "")));
                    }
                }
            }
            return out;
        } finally {
            if (before == null) System.clearProperty(Cantonment.PLAN_PROPERTY);
            else System.setProperty(Cantonment.PLAN_PROPERTY, before);
        }
    }

    /** Cells from the HQ a garrison building may stand: more than any cantonment's square is across. */
    private static final int REACH = 90;

    /** Cells past the military floor a depot building may stand and still be the garrison's. */
    private static final int DEPOT_REACH = 30;

    private static final Set<RoomPurpose> MILITARY = EnumSet.of(RoomPurpose.BARRACKS, RoomPurpose.KEEP_THRONE,
            RoomPurpose.MESS_HALL, RoomPurpose.VEHICLE_BAY, RoomPurpose.KEEP_ENTRY, RoomPurpose.ARMORY,
            RoomPurpose.CONTROL_ROOM, RoomPurpose.HANGAR);
    private static final Set<RoomPurpose> DEPOT = EnumSet.of(RoomPurpose.STOCKROOM, RoomPurpose.PARTS_CAGE,
            RoomPurpose.ENGINE_ROOM);

    /**
     * The headquarters' middle and the garrison's frame: {hqX, hqY, frameX, frameY, cellsAcross}. The garrison is
     * the military floor (barracks, HQ, posts, motor pool, magazines, the flight line's hangars) within {@link #REACH}
     * of the HQ, and the depot's stores, workshops and generator within {@link #DEPOT_REACH} of that: near enough to
     * be the garrison's, not the works' beside it.
     */
    private static float[] headquarters(CellTopology topology, int w, int h) {
        long sx = 0, sy = 0, n = 0;
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                if (topology.getRoomPurpose(x, y) != RoomPurpose.KEEP_THRONE) continue;
                sx += x;
                sy += y;
                n++;
            }
        }
        if (n == 0) return new float[]{w / 2f, h / 2f, w / 2f, h / 2f, w};
        float hx = sx / (float) n, hy = sy / (float) n;
        int[] box = {w, h, -1, -1};
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                if (!MILITARY.contains(topology.getRoomPurpose(x, y))) continue;
                if (Math.abs(x - hx) > REACH || Math.abs(y - hy) > REACH) continue;
                grow(box, x, y);
            }
        }
        int[] military = box.clone();
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                if (!DEPOT.contains(topology.getRoomPurpose(x, y))) continue;
                if (x < military[0] - DEPOT_REACH || x > military[2] + DEPOT_REACH
                        || y < military[1] - DEPOT_REACH || y > military[3] + DEPOT_REACH) continue;
                grow(box, x, y);
            }
        }
        float fx = (box[0] + box[2]) / 2f + .5f, fy = (box[1] + box[3]) / 2f + .5f;
        float across = Math.max(box[2] - box[0] + 1, (box[3] - box[1] + 1) * 16f / 9f) + 24f;
        return new float[]{hx, hy, fx, fy, across};
    }

    private static void grow(int[] box, int x, int y) {
        box[0] = Math.min(box[0], x);
        box[1] = Math.min(box[1], y);
        box[2] = Math.max(box[2], x);
        box[3] = Math.max(box[3], y);
    }
}
