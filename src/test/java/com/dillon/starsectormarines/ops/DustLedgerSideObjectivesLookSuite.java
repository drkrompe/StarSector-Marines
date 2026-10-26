package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.air.ShuttleAssignment;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.command.objective.MissionObjectiveProjection;
import com.dillon.starsectormarines.battle.command.objective.MissionObjectiveSnapshot;
import com.dillon.starsectormarines.battle.objective.side.SideObjective;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.ops.battleview.BattleReviewFrameRenderer;
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
 * The Dust Ledger's side objectives in the quiet phase, through the look preset with the mission beacons and the HUD
 * rail: the open jobs offered with the feed still hidden; the feed found and being worked; the feed cut while the coupling is carried, then that moment at play
 * zoom on the market and over the whole map. The production chapter
 * fixture under autonomous command (whose company picks the crate up on its way off the apron), with a linesman stood
 * at the junction box as the controlled stand-in for a squad the player sends. 1920x1080 at 100%. Look evidence,
 * opt-in.
 */
public final class DustLedgerSideObjectivesLookSuite implements SnapshotSuite {
    /** The junction box's work cell. */
    static final int FEED_X = 122, FEED_Y = 32;
    /** The HUD's inset from the screen edge, as in play. */
    static final int HUD_INSET = 12;

    @Override public String id() { return "dust-ledger-side-objectives"; }

    @Override public String label() { return "The Dust Ledger's side objectives in the quiet phase: offered, worked, done"; }

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
                advance(sim, 10f);
                if (sim.getSideObjectives().job("dust-ledger-feed").known()) throw new IllegalStateException("the feed was found early");
                out.add(shot(context, sim, "01-offered-feed-hidden", 78f, 52f, 150f));

                sim.spawn(new EntitySpec("Linesman", Faction.MARINE, UnitType.MARINE, FEED_X, FEED_Y));
                advance(sim, 4f);
                require(sim, "dust-ledger-feed", SideObjective.State.WORKING);
                out.add(shot(context, sim, "02-feed-found-working", 112f, 42f, BattleReviewFrameRenderer.PLAY_ZOOM_CELLS));

                advance(sim, 7f);
                require(sim, "dust-ledger-feed", SideObjective.State.DONE);
                require(sim, "dust-ledger-coupling", SideObjective.State.WORKING);
                out.add(shot(context, sim, "03-feed-done-coupling-carried", 78f, 52f, 150f));
                out.add(shot(context, sim, "04-market-play-zoom", 78f, 52f, BattleReviewFrameRenderer.PLAY_ZOOM_CELLS));
                int w = sim.getGrid().getWidth(), h = sim.getGrid().getHeight();
                out.add(shot(context, sim, "05-whole-map", w / 2f, h / 2f, w));
            }
        }
        return out;
    }

    private static void advance(BattleSimulation sim, float seconds) {
        for (int i = 0, n = Math.round(seconds / BattleSimulation.TICK_DT); i < n && !sim.isComplete(); i++)
            sim.advance(BattleSimulation.TICK_DT);
        if (!sim.getDustLedger().progress().quiet()) throw new IllegalStateException("the quiet phase broke before the shot");
    }

    private static void require(BattleSimulation sim, String key, SideObjective.State state) {
        SideObjective.State actual = sim.getSideObjectives().job(key).state();
        if (actual != state) throw new IllegalStateException(key + " is " + actual + ", wanted " + state);
    }

    private static SnapshotArtifact shot(SnapshotContext context, BattleSimulation sim, String name,
                                         float x, float y, float cellsAcross) throws Exception {
        MissionObjectiveSnapshot picture = MissionObjectiveProjection.capture(sim);
        Reactor reactor = new Reactor();
        BattleHudOverlayModel model = new BattleHudOverlayModel(reactor, ignored -> { }, "Pause", "1x", "2x", "4x");
        model.updateProjected(1f, List.of());
        var presentation = model.updateMission(picture, ignored -> { });
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
            BufferedImage world = BattleReviewFrameRenderer.look(context.modRoot(), 1920, 1080, x, y, cellsAcross)
                    .withMissionMarkers().withHudCover(HUD_INSET + rail.x(), 1080 - HUD_INSET - rail.bottom())
                    .render(sim, "");
            var hud = new HeadlessUiRenderer(context.modRoot(), context.starsectorCore()).render(doc, 1896, height);
            var g = world.createGraphics();
            g.drawImage(hud, HUD_INSET, HUD_INSET, null);
            g.dispose();
            return new SnapshotArtifact(name + ".png", world);
        }
    }

    private static void installFonts(SnapshotContext context, BitmapFont... fonts) throws Exception {
        for (BitmapFont font : fonts) {
            Path path = context.modRoot().resolve(font.sourcePath());
            if (!Files.exists(path)) path = context.starsectorCore().resolve(font.sourcePath());
            font.installMetrics(Files.readString(path));
        }
    }
}
