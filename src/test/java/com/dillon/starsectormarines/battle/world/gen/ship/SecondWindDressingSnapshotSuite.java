package com.dillon.starsectormarines.battle.world.gen.ship;

import com.dillon.starsectormarines.battle.world.gen.ship.CompanyShip.BoatBayState;
import com.dillon.starsectormarines.battle.world.gen.ship.ShipCore;
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
 * The founding hull's per-core Deficit dressing, for art review: the deck at
 * every core's Deficit, each core restored on its own, and every core restored
 * (today's clean look), whole-ship and at four close framings, plus the dead-air
 * haze's control. Every frame is 1920x1080 at 100% scale, the same hull, seed,
 * crew and deck clock, so only the dressing changes between them.
 */
public final class SecondWindDressingSnapshotSuite implements SnapshotSuite {
    private static final long SEED = 42L;
    private static final float CLOCK = 3.5f;

    @Override public String id() { return "second-wind-dressing"; }
    @Override public String label() { return "Second Wind Deficit dressing"; }

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
                .withWorkshop(WorkshopState.BURIED);
        var renderer = new HeadlessUiRenderer(HeadlessBattleSceneRenderer.resourceRoots(context.modRoot()),
                new HeadlessBattleSceneRenderer(context.modRoot(), true));

        // The facility condition is the opening's throughout; only the cores move.
        // Power's Restored tier is the distribution order, which also clears the rooms' faults.
        Map<String, CoreDressing> states = new LinkedHashMap<>();
        states.put("01-all-deficit", CoreDressing.DEFICIT);
        states.put("02-power-restored", CoreDressing.DEFICIT.with(ShipCore.POWER, true));
        states.put("03-life-support-restored", CoreDressing.DEFICIT.with(ShipCore.LIFE_SUPPORT, true));
        states.put("04-structure-restored", CoreDressing.DEFICIT.with(ShipCore.STRUCTURE, true));
        states.put("05-compute-restored", CoreDressing.DEFICIT.with(ShipCore.COMPUTE, true));
        states.put("06-all-restored", CoreDressing.RESTORED);

        List<SnapshotArtifact> frames = new ArrayList<>();
        CompanyDeck dark = deck(hull, false), lit = deck(hull, true);
        try {
            for (var entry : states.entrySet()) {
                CompanyDeck deck = entry.getValue().power() ? lit : dark;
                ShipDeckBattleScene scene = deck.scene();
                scene.dress(entry.getValue());
                if (!scene.coreDressing().equals(entry.getValue())) throw new AssertionError("Scene ignored its dressing");
                frames.addAll(views(renderer, deck, entry.getKey(), caption(entry.getKey(), entry.getValue())));
            }
            ShipDeckBattleScene scene = dark.scene();
            scene.dress(CoreDressing.DEFICIT);
            String previous = System.getProperty("battle.render.deadAirHaze");
            System.setProperty("battle.render.deadAirHaze", "false");
            try {
                frames.addAll(views(renderer, dark, "07-all-deficit-haze-off",
                        "All cores at Deficit, dead-air haze off (-Dbattle.render.deadAirHaze=false)"));
            } finally {
                if (previous == null) System.clearProperty("battle.render.deadAirHaze");
                else System.setProperty("battle.render.deadAirHaze", previous);
            }
        } finally {
            dark.dismiss();
            lit.dismiss();
        }
        return frames;
    }

    private static CompanyDeck deck(CompanyShip hull, boolean power) {
        var ship = hull.withRestoration(new ShipRestoration(true, false, false, power, false));
        CompanyDeck deck = CompanyDeck.home(ship, SEED, null, List::of, List::of, () -> new boolean[]{true});
        while (!deck.ready()) Thread.onSpinWait();
        deck.advance(1f / 30f);
        deck.scene().advanceTo(CLOCK);
        return deck;
    }

    private static List<SnapshotArtifact> views(HeadlessUiRenderer renderer, CompanyDeck deck,
                                                String name, String caption) {
        ShipDeckBattleScene scene = deck.scene();
        int width = scene.simulation().getGrid().getWidth(), height = scene.simulation().getGrid().getHeight();
        float fit = Math.min(1920f / width, 1080f / height) * .97f;
        List<SnapshotArtifact> out = new ArrayList<>();
        out.add(frame(renderer, scene, new ShipDeckBattleScene.DeckView(width / 2f, height / 2f, fit),
                name + "/00-whole-ship.png", caption + " - whole ship"));
        int index = 1;
        for (RoomPurpose purpose : List.of(RoomPurpose.BRIDGE, RoomPurpose.BARRACKS, RoomPurpose.ENGINE_ROOM,
                RoomPurpose.ARMORY)) {
            var room = deck.rooms().largest(purpose);
            if (room == null) continue;
            out.add(frame(renderer, scene, ShipDeckBattleScene.DeckView.over(room, 6, 40),
                    name + "/0" + index++ + "-" + purpose.name().toLowerCase().replace('_', '-') + ".png",
                    caption + " - " + purpose.name().toLowerCase().replace('_', ' ') + " and passages"));
        }
        return out;
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

    private static String caption(String name, CoreDressing state) {
        if (state.clean()) return "All cores Restored (today's clean look)";
        if (state.equals(CoreDressing.DEFICIT)) return "All cores at Deficit (the battered start)";
        for (ShipCore core : CoreDressing.DRESSED)
            if (state.restored(core)) return core.name().replace('_', ' ') + " restored alone, the rest at Deficit";
        return name;
    }
}
