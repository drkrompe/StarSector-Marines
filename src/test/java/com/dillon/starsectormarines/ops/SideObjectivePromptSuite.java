package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.air.ShuttleAssignment;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.command.objective.MissionObjectiveProjection;
import com.dillon.starsectormarines.battle.command.objective.SideObjectiveRows;
import com.dillon.starsectormarines.battle.objective.side.SideField;
import com.dillon.starsectormarines.battle.objective.side.SideObjectiveService.Offer;
import com.dillon.starsectormarines.battle.setup.DustLedgerSequence.Area;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
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
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The side objectives' world prompts and the compact rail as they ship, over the real look-preset scene: the Dust Ledger's
 * feed junction (a card) and the warden's well house (a line per verb), with the company standing near each. The prompt
 * is the shipped component ({@code battle-side-prompt.mlx}) placed by {@link BattleSidePromptOverlay#place}, and the rail
 * is the shipped one, running compact and listing only the jobs in reach. 1920x1080 at 100%. Look evidence, opt-in.
 */
public final class SideObjectivePromptSuite implements SnapshotSuite {
    private static final int HUD_INSET = 12, SCREEN_W = 1920, SCREEN_H = 1080;
    /** The junction box, and the stand-ins the company is played by a few cells off it. */
    private static final int FEED_X = 122, FEED_Y = 32;

    @Override public String id() { return "side-objective-prompt"; }

    @Override public String label() { return "Side-objective world prompts and the compact rail"; }

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
                out.add(frame(context, sim, "feed-card", FEED_X - 3f, FEED_Y - 1f, "dust-ledger-feed"));
            }

            try (BattleSimulation sim = chapter.fixture(mission.battleSeed, manifest, chapter.profile(chapter.surface)).build()) {
                Area well = sim.getDustLedger().sites().wellHouse();
                var anchor = well.centre();
                advance(sim, 8f);
                // A company unit within sight of the junction box finds the feed job, so its row is listed too.
                sim.spawn(new EntitySpec("Linesman", Faction.MARINE, UnitType.MARINE, FEED_X - 6, FEED_Y + 1));
                // The warden's job wants a squad leader, so one of the company's own is stood a few cells off the well house.
                for (Squad squad : sim.getSquads()) {
                    if (squad.faction != Faction.MARINE) continue;
                    sim.world().setPos(squad.leaderId, anchor.x() - 5.5f, anchor.y() - 1.5f);
                    break;
                }
                advance(sim, 2f);
                out.add(frame(context, sim, "warden-lines", anchor.x() - 4f, anchor.y() - 2f, "dust-ledger-warden"));
            }
        }
        return out;
    }

    private static SnapshotArtifact frame(SnapshotContext context, BattleSimulation sim, String name, float cameraX, float cameraY,
                                          String job) throws Exception {
        var picture = MissionObjectiveProjection.capture(sim);
        Reactor reactor = new Reactor();
        BattleHudOverlayModel model = new BattleHudOverlayModel(reactor, ignored -> { }, "Pause", "1x", "2x", "4x");
        model.updateProjected(1f, List.of());
        var presentation = model.updateMission(picture, SideObjectiveRows.far(sim.getSideObjectives(), SideField.of(sim)), ignored -> { });
        var loader = new MarkupLoader(path -> Files.readString(context.modRoot().resolve(path)), List.of(BattleHudOverlay.COMPONENT_PATH));
        loader.reload();
        try (var instance = loader.build(reactor, BattleHudOverlay.COMPONENT, model.props())) {
            BattleHudOverlay.wireLayout(instance);
            var doc = new UiDocument(instance.root());
            instance.styles().forEach(doc::addStyleSheet);
            doc.theme(MarineOpsThemes.standard());
            int height = Math.round(BattleHudOverlay.documentHeight(presentation));
            doc.layout(1896, height);
            var rail = instance.requireElement("battle-command-rail").box().borderBox();
            float coverLeft = HUD_INSET + rail.x(), coverBottom = SCREEN_H - HUD_INSET - rail.bottom();
            var renderer = BattleReviewFrameRenderer.look(context.modRoot(), SCREEN_W, SCREEN_H, cameraX, cameraY,
                    BattleReviewFrameRenderer.PLAY_ZOOM_CELLS)
                    .withMissionMarkers().withHudCover(coverLeft, coverBottom);
            BufferedImage world = renderer.render(sim, "");
            Offer offer = sim.getSideObjectives().offers(SideField.of(sim)).stream()
                    .filter(o -> o.key().equals(job)).findFirst().orElseThrow();
            drawPrompt(context, world, renderer.cameraOf(sim), offer, coverLeft, coverBottom);
            var hud = new HeadlessUiRenderer(context.modRoot(), context.starsectorCore()).render(doc, 1896, height);
            var g = world.createGraphics();
            g.drawImage(hud, HUD_INSET, HUD_INSET, null);
            g.dispose();
            return new SnapshotArtifact(name + ".png", world);
        }
    }

    /** The shipped prompt, drawn where {@link BattleSidePromptOverlay#place} puts it for the camera. */
    private static void drawPrompt(SnapshotContext context, BufferedImage world, BattleCamera camera, Offer offer,
                                   float coverLeft, float coverBottom) throws Exception {
        int w = BattleSidePromptModel.width(offer), h = BattleSidePromptModel.height(offer);
        float[] at = BattleSidePromptOverlay.place(camera.cellToScreenX(offer.cellX() + .5f), camera.cellToScreenY(offer.cellY() + .5f),
                w, h, camera.vpX(), camera.vpY(), camera.vpW(), camera.vpH(), coverLeft, coverBottom, 36f);
        if (at == null) throw new IllegalStateException("the " + offer.key() + " prompt is out of view");
        Reactor reactor = new Reactor();
        BattleSidePromptModel model = new BattleSidePromptModel(reactor);
        model.show(offer, (key, verb) -> { });
        var loader = new MarkupLoader(path -> Files.readString(context.modRoot().resolve(path)),
                List.of(BattleSidePromptOverlay.COMPONENT_PATH));
        loader.reload();
        try (var instance = loader.build(reactor, BattleSidePromptOverlay.COMPONENT, model.props())) {
            var doc = new UiDocument(instance.root());
            instance.styles().forEach(doc::addStyleSheet);
            doc.theme(MarineOpsThemes.standard());
            doc.layout(w, h);
            BufferedImage card = new HeadlessUiRenderer(context.modRoot(), context.starsectorCore()).render(doc, w, h);
            var g = world.createGraphics();
            g.drawImage(card, Math.round(at[0]), Math.round(SCREEN_H - at[1] - h), null);
            g.dispose();
        }
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
