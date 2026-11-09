package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.gen.EconomicFunction;
import com.dillon.starsectormarines.battle.world.gen.LandingPad;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.SettlementLink;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.gen.precinct.PrecinctPlan;
import com.dillon.starsectormarines.battle.world.gen.precinct.Standoff;
import com.dillon.starsectormarines.battle.world.model.MapScale;
import com.dillon.starsectormarines.ops.MissionType;
import com.dillon.starsectormarines.ops.OperationTier;
import com.dillon.starsectormarines.ops.RiskLevel;
import com.dillon.starsectormarines.ops.battleview.BattleReviewFrameRenderer;
import com.dillon.starsectormarines.testsupport.DiskRegistries;
import com.dillon.starsectormarines.testsupport.TileRegistryTestInstaller;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * Where a Raid lands, for the owner's pick (owner: "we don't need to keep landing zones at the edge of the map"):
 * {@code edge} is {@link Standoff#FAR}, the berths scanned in from the map edge as every precinct map had them, and
 * {@code inset} is {@link Standoff#INSET}, the arrival band slid {@value Standoff#INSET_DEPTH} cells into the town
 * (built). Whole maps through the look preset, roofs on, at a first contract ({@code *-first-contract.png}) and
 * established ({@code *-established.png}), with the berths the company's four shuttles take outlined in yellow and the
 * prize the Raid picks from them ringed in red. Seed and market are the Raid evidence fixture's. Ground only: nothing is
 * played.
 */
public final class RaidLandingOptionsSuite implements SnapshotSuite {

    private static final int W = 1920, H = 1080;
    private static final long SEED = 141_418L;
    private static final int SHUTTLES = 4;
    private static final TargetProfile MARKET = new TargetProfile(
            6, 5, 4, 2, "independent",
            EnumSet.of(EconomicFunction.SPACEPORT, EconomicFunction.HEAVY_INDUSTRY),
            SurfacePalette.ROCK, SettlementLink.ROAD);

    @Override public String id() { return "raid-landing-options"; }

    @Override public String label() { return "Raid landing zones at the map edge against inset into the town"; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        DiskRegistries.installMapGeneration(context.projectRoot());
        TileRegistryTestInstaller.install(context.projectRoot());
        List<SnapshotArtifact> out = new ArrayList<>();
        for (OperationTier tier : List.of(OperationTier.FIRST_CONTRACT, OperationTier.ESTABLISHED)) {
            String tierName = tier == OperationTier.FIRST_CONTRACT ? "first-contract" : "established";
            for (Standoff standoff : List.of(Standoff.FAR, Standoff.INSET)) {
                String key = standoff == Standoff.FAR ? "edge" : "inset";
                out.add(new SnapshotArtifact(key + "-" + tierName + ".png", frame(context, tier, standoff,
                        (standoff == Standoff.FAR ? "Edge" : "Inset " + Standoff.INSET_DEPTH + " cells")
                                + ", Raid " + tierName.replace('-', ' '))));
            }
        }
        return out;
    }

    private static BufferedImage frame(SnapshotContext context, OperationTier tier, Standoff standoff, String label)
            throws Exception {
        RiskLevel risk = RiskLevel.MEDIUM;
        MapScale scale = MapScale.forRaid(tier);
        PrecinctPlan precincts = BattleSetup.precinctPlanFor(MissionType.RAID, tier, risk, MARKET, scale, SEED, null)
                .withStandoff(standoff);
        MapResult map = BattleSetup.proceduralOperationMap(MissionType.RAID, tier, risk, MARKET, scale, SEED, null,
                precincts);
        List<LandingPad> pads = LandingPadSelector.select(map, SHUTTLES, BattleSetup.LZ_MIN_SEPARATION);
        RaidTargetLayout target = RaidTargetLayout.select(map, pads);
        BufferedImage image;
        float cellsAcross;
        try (BattleSimulation sim = BattleSetup.buildMap(map, List.of(), map.defensePosts, List.of(), SEED).sim()) {
            int width = sim.getGrid().getWidth();
            cellsAcross = width * 1.04f;
            image = BattleReviewFrameRenderer.look(context.modRoot(), W, H, width / 2f,
                    sim.getGrid().getHeight() / 2f, cellsAcross).render(sim, "");
        }
        float cx = map.grid.getWidth() / 2f, cy = map.grid.getHeight() / 2f;
        // The camera draws whole pixels per cell, the renderer's own rounding.
        int ppc = (int) Math.floor(W / cellsAcross);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setStroke(new BasicStroke(3f));
        g.setColor(new Color(255, 214, 40));
        for (LandingPad pad : pads) {
            int x0 = px(pad.left(), cx, ppc), x1 = px(pad.right() + 1, cx, ppc);
            int y0 = py(pad.top() + 1, cy, ppc), y1 = py(pad.bottom(), cy, ppc);
            g.drawRect(x0, y0, x1 - x0, y1 - y0);
        }
        g.setColor(new Color(235, 50, 40));
        int tx = px(target.targetCellX() + 0.5f, cx, ppc), ty = py(target.targetCellY() + 0.5f, cy, ppc);
        g.drawOval(tx - 22, ty - 22, 44, 44);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 28));
        String text = label + "  " + scale.width + "x" + scale.height;
        g.setColor(new Color(0, 0, 0, 170));
        g.fillRect(14, 14, g.getFontMetrics().stringWidth(text) + 20, 46);
        g.setColor(Color.WHITE);
        g.drawString(text, 24, 48);
        g.dispose();
        return image;
    }

    private static int px(float cellX, float centerX, int ppc) {
        return Math.round(W / 2f + (cellX - centerX) * ppc);
    }

    private static int py(float cellY, float centerY, int ppc) {
        return Math.round(H / 2f - (cellY - centerY) * ppc);
    }
}
