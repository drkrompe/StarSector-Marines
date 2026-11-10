package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.flyby.FlybyRoster;
import com.dillon.starsectormarines.battle.setup.BattleSetup;
import com.dillon.starsectormarines.battle.setup.RaidWorksLayout;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.gen.EconomicFunction;
import com.dillon.starsectormarines.battle.world.gen.SettlementLink;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.gen.bsp.stage.PrecinctWardStage;
import com.dillon.starsectormarines.battle.world.gen.fortress.Cantonment;
import com.dillon.starsectormarines.ops.battleview.BattleReviewFrameRenderer;
import com.dillon.starsectormarines.render2d.BattleCamera;
import com.dillon.starsectormarines.testsupport.HeadlessBattles;
import com.dillon.starsectormarines.testsupport.HeadlessStrings;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;
import com.dillon.starsectormarines.ui.Fonts;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;

/**
 * The Raid site's garrison laid as a cantonment ({@code raid-mission.md}, Garrison district) across market sizes and
 * security grades. For each: the garrison framed whole with a supply truck's round drawn on in orange and the flight
 * line's supply round in blue ({@code garrison-<case>-route.png}), the same frame clean
 * ({@code garrison-<case>-garrison.png}) and the whole map ({@code garrison-<case>-whole.png}); look preset,
 * 1920x1080. Nothing is played.
 */
public final class RaidGarrisonLookSuite implements SnapshotSuite {

    private record Case(String id, long seed, int marketSize, int defence, OperationTier tier) { }

    private static final List<Case> CASES = List.of(
            new Case("size4-open", 141_418L, 4, 1, OperationTier.ESTABLISHED),
            new Case("size5-guarded", 7L, 5, 3, OperationTier.ESTABLISHED),
            new Case("size7-fortified", 141_418L, 7, 5, OperationTier.REINFORCED));

    private static final Color TRUCK = new Color(255, 150, 30);
    private static final Color FLIGHT = new Color(60, 170, 255);

    @Override public String id() { return "raid-garrison-look"; }

    @Override public String label() { return "The Raid site's garrison, its supply rounds drawn on, look preset"; }

    @Override public boolean inAll() { return false; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        try (HeadlessStrings strings = HeadlessBattles.install(context.projectRoot())) {
            DustLedgerMissionPreviewSuite.installFonts(context, Fonts.ORBITRON_12_BOLD, Fonts.INSIGNIA_15_AA);
            List<SnapshotArtifact> out = new ArrayList<>();
            for (Case c : CASES) out.addAll(shots(context, c));
            return out;
        }
    }

    private List<SnapshotArtifact> shots(SnapshotContext context, Case c) throws Exception {
        TargetProfile market = new TargetProfile(c.marketSize(), 5, c.defence(), 2, "independent",
                EnumSet.of(EconomicFunction.HEAVY_INDUSTRY, EconomicFunction.SPACEPORT, EconomicFunction.HABITATION),
                SurfacePalette.ROCK, SettlementLink.ROAD);
        List<Cantonment.Laid> laid = new ArrayList<>();
        PrecinctWardStage.CANTONMENT_OBSERVER.set(laid::add);
        try (BattleSimulation sim = BattleSetup.createRaidWorks(c.seed(), BattleSetup.defaultManifest(), false,
                c.tier(), RiskLevel.MEDIUM, market, FlybyRoster.EMPTY, FlybyRoster.EMPTY, null, null,
                RaidWorksLayout.machinesFor(c.tier()), "heavyindustry")) {
            int w = sim.getGrid().getWidth(), h = sim.getGrid().getHeight();
            if (laid.isEmpty()) throw new IllegalStateException(c.id() + ": no cantonment was laid");
            Cantonment.Laid garrison = laid.get(laid.size() - 1);
            float[] frame = frame(garrison);
            System.out.printf(Locale.ROOT, "[%s] %s garrison at %.0f,%.0f, %d cells across; truck round %d cells, "
                            + "flight round %d, unplaced %s, strays %s%n", id(), c.id(), frame[0], frame[1],
                    (int) frame[2], garrison.truckRoute().size(), garrison.flightRoute().size(), garrison.unplaced(),
                    garrison.strays());
            BufferedImage clean = BattleReviewFrameRenderer.look(context.modRoot(), 1920, 1080, frame[0], frame[1],
                    frame[2]).withMissionMarkers().render(sim, "");
            BufferedImage route = copy(clean);
            Graphics2D g = route.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            // The renderer's own camera, which keeps its view on the map, so the rounds land where the streets are.
            BattleCamera camera = new BattleCamera(w, h);
            camera.setViewport(0, 0, 1920, 1080, (float) Math.floor(1920 / frame[2]));
            camera.centerOn(frame[0], frame[1]);
            draw(g, garrison.truckRoute(), camera, TRUCK, 0);
            draw(g, garrison.flightRoute(), camera, FLIGHT, 1);
            legend(g);
            g.dispose();
            return List.of(
                    new SnapshotArtifact("garrison-" + c.id() + "-route.png", route),
                    new SnapshotArtifact("garrison-" + c.id() + "-garrison.png", clean),
                    new SnapshotArtifact("garrison-" + c.id() + "-whole.png",
                            BattleReviewFrameRenderer.look(context.modRoot(), 1920, 1080, w / 2f, h / 2f,
                                    Math.max(w, h * 16f / 9f) + 4f).withMissionMarkers().render(sim, "")));
        } finally {
            PrecinctWardStage.CANTONMENT_OBSERVER.remove();
        }
    }

    /** The garrison's middle and the cells across a frame that holds all it laid: {x, y, cellsAcross}. */
    private static float[] frame(Cantonment.Laid laid) {
        int[] box = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
        for (List<int[]> cells : List.of(laid.streets(), laid.pool(), laid.apron(), laid.parade())) {
            for (int[] c : cells) grow(box, c[0], c[1]);
        }
        laid.rooms().forEach(r -> {
            grow(box, r.originX(), r.originY());
            grow(box, r.originX() + r.shape().width(), r.originY() + r.shape().height());
        });
        if (laid.airfield() != null) {
            grow(box, laid.airfield()[0], laid.airfield()[1]);
            grow(box, laid.airfield()[2], laid.airfield()[3]);
        }
        float across = Math.max(box[2] - box[0] + 1, (box[3] - box[1] + 1) * 16f / 9f) + 12f;
        return new float[]{(box[0] + box[2]) / 2f + .5f, (box[1] + box[3]) / 2f + .5f, across};
    }

    private static void grow(int[] box, int x, int y) {
        box[0] = Math.min(box[0], x);
        box[1] = Math.min(box[1], y);
        box[2] = Math.max(box[2], x);
        box[3] = Math.max(box[3], y);
    }

    /** A route's cells as a line through their middles, offset a little so two rounds on one street both show. */
    private static void draw(Graphics2D g, List<int[]> route, BattleCamera camera, Color color, int lane) {
        if (route.isEmpty()) return;
        Path2D.Float path = new Path2D.Float();
        float px = camera.cellPxSize();
        float off = (lane == 0 ? -0.25f : 0.25f) * px;
        for (int i = 0; i < route.size(); i++) {
            float x = camera.cellToScreenX(route.get(i)[0] + .5f) + off;
            float y = 1080 - camera.cellToScreenY(route.get(i)[1] + .5f) + off;
            if (i == 0) path.moveTo(x, y);
            else path.lineTo(x, y);
        }
        g.setStroke(new BasicStroke(Math.max(3f, px * 0.35f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(0, 0, 0, 140));
        g.translate(2, 2);
        g.draw(path);
        g.translate(-2, -2);
        g.setColor(color);
        g.draw(path);
    }

    private static void legend(Graphics2D g) {
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 22));
        g.setColor(new Color(0, 0, 0, 170));
        g.fillRoundRect(24, 24, 620, 84, 12, 12);
        g.setColor(TRUCK);
        g.fillRect(40, 44, 34, 8);
        g.setColor(FLIGHT);
        g.fillRect(40, 80, 34, 8);
        g.setColor(Color.WHITE);
        g.drawString("Supply truck: gate, depot apron, fuel point, yard, gate", 88, 56);
        g.drawString("Flight line: armoury and its fuel point to the apron", 88, 92);
    }

    private static BufferedImage copy(BufferedImage src) {
        BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return out;
    }
}
