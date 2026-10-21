package com.dillon.starsectormarines.battle.world.gen.ship;

import com.dillon.starsectormarines.battle.world.gen.ship.CompanyShip.BoatBayState;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import com.dillon.starsectormarines.ops.battleview.CompanyDeck;
import com.dillon.starsectormarines.ops.battleview.HeadlessArmoryPreviewRenderer;
import com.dillon.starsectormarines.ops.battleview.HeadlessBattleSceneRenderer;
import com.dillon.starsectormarines.ops.battleview.ShipDeckBattleScene;
import com.dillon.starsectormarines.testsupport.HeadlessSvgAssets;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;
import com.dillon.starsectormarines.ui.retained.headless.HeadlessUiRenderer;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONObject;

/**
 * The Second Wind's roomless facilities on her deck, for art review: the
 * provisions store, the mess and the hold at each facility tier, from nothing
 * bought to every tier, plus the whole ship at both ends. Every frame is
 * 1920x1080 at 100% scale, the same hull, seed, crew and deck clock, with every
 * core restored, so only the facility dressing changes between them.
 */
public final class SecondWindFacilitiesSnapshotSuite implements SnapshotSuite {
    private static final long SEED = 42L;
    private static final float CLOCK = 3.5f;

    @Override public String id() { return "second-wind-facilities"; }
    @Override public String label() { return "Second Wind facility dressing"; }

    @Override public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        ShipDeckSnapshotSuite.installTileRegistry();
        HeadlessArmoryPreviewRenderer.installCatalogs(context.modRoot());
        HeadlessSvgAssets.install(context.modRoot());
        var spec = new JSONObject(Files.readString(context.starsectorCore().resolve("data/hulls/warhound.ship")));
        var bounds = spec.getJSONArray("bounds");
        float[] polygon = new float[bounds.length()];
        for (int i = 0; i < polygon.length; i++) polygon[i] = (float) bounds.getDouble(i);
        var outline = HullOutline.fromBounds(polygon, "cerberus");
        CompanyShip hull = new CompanyShip(HullClass.FRIGATE, HullRole.FREIGHTER, 20, 68, 75,
                outline.aspect(), outline, spec.getString("spriteName"), true, BoatBayState.DAMAGED)
                .withWorkshop(WorkshopState.BURIED)
                .withRestoration(new ShipRestoration(true, false, false, true, true));
        var renderer = new HeadlessUiRenderer(HeadlessBattleSceneRenderer.resourceRoots(context.modRoot()),
                new HeadlessBattleSceneRenderer(context.modRoot(), true));

        Map<String, FacilityDressing> states = new LinkedHashMap<>();
        states.put("01-none", FacilityDressing.NONE);
        states.put("02-restored", FacilityDressing.uniform(1));
        states.put("03-improved-i", FacilityDressing.uniform(2));
        states.put("04-improved-ii", FacilityDressing.uniform(3));

        List<SnapshotArtifact> frames = new ArrayList<>();
        CompanyDeck deck = CompanyDeck.home(hull, SEED, null, List::of, List::of, () -> new boolean[]{true});
        try {
            while (!deck.ready()) Thread.onSpinWait();
            deck.advance(1f / 30f);
            deck.scene().advanceTo(CLOCK);
            ShipDeckBattleScene scene = deck.scene();
            scene.dress(CoreDressing.RESTORED);
            for (var entry : states.entrySet()) {
                scene.dress(entry.getValue());
                if (!scene.facilityDressing().equals(entry.getValue())) throw new AssertionError("Scene ignored its dressing");
                frames.addAll(views(renderer, deck, entry.getKey(), caption(entry.getValue()),
                        entry.getValue() == FacilityDressing.NONE || entry.getKey().startsWith("04")));
            }
        } finally {
            deck.dismiss();
        }
        return frames;
    }

    private static List<SnapshotArtifact> views(HeadlessUiRenderer renderer, CompanyDeck deck,
                                                String name, String caption, boolean wholeShip) {
        ShipDeckBattleScene scene = deck.scene();
        int width = scene.simulation().getGrid().getWidth(), height = scene.simulation().getGrid().getHeight();
        float fit = Math.min(1920f / width, 1080f / height) * .97f;
        List<SnapshotArtifact> out = new ArrayList<>();
        if (wholeShip) out.add(frame(renderer, scene, new ShipDeckBattleScene.DeckView(width / 2f, height / 2f, fit),
                name + "/00-whole-ship.png", caption + " - whole ship"));
        int index = 1;
        for (RoomPurpose purpose : List.of(RoomPurpose.LOADING_BAY, RoomPurpose.MESS_HALL, RoomPurpose.STOCKROOM)) {
            var room = deck.rooms().largest(purpose);
            if (room == null) continue;
            out.add(frame(renderer, scene, ShipDeckBattleScene.DeckView.over(room, 3, 52),
                    name + "/0" + index++ + "-" + label(purpose).replace(' ', '-') + ".png",
                    caption + " - " + label(purpose)));
        }
        return out;
    }

    private static String label(RoomPurpose purpose) {
        return switch (purpose) {
            case LOADING_BAY -> "provisions store";
            case MESS_HALL -> "mess";
            case STOCKROOM -> "hold";
            default -> purpose.name().toLowerCase().replace('_', ' ');
        };
    }

    private static SnapshotArtifact frame(HeadlessUiRenderer renderer, ShipDeckBattleScene scene,
                                          ShipDeckBattleScene.DeckView view, String path, String caption) {
        BufferedImage image = renderer.renderHostPass(scene.pass(view), 1920, 1080);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 22));
        int textWidth = g.getFontMetrics().stringWidth(caption);
        g.setColor(new Color(0, 0, 0, 170));
        g.fillRect(16, 16, textWidth + 24, 38);
        g.setColor(new Color(235, 228, 205));
        g.drawString(caption, 28, 43);
        g.dispose();
        return new SnapshotArtifact(path, image);
    }

    private static String caption(FacilityDressing state) {
        if (state.equals(FacilityDressing.NONE)) return "No facility tier bought (today's deck)";
        String[] tiers = {"", "Restored", "Improved I", "Improved II"};
        return "Hydroponics, wardroom, holds at " + tiers[state.hydroponics()]
                + (state.briefing() > 0 ? "; briefing room Restored" : "");
    }
}
