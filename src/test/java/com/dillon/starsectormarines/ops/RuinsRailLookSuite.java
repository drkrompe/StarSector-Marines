package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.command.objective.MissionObjectiveProjection;
import com.dillon.starsectormarines.battle.command.objective.MissionObjectiveSnapshot;
import com.dillon.starsectormarines.battle.command.objective.RuinsPullOut;
import com.dillon.starsectormarines.battle.command.objective.RuinsVaultObjective;
import com.dillon.starsectormarines.battle.ruins.RuinsWorks;
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
import org.json.JSONObject;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The Ruins rail ({@code ruins-expedition.md}, The rail shows the phase) through the look preset with the mission beacons and
 * the HUD: the vault's card and the Delve row in the approach, an entrance's apron being held, the entry taken (the
 * pull-out marker moved to it, the first stand's report and close reading), and a layer's stand. A scavenger site's
 * checked-in fixture, with marines stood on the apron and the junctions as the controlled stand-ins for squads the
 * player sends. 1920x1080 at 100%. Look evidence, opt-in; it judges nothing.
 */
public final class RuinsRailLookSuite implements SnapshotSuite {
    static final String FIXTURE = "src/test/resources/battle-fixtures/ruins-scavengers-widespread-v1.json";
    /** The HUD's inset from the screen edge, as in play. */
    static final int HUD_INSET = 12;

    @Override public String id() { return "ruins-rail"; }

    @Override public String label() { return "Ruins rail: the entrance step, the Delve row, the stands"; }

    @Override public boolean inAll() { return false; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        List<SnapshotArtifact> out = new ArrayList<>();
        try (HeadlessStrings strings = HeadlessBattles.install(context.projectRoot())) {
            installFonts(context, Fonts.ORBITRON_12_BOLD, Fonts.INSIGNIA_15_AA);
            JSONObject fixture = new JSONObject(Files.readString(context.projectRoot().resolve(FIXTURE)));
            try (BattleSimulation sim = HeadlessBattles.fixture(fixture)) {
                RuinsVaultObjective vault = RuinsPullOut.vault(sim);
                if (vault == null) throw new IllegalStateException("the fixture is no Ruins battle");
                int entrance = -1;
                for (int i = 0; i < vault.liftPoints().size() && entrance < 0; i++) {
                    if (vault.liftPoints().get(i).entrance()) entrance = i;
                }
                if (entrance < 0) throw new IllegalStateException("the fixture's ground has no entrance");
                int ex = vault.liftPointCellX(entrance);
                int ey = vault.liftPointCellY(entrance);
                int lx = vault.liftPointCellX(0);
                int ly = vault.liftPointCellY(0);

                advance(sim, 8f);
                out.add(shot(context, sim, "01-approach", (ex + lx) / 2f, (ey + ly) / 2f, 170f));

                for (int i = 0; i < 3; i++) {
                    sim.spawn(new EntitySpec("Pointman " + (i + 1), Faction.MARINE, UnitType.MARINE, ex + i - 1, ey));
                }
                advance(sim, 1.5f);
                out.add(shot(context, sim, "02-apron-holding", ex - 25, ey, 110f));

                for (int i = 0; i < 40 && vault.entry() < 0; i++) advance(sim, 0.25f);
                if (vault.entry() < 0) throw new IllegalStateException("the apron was not taken for the shot");
                out.add(shot(context, sim, "03-entry-held", ex - 25, ey, 110f));

                RuinsWorks works = sim.getRuinsWorks();
                RuinsWorks.Site junction = works.junctions().get(works.junctions().size() - 1);
                for (int i = 0; i < 2; i++) {
                    sim.spawn(new EntitySpec("Stand " + (i + 1), Faction.MARINE, UnitType.MARINE,
                            junction.x() + i, junction.y()));
                }
                advance(sim, 4f);
                out.add(shot(context, sim, "04-layer-stand", junction.x(), junction.y(), 130f));
            }
        }
        return out;
    }

    static void advance(BattleSimulation sim, float seconds) {
        for (int i = 0, n = Math.round(seconds / BattleSimulation.TICK_DT); i < n && !sim.isComplete(); i++)
            sim.advance(BattleSimulation.TICK_DT);
    }

    static SnapshotArtifact shot(SnapshotContext context, BattleSimulation sim, String name,
                                         float x, float y, float cellsAcross) throws Exception {
        MissionObjectiveSnapshot picture = MissionObjectiveProjection.capture(sim);
        Reactor reactor = new Reactor();
        BattleHudOverlayModel model = new BattleHudOverlayModel(reactor, ignored -> { }, "Pause", "1x", "2x", "4x");
        model.updateProjected(1f, List.of());
        var presentation = model.updateMission(picture, ignored -> { });
        var loader = new MarkupLoader(path -> Files.readString(context.modRoot().resolve(path)),
                List.of(BattleHudOverlay.COMPONENT_PATH));
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

    static void installFonts(SnapshotContext context, BitmapFont... fonts) throws Exception {
        for (BitmapFont font : fonts) {
            Path path = context.modRoot().resolve(font.sourcePath());
            if (!Files.exists(path)) path = context.starsectorCore().resolve(font.sourcePath());
            font.installMetrics(Files.readString(path));
        }
    }
}
