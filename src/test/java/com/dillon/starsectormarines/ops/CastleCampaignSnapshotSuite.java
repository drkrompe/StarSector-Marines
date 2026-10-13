package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.testsupport.HeadlessStrings;
import com.dillon.starsectormarines.ops.battleview.HeadlessBattleSceneRenderer;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;
import com.dillon.starsectormarines.battle.air.FittedBoat;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.power.MechSupport;
import com.dillon.starsectormarines.ops.spec.SpecSheets;
import com.dillon.starsectormarines.ui.retained.markup.MarkupLoader;
import com.dillon.starsectormarines.ui.retained.headless.HeadlessUiRenderer;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Shipped portrait/spotlight markup at 1920x1080 and 100% UI scale. */
public final class CastleCampaignSnapshotSuite implements SnapshotSuite {
    public String id() { return "castle-campaign"; }
    public String label() { return "Castle / Relief to Junction lessons"; }
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        try (var strings = new HeadlessStrings(context.modRoot())) {
            var frames = new ArrayList<>(UiSnapshotSuite.renderCampaignGantries(context));
            var roster = CampaignLessonsTest.afterCounterattack();
            roster.campaignLessons().reviewInvestments();
            var second = roster.createSquad(); roster.recruitToSquad(second.id(), 12);
            Set<String> both = roster.squads().stream().filter(s -> !s.reserve()).map(s -> s.id()).collect(Collectors.toSet());
            var mission = SecondSquadPreparationTest.junction();
            var props = BriefingViewModel.campaignPreviewProps(false);
            props.put("missionTitle", mission.name);
            props.put("missionKicker", "CAPTURE / HOLD / LOW RISK");
            props.put("missionFlavor", "Capture Weigh Station, Freight Yard and Junction Dispatch together. Our militia will hold your gains while the company advances.");
            props.put("rewardValue", "12,000 credits");
            props.put("personnelSummary", "Two formations / 24 marines aboard");
            props.put("experienceSummary", "Company infantry / recovered Hound");
            props.put("missionRows", List.of(BriefingViewModel.info("requirements", "Launch minimum", "Two squads / four ready marines each", "")));
            props.put("sourceRows", List.of());
            props.put("sourceEmpty", "Wayline / contracted heavy lift");
            props.put("captains", List.of(new BriefingViewModel.ChoiceRow("captain-mira", "choice-row selected",
                    "Mira Hale", "Lieutenant / 3 squad coordination", false, () -> {}, "graphics/portraits/mira-hale.png")));
            props.put("employerRows", List.of(BriefingViewModel.info("heavy-lift", "Mech Support", "Contracted heavy carrier", "")));
            BriefingViewModel.putFactionIdentity(props, "employer", "Wayline / Independents", null);
            BriefingViewModel.putFactionIdentity(props, "opposition", "Local raiders", null);
            BriefingViewModel.putLandingCraft(props, mission, List.of(FittedBoat.standard(ShuttleType.HERMES)), 24, "Second Wind");
            props.put("preparationClasses", "mission-preparation");
            BriefingViewModel.putDebugDrawer(props, false, false, () -> {});
            BriefingViewModel.putForceComparison(props, OperationTier.FIRST_CONTRACT, RiskLevel.LOW, 24, 24);
            var loader = new MarkupLoader(path -> Files.readString(context.modRoot().resolve(path)),
                    List.of("data/ui/components/missions/mission-briefing.mlx"));
            var renderer = new HeadlessUiRenderer(HeadlessBattleSceneRenderer.resourceRoots(context.modRoot()),
                    new HeadlessBattleSceneRenderer(context.modRoot(), true));
            for (int step = 0; step < 3; step++) {
                int stage = step;
                var power = new MechSupport();
                props.put("commandSummary", stage == 2 ? "3 / 5 slots" : "0 / 5 slots");
                props.put("powerRows", List.of(new BriefingViewModel.ChoiceRow("power-mech", "choice-row" + (stage == 2 ? " selected" : ""),
                        power.displayName, "3 slots", false, () -> {}, SpecSheets.commandPowerIcon(power), SpecSheets.commandPower(power))));
                try (var markup = loader.reloadAndBuild(new Reactor(), "mission-briefing", props)) {
                    var doc = PreparationGuide.document(markup.root()).theme(MarineOpsThemes.standard());
                    markup.styles().forEach(doc::addStyleSheet);
                    BriefingViewModel.applyVisibility(markup, props);
                    var guide = new PreparationGuide(doc, () -> CampaignLessons.briefing(mission, roster,
                            stage == 0 ? Set.of(second.id()) : both, true, stage == 2), () -> {},
                            new MarkupLoader(path -> Files.readString(context.modRoot().resolve(path)),
                                    List.of("data/ui/components/company/preparation-guide.mlx")));
                    doc.layout(1920, 1080); guide.update();
                    frames.add(new SnapshotArtifact("junction-" + step + ".png", renderer.render(doc, 1920, 1080)));
                }
            }
            return frames;
        }
    }
}
