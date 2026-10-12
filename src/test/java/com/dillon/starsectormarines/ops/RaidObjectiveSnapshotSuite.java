package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.command.objective.RaidObjective;
import com.dillon.starsectormarines.ops.battleview.BattleReviewFrameRenderer;
import com.dillon.starsectormarines.ops.battleview.HeadlessArmoryPreviewRenderer;
import com.dillon.starsectormarines.testsupport.HeadlessSvgAssets;
import com.dillon.starsectormarines.testsupport.TileRegistryTestInstaller;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;
import com.dillon.starsectormarines.ui.retained.UiDocument;
import com.dillon.starsectormarines.ui.retained.headless.HeadlessUiRenderer;
import com.dillon.starsectormarines.ui.retained.markup.MarkupLoader;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

/** Authored launch seam plus controlled HUD phase fixtures, not a mission balance replay. */
public final class RaidObjectiveSnapshotSuite implements SnapshotSuite {
    @Override public String id() { return "raid-objectives"; }
    @Override public String label() { return "Asharu objectives and phase checklist"; }

    @Override public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        TileRegistryTestInstaller.install(context.projectRoot());
        HeadlessSvgAssets.install(context.modRoot());
        HeadlessArmoryPreviewRenderer.installCatalogs(context.modRoot());
        var chapter = CorvusChapter.DUST_LEDGER;
        var mission = chapter.mission("preview", chapter.planetName, "");
        var artifacts = new ArrayList<SnapshotArtifact>();
        var renderer = new HeadlessUiRenderer(context.modRoot(), context.starsectorCore());
        try (var sim = chapter.fixture(mission.battleSeed, List.of(), chapter.profile(chapter.surface)).build()) {
            RaidObjective raid = sim.getObjectives().stream().filter(RaidObjective.class::isInstance)
                    .map(RaidObjective.class::cast).findFirst().orElseThrow();
            assertEquals("Dispatch archive", raid.targetName());
            assertTrue(sim.getGrid().isWalkable(raid.targetCellX(), raid.targetCellY()));
            assertNotEquals(0, sim.getTopology().getBuildingId(raid.targetCellX(), raid.targetCellY()));
            var survey = BriefingSurvey.capture(sim);
            assertTrue(survey.markers().stream().anyMatch(m -> m.label().equals("1 / SEIZE")
                    && m.x() == raid.targetCellX() && m.y() == raid.targetCellY()));
            assertTrue(survey.markers().stream().anyMatch(m -> m.label().equals("2 / RETURN")
                    && m.x() == raid.egressCellX() && m.y() == raid.egressCellY()));
            var map = new BattleReviewFrameRenderer(context.modRoot(), 1920, 1080).render(sim,
                    "ASHARU / HUD PHASE FIXTURE / tick-zero observer map; GL bitmap world labels omitted");
            for (var phase : RaidObjective.Phase.values()) {
                Reactor reactor = new Reactor();
                BattleHudOverlayModel model = new BattleHudOverlayModel(reactor, ignored -> { }, "Pause", "1x", "2x", "4x");
                model.updateProjected(1f, List.of());
                var state = model.updateRaid(raid.targetName(), phase,
                        phase == RaidObjective.Phase.SERVICE ? 3 : phase == RaidObjective.Phase.APPROACH ? 0 : 6,
                        raid.serviceDuration(), () -> { });
                var loader = new MarkupLoader(path -> Files.readString(context.modRoot().resolve(path)),
                        List.of(BattleHudOverlay.COMPONENT_PATH));
                loader.reload();
                try (var instance = loader.build(reactor, BattleHudOverlay.COMPONENT, model.props())) {
                    BattleHudOverlay.wireLayout(instance);
                    UiDocument document = new UiDocument(instance.root());
                    instance.styles().forEach(document::addStyleSheet);
                    document.theme(MarineOpsThemes.standard());
                    BufferedImage hud = renderer.render(document, 1896,
                            Math.round(BattleHudOverlay.documentHeight(state)));
                    BufferedImage image = new BufferedImage(1920, 1080, BufferedImage.TYPE_INT_ARGB);
                    var graphics = image.createGraphics();
                    graphics.drawImage(map, 0, 0, null);
                    graphics.drawImage(hud, 12, 42, null);
                    graphics.dispose();
                    artifacts.add(new SnapshotArtifact(phase.name().toLowerCase(Locale.ROOT) + ".png", image));
                }
            }
        }
        return artifacts;
    }
}
