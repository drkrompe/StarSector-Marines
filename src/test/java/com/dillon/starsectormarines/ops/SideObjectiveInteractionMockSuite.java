package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.air.ShuttleAssignment;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.command.objective.MissionObjectiveProjection;
import com.dillon.starsectormarines.battle.setup.DustLedgerSequence.Area;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.ops.battleview.BattleReviewFrameRenderer;
import com.dillon.starsectormarines.render2d.BattleCamera;
import com.dillon.starsectormarines.testsupport.HeadlessBattles;
import com.dillon.starsectormarines.testsupport.HeadlessStrings;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;
import com.dillon.starsectormarines.ui.BitmapFont;
import com.dillon.starsectormarines.ui.Fonts;
import com.dillon.starsectormarines.ui.retained.UiDocument;
import com.dillon.starsectormarines.ui.retained.headless.HeadlessUiRenderer;
import com.dillon.starsectormarines.ui.retained.markup.MarkupLoader;
import com.dillon.starsectormarines.ui.retained.markup.MarkupPropertySource;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Mockups for how the player acts on the Dust Ledger's side objectives: a prompt on the thing itself (A) and buttons on
 * the checklist row (B), drawn over the real look-preset scene and the real rail. The prompts and row buttons are
 * mockups, not the game's: the cards are the game's own UI renderer on a test-only component
 * ({@code mockups/side-objective/world-prompt.mlx}), and the row buttons are spliced into a copy of the rail's markup.
 * Nothing here ships. 1920x1080 at 100%. Look evidence, opt-in.
 */
public final class SideObjectiveInteractionMockSuite implements SnapshotSuite {
    private static final int HUD_INSET = 12, SCREEN_W = 1920, SCREEN_H = 1080;
    private static final String PROMPT_PATH = "mockups/side-objective/world-prompt.mlx";
    /** The junction box and its work cell, and the leader's stand-in a few cells off it. */
    private static final int FEED_X = 122, FEED_Y = 32;

    @Override public String id() { return "side-objective-interaction-mock"; }

    @Override public String label() { return "Side-objective interaction mockups: world prompts and rail row buttons"; }

    @Override public boolean inAll() { return false; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        List<SnapshotArtifact> out = new ArrayList<>();
        try (HeadlessStrings strings = HeadlessBattles.install(context.projectRoot())) {
            installFonts(context, Fonts.ORBITRON_12_BOLD, Fonts.INSIGNIA_15_AA);
            var chapter = CorvusChapter.DUST_LEDGER;
            var mission = chapter.mission("look", chapter.planetName, "");
            var manifest = List.of(new ShuttleAssignment(ShuttleType.AEROSHUTTLE, 2, 6),
                    new ShuttleAssignment(ShuttleType.AEROSHUTTLE, 2, 6));

            try (BattleSimulation sim = chapter.fixture(mission.battleSeed, manifest, chapter.profile(chapter.surface)).build()) {
                advance(sim, 8f);
                sim.spawn(new EntitySpec("Linesman", Faction.MARINE, UnitType.MARINE, FEED_X - 6, FEED_Y + 1));
                sim.spawn(new EntitySpec("Linesman", Faction.MARINE, UnitType.MARINE, FEED_X - 7, FEED_Y + 2));
                advance(sim, 4f);
                float cx = FEED_X - 3f, cy = FEED_Y - 1f;
                Prompt card = new Prompt("p-card", "Junction box", "FEED TAP", "", 300,
                        List.of(new Verb("cut", "CUT THE FEED", "10 s unbroken / the gun nests go dark", true)), "Squad 2 in reach", 0);
                Prompt chip = new Prompt("p-chip", "", "", "", 168,
                        List.of(new Verb("cut", "CUT FEED  10 s", "", true)), "", 0);
                out.add(frame(context, sim, "q1-a-action-card", cx, cy, FEED_X + .5f, FEED_Y + .5f, card, null));
                out.add(frame(context, sim, "q1-b-compact-chip", cx, cy, FEED_X + .5f, FEED_Y + .5f, chip, null));
            }

            try (BattleSimulation sim = chapter.fixture(mission.battleSeed, manifest, chapter.profile(chapter.surface)).build()) {
                Area well = sim.getDustLedger().sites().wellHouse();
                var anchor = well.centre();
                advance(sim, 8f);
                // A company unit within sight of the junction box finds the feed job, so its row is listed too.
                sim.spawn(new EntitySpec("Linesman", Faction.MARINE, UnitType.MARINE, FEED_X - 6, FEED_Y + 1));
                sim.spawn(new EntitySpec("Leader", Faction.MARINE, UnitType.MARINE, anchor.x() - 6, anchor.y() - 2));
                sim.spawn(new EntitySpec("Rifleman", Faction.MARINE, UnitType.MARINE, anchor.x() - 7, anchor.y() - 3));
                advance(sim, 2f);
                float cx = anchor.x() - 4f, cy = anchor.y() - 2f;
                Prompt verbs = new Prompt("p-card", "Tobias Ankrum", "MARKET WARDEN", "", 330,
                        List.of(new Verb("respects", "PAY YOUR RESPECTS", "8 s / the watch holds the cantina and spots the yard", true),
                                new Verb("price", "OFFER FREIGHT CREDIT", "Illustrative second choice / a different payoff", false),
                                new Verb("leave", "NOT NOW", "", false)),
                        "Squad leader in reach", 0);
                Prompt speech = new Prompt("p-card", "Tobias Ankrum", "MARKET WARDEN",
                        "\"He speaks for Saltwell Cross, not for any faction. Show me you can wait.\"", 460,
                        List.of(new Verb("respects", "PAY RESPECTS", "", true), new Verb("price", "OFFER CREDIT", "", false),
                                new Verb("leave", "NOT NOW", "", false)),
                        "", 1);
                out.add(frame(context, sim, "q2-a-verb-list", cx, cy, anchor.x() + .5f, anchor.y() + .5f, verbs, null));
                out.add(frame(context, sim, "q2-b-speech-card", cx, cy, anchor.x() + .5f, anchor.y() + .5f, speech, null));
                out.add(frame(context, sim, "q3-a-row-strip", cx, cy, 0, 0, null, RowMode.STRIP));
                out.add(frame(context, sim, "q3-b-row-expand", cx, cy, 0, 0, null, RowMode.EXPAND));
            }
        }
        return out;
    }

    enum RowMode { STRIP, EXPAND }

    /** One verb on a prompt; {@code primary} is the gold one. */
    private record Verb(String id, String name, String detail, boolean primary) implements MarkupPropertySource {
        @Override public Object markupProperty(String key) {
            return switch (key) {
                case "id" -> "verb-" + id;
                case "name" -> name;
                case "detail" -> detail;
                case "classes" -> (detail.isEmpty() ? "p-verb-row" : "p-verb") + (primary ? (detail.isEmpty() ? " p-verb-row-primary" : " p-verb-primary") : "");
                case "nameId" -> "verb-" + id + "-name";
                case "detailId" -> "verb-" + id + "-detail";
                case "detailClasses" -> detail.isEmpty() ? "p-detail p-hidden" : "p-detail";
                default -> throw new IllegalArgumentException(key);
            };
        }
    }

    /** A world prompt: its card, text and verbs; {@code quoteLines} sizes the quote block, 0 for none. */
    private record Prompt(String rootClasses, String title, String tag, String quote, int width, List<Verb> verbs,
                          String foot, int quoteLines) {
        Prompt {
            if (!quote.isEmpty() && quoteLines == 0) throw new IllegalArgumentException("a quote needs its lines");
        }

        boolean chip() { return rootClasses.equals("p-chip"); }
        boolean rowVerbs() { return quoteLines > 0 || chip(); }

        int height() {
            if (chip()) return 28;
            int h = 12 + 16 + 4;
            if (quoteLines > 0) h += quoteLines * 15 + 4;
            boolean detail = verbs.stream().anyMatch(v -> !v.detail().isEmpty());
            if (rowVerbs()) h += 22 + 4;
            else h += verbs.size() * (detail ? 30 : 22) + (verbs.size() - 1) * 4 + 4;
            if (!foot.isEmpty()) h += 13 + 4;
            return h;
        }
    }

    private static BufferedImage promptImage(SnapshotContext context, Prompt prompt) throws Exception {
        Reactor reactor = new Reactor();
        Map<String, Object> props = Map.of(
                "rootClasses", reactor.signal(prompt.rootClasses()),
                "headClasses", reactor.signal(prompt.chip() ? "p-head p-hidden" : "p-head"),
                "title", reactor.signal(prompt.title()),
                "tag", reactor.signal(prompt.tag()),
                "quoteClasses", reactor.signal(prompt.quote().isEmpty() ? "p-quote p-hidden" : "p-quote"),
                "quote", reactor.signal(prompt.quote()),
                "verbsClasses", reactor.signal(prompt.rowVerbs() ? "p-verbs-row" : "p-verbs"),
                "verbs", reactor.signal(prompt.verbs()),
                "footClasses", reactor.signal(prompt.foot().isEmpty() ? "p-foot p-hidden" : "p-foot"),
                "foot", reactor.signal(prompt.foot()));
        var loader = new MarkupLoader(path -> Files.readString(context.projectRoot().resolve("src/test/resources").resolve(path)),
                List.of(PROMPT_PATH));
        loader.reload();
        try (var instance = loader.build(reactor, "world-prompt", props)) {
            var doc = new UiDocument(instance.root());
            instance.styles().forEach(doc::addStyleSheet);
            doc.theme(MarineOpsThemes.standard());
            doc.layout(prompt.width(), prompt.height());
            return new HeadlessUiRenderer(context.modRoot(), context.starsectorCore()).render(doc, prompt.width(), prompt.height());
        }
    }

    private static SnapshotArtifact frame(SnapshotContext context, BattleSimulation sim, String name, float cameraX, float cameraY,
                                       float anchorX, float anchorY, Prompt prompt, RowMode rows) throws Exception {
        var picture = MissionObjectiveProjection.capture(sim);
        Reactor reactor = new Reactor();
        BattleHudOverlayModel model = new BattleHudOverlayModel(reactor, ignored -> { }, "Pause", "1x", "2x", "4x");
        model.updateProjected(1f, List.of());
        var presentation = model.updateMission(picture, ignored -> { });
        var props = model.props();
        int extra = rows == null ? 0 : SideObjectiveRailMock.apply(props, presentation, rows);
        var loader = new MarkupLoader(path -> SideObjectiveRailMock.markup(context, path, rows), List.of(BattleHudOverlay.COMPONENT_PATH));
        loader.reload();
        try (var instance = loader.build(reactor, BattleHudOverlay.COMPONENT, props)) {
            BattleHudOverlay.wireLayout(instance);
            var doc = new UiDocument(instance.root());
            instance.styles().forEach(doc::addStyleSheet);
            doc.theme(MarineOpsThemes.standard());
            int height = Math.round(BattleHudOverlay.documentHeight(presentation)) + extra;
            doc.layout(1896, height);
            var rail = instance.requireElement("battle-command-rail").box().borderBox();
            var renderer = BattleReviewFrameRenderer.look(context.modRoot(), SCREEN_W, SCREEN_H, cameraX, cameraY,
                    BattleReviewFrameRenderer.PLAY_ZOOM_CELLS)
                    .withMissionMarkers().withHudCover(HUD_INSET + rail.x(), SCREEN_H - HUD_INSET - rail.bottom());
            BufferedImage world = renderer.render(sim, "");
            if (prompt != null) drawPrompt(world, renderer.cameraOf(sim), anchorX, anchorY, promptImage(context, prompt));
            var hud = new HeadlessUiRenderer(context.modRoot(), context.starsectorCore()).render(doc, 1896, height);
            var g = world.createGraphics();
            g.drawImage(hud, HUD_INSET, HUD_INSET, null);
            g.dispose();
            return new SnapshotArtifact(name + ".png", world);
        }
    }

    /** The card above the thing, a leader line down to it, clear of the rail. */
    private static void drawPrompt(BufferedImage world, BattleCamera camera, float cellX, float cellY, BufferedImage card) {
        int sx = Math.round(camera.cellToScreenX(cellX)), sy = Math.round(SCREEN_H - camera.cellToScreenY(cellY));
        int left = Math.max(16, Math.min(sx - card.getWidth() / 2, 1530 - card.getWidth()));
        int top = Math.max(16, sy - 90 - card.getHeight());
        var g = world.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(0xffd464));
        g.setStroke(new BasicStroke(2f));
        g.drawLine(Math.max(left + 14, Math.min(sx, left + card.getWidth() - 14)), top + card.getHeight(), sx, sy - 6);
        g.drawOval(sx - 6, sy - 6, 12, 12);
        g.drawImage(card, left, top, null);
        g.dispose();
    }

    private static void advance(BattleSimulation sim, float seconds) {
        for (int i = 0, n = Math.round(seconds / BattleSimulation.TICK_DT); i < n && !sim.isComplete(); i++)
            sim.advance(BattleSimulation.TICK_DT);
        if (!sim.getDustLedger().progress().quiet()) throw new IllegalStateException("the quiet phase broke before the shot");
    }

    private static void installFonts(SnapshotContext context, BitmapFont... fonts) throws Exception {
        for (BitmapFont font : fonts) {
            Path path = context.modRoot().resolve(font.sourcePath());
            if (!Files.exists(path)) path = context.starsectorCore().resolve(font.sourcePath());
            font.installMetrics(Files.readString(path));
        }
    }
}
