package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.gen.EconomicFunction;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.SettlementLink;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.gen.precinct.PrecinctPlan;
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
 * Raid map sizes for the owner's pick (owner: "Raid maps are too small"): each option's Raid map beside an
 * Extraction map of the same tier, both whole and at one shared scale, so the sizes compare. {@code today} is the
 * tier band ({@link MapScale#forTier}), {@code one-step} is {@link MapScale#forRaid} (built), {@code two-step} a step
 * past it. Two tiers per option: established ({@code *-established.png}, {@value #ESTABLISHED_CELLS} cells across
 * each half) and reinforced ({@code *-reinforced.png}, {@value #REINFORCED_CELLS} cells across). Each Raid is also
 * drawn alone fitted to the frame ({@code *-raid-fit.png}). Ground only, through the look preset, roofs on; seed and
 * market are the Raid evidence fixture's (a spaceport and heavy industry on rock).
 */
public final class RaidScaleOptionsSuite implements SnapshotSuite {

    private static final int W = 1920, H = 1080, HALF = W / 2;
    /** Wide enough for the two-step established Raid. */
    static final int ESTABLISHED_CELLS = 300;
    /** Wide enough for the reinforced Raid at its largest option. */
    static final int REINFORCED_CELLS = 420;
    private static final long SEED = 141_418L;
    private static final TargetProfile MARKET = new TargetProfile(
            6, 5, 4, 2, "independent",
            EnumSet.of(EconomicFunction.SPACEPORT, EconomicFunction.HEAVY_INDUSTRY),
            SurfacePalette.ROCK, SettlementLink.ROAD);

    private record Option(String key, MapScale established, MapScale reinforced) {}

    private static final List<Option> OPTIONS = List.of(
            new Option("today", MapScale.forTier(OperationTier.ESTABLISHED), MapScale.forTier(OperationTier.REINFORCED)),
            new Option("one-step", MapScale.forRaid(OperationTier.ESTABLISHED), MapScale.forRaid(OperationTier.REINFORCED)),
            new Option("two-step", MapScale.LARGE, MapScale.VAST));

    @Override public String id() { return "raid-scale-options"; }

    @Override public String label() { return "Raid map size options beside an Extraction of the same tier"; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        DiskRegistries.installMapGeneration(context.projectRoot());
        TileRegistryTestInstaller.install(context.projectRoot());
        List<SnapshotArtifact> out = new ArrayList<>();
        BufferedImage extractionEstablished = half(context, map(MissionType.EXTRACTION, OperationTier.ESTABLISHED,
                MapScale.forTier(OperationTier.ESTABLISHED)), ESTABLISHED_CELLS);
        BufferedImage extractionReinforced = half(context, map(MissionType.EXTRACTION, OperationTier.REINFORCED,
                MapScale.forTier(OperationTier.REINFORCED)), REINFORCED_CELLS);
        for (Option option : OPTIONS) {
            MapResult established = map(MissionType.RAID, OperationTier.ESTABLISHED, option.established());
            out.add(new SnapshotArtifact(option.key() + "-established.png", pair(
                    half(context, established, ESTABLISHED_CELLS), "Raid, established  " + size(option.established()),
                    extractionEstablished, "Extraction, established  " + size(MapScale.forTier(OperationTier.ESTABLISHED)))));
            MapResult reinforced = map(MissionType.RAID, OperationTier.REINFORCED, option.reinforced());
            out.add(new SnapshotArtifact(option.key() + "-reinforced.png", pair(
                    half(context, reinforced, REINFORCED_CELLS), "Raid, reinforced  " + size(option.reinforced()),
                    extractionReinforced, "Extraction, reinforced  " + size(MapScale.forTier(OperationTier.REINFORCED)))));
            try (BattleSimulation sim = BattleSetup.buildMap(established, List.of(), established.defensePosts,
                    List.of(), SEED).sim()) {
                int width = sim.getGrid().getWidth(), height = sim.getGrid().getHeight();
                out.add(new SnapshotArtifact(option.key() + "-raid-fit.png", BattleReviewFrameRenderer.look(
                        context.modRoot(), W, H, width / 2f, height / 2f, width * 1.04f).render(sim, "")));
            }
        }
        return out;
    }

    private static MapResult map(MissionType type, OperationTier tier, MapScale scale) {
        RiskLevel risk = RiskLevel.MEDIUM;
        PrecinctPlan precincts = BattleSetup.precinctPlanFor(type, tier, risk, MARKET, scale, SEED, null);
        return BattleSetup.proceduralOperationMap(type, tier, risk, MARKET, scale, SEED, null, precincts);
    }

    private static BufferedImage half(SnapshotContext context, MapResult map, int cellsAcross) throws Exception {
        try (BattleSimulation sim = BattleSetup.buildMap(map, List.of(), map.defensePosts, List.of(), SEED).sim()) {
            int width = sim.getGrid().getWidth(), height = sim.getGrid().getHeight();
            return BattleReviewFrameRenderer.look(context.modRoot(), HALF, H, width / 2f, height / 2f, cellsAcross)
                    .render(sim, "");
        }
    }

    private static BufferedImage pair(BufferedImage left, String leftLabel, BufferedImage right, String rightLabel) {
        BufferedImage frame = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = frame.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.drawImage(left, 0, 0, null);
        g.drawImage(right, HALF, 0, null);
        g.setColor(new Color(20, 20, 20));
        g.setStroke(new BasicStroke(4f));
        g.drawLine(HALF, 0, HALF, H);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 28));
        label(g, leftLabel, 24, 48);
        label(g, rightLabel, HALF + 24, 48);
        g.dispose();
        return frame;
    }

    private static void label(Graphics2D g, String text, int x, int y) {
        g.setColor(new Color(0, 0, 0, 170));
        g.fillRect(x - 10, y - 34, g.getFontMetrics().stringWidth(text) + 20, 46);
        g.setColor(Color.WHITE);
        g.drawString(text, x, y);
    }

    private static String size(MapScale scale) {
        return scale.width + "x" + scale.height;
    }
}
