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
 * Mockups for how the player acts on the Dust Ledger's side objectives, drawn over the real look-preset scene and the
 * real rail. The prompts are battle glass with small buttons, shown only for a thing a company unit is in reach of and
 * the camera has in view; the rail frames show three ways to give the side jobs less of the rail. The cards are the
 * game's own UI renderer on a test-only component ({@code mockups/side-objective/world-prompt.mlx}) and the rail is a
 * patched copy of its markup ({@link SideObjectiveRailMock}); nothing here ships. 1920x1080 at 100%. Look evidence,
 * opt-in.
 */
public final class SideObjectiveInteractionMockSuite implements SnapshotSuite {
    private static final int HUD_INSET = 12, SCREEN_W = 1920, SCREEN_H = 1080;
    private static final String PROMPT_PATH = "mockups/side-objective/world-prompt.mlx";
    /** The junction box and its work cell, and the leader's stand-in a few cells off it. */
    private static final int FEED_X = 122, FEED_Y = 32;
    private static final String GLASS = "p-glass hud-glass", PILL = "p-pill hud-glass";

    @Override public String id() { return "side-objective-interaction-mock"; }

    @Override public String label() { return "Side-objective interaction mockups: glass world prompts and rail space"; }

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
                Prompt card = new Prompt(GLASS, "Junction box", "FEED TAP", "10 s unbroken / the gun nests go dark", 236,
                        List.of(new Verb("cut", "CUT 10 s", true), new Verb("send", "SEND", false)), List.of(), 1);
                Prompt pill = new Prompt(PILL, "", "", "", 104, List.of(new Verb("cut", "CUT 10 s", true)), List.of(), 0);
                out.add(frame(context, sim, "q4-a-glass-card", cx, cy, FEED_X + .5f, FEED_Y + .5f, card, null));
                out.add(frame(context, sim, "q4-b-glass-pill", cx, cy, FEED_X + .5f, FEED_Y + .5f, pill, null));
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
                float ax = anchor.x() + .5f, ay = anchor.y() + .5f;
                Prompt buttons = new Prompt(GLASS, "Tobias Ankrum", "WARDEN", "8 s at the well house / the watch holds the cantina", 350,
                        List.of(new Verb("respects", "RESPECTS", true), new Verb("price", "OFFER", false),
                                new Verb("leave", "LEAVE", false)), List.of(), 1);
                Prompt lines = new Prompt(GLASS, "Tobias Ankrum", "WARDEN", "", 330, List.of(),
                        List.of(new Line("respects", "RESPECTS", "the watch holds the cantina", true),
                                new Line("price", "OFFER", "illustrative second price", false),
                                new Line("leave", "LEAVE", "", false)), 0);
                out.add(frame(context, sim, "q5-a-glass-buttons", cx, cy, ax, ay, buttons, null));
                out.add(frame(context, sim, "q5-b-glass-lines", cx, cy, ax, ay, lines, null));
                for (SideObjectiveRailMock.Mode mode : SideObjectiveRailMock.Mode.values())
                    out.add(frame(context, sim, "q6-" + mode.name().toLowerCase().replace('_', '-'), cx, cy, ax, ay, buttons, mode));
            }
        }
        return out;
    }

    /** One small button on a prompt; {@code primary} is the gold one. */
    private record Verb(String id, String name, boolean primary) implements MarkupPropertySource {
        @Override public Object markupProperty(String key) {
            return switch (key) {
                case "id" -> "verb-" + id;
                case "name" -> name;
                case "detail" -> "";
                case "classes" -> "p-cta" + (primary ? " selected" : "");
                case "nameId" -> "verb-" + id + "-name";
                case "detailId" -> "verb-" + id + "-detail";
                case "detailClasses" -> "p-detail p-hidden";
                default -> throw new IllegalArgumentException(key);
            };
        }
    }

    /** One choice as a small button with what it does beside it. */
    private record Line(String id, String name, String detail, boolean primary) implements MarkupPropertySource {
        @Override public Object markupProperty(String key) {
            return switch (key) {
                case "id" -> "line-" + id;
                case "name" -> name;
                case "detail" -> detail;
                case "classes" -> "p-cta" + (primary ? " selected" : "");
                case "buttonId" -> "line-" + id + "-button";
                case "nameId" -> "line-" + id + "-name";
                case "textId" -> "line-" + id + "-text";
                default -> throw new IllegalArgumentException(key);
            };
        }
    }

    /** A world prompt in battle glass: a title, one line of what it does, then small buttons in a row or as lines. */
    private record Prompt(String rootClasses, String title, String tag, String quote, int width, List<Verb> verbs,
                          List<Line> lines, int quoteLines) {
        boolean pill() { return rootClasses.equals(PILL); }

        int height() {
            if (pill()) return 20;
            int h = 4 + 16 + 3;
            if (quoteLines > 0) h += quoteLines * 15 + 3;
            if (!verbs.isEmpty()) h += 20 + 3;
            if (!lines.isEmpty()) h += lines.size() * 20 + (lines.size() - 1) * 4 + 3;
            return h;
        }
    }

    private static BufferedImage promptImage(SnapshotContext context, Prompt prompt) throws Exception {
        Reactor reactor = new Reactor();
        Map<String, Object> props = Map.ofEntries(
                Map.entry("rootClasses", reactor.signal(prompt.rootClasses())),
                Map.entry("headClasses", reactor.signal(prompt.title().isEmpty() ? "p-head p-hidden" : "p-head")),
                Map.entry("title", reactor.signal(prompt.title())),
                Map.entry("tag", reactor.signal(prompt.tag())),
                Map.entry("quoteClasses", reactor.signal(prompt.quote().isEmpty() ? "p-quote p-hidden" : "p-quote")),
                Map.entry("quote", reactor.signal(prompt.quote())),
                Map.entry("verbsClasses", reactor.signal(prompt.verbs().isEmpty() ? "p-hidden" : "p-verbs-row")),
                Map.entry("verbs", reactor.signal(prompt.verbs())),
                Map.entry("linesClasses", reactor.signal(prompt.lines().isEmpty() ? "p-hidden" : "p-verbs")),
                Map.entry("lines", reactor.signal(prompt.lines())),
                Map.entry("footClasses", reactor.signal("p-foot p-hidden")),
                Map.entry("foot", reactor.signal("")));
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
                                          float anchorX, float anchorY, Prompt prompt, SideObjectiveRailMock.Mode rows) throws Exception {
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

    /** The prompt above the thing with a leader line down to it, clear of the rail. */
    private static void drawPrompt(BufferedImage world, BattleCamera camera, float cellX, float cellY, BufferedImage card) {
        int sx = Math.round(camera.cellToScreenX(cellX)), sy = Math.round(SCREEN_H - camera.cellToScreenY(cellY));
        int left = Math.max(16, Math.min(sx - card.getWidth() / 2, 1530 - card.getWidth()));
        int top = Math.max(16, sy - 70 - card.getHeight());
        var g = world.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(0xffd464));
        g.setStroke(new BasicStroke(1.5f));
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
