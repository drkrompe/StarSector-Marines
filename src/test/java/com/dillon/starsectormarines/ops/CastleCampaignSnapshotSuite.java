package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.testsupport.HeadlessStrings;
import com.dillon.starsectormarines.ops.battleview.HeadlessBattleSceneRenderer;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;
import com.dillon.starsectormarines.battle.air.FittedBoat;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.marine.MarineSoldierStatus;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.ui.retained.headless.HeadlessUiRenderer;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

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
            var mission = SecondSquadPreparationTest.junction();
            var props = BriefingViewModel.campaignPreviewProps(false);
            props.put("missionTitle", mission.name);
            props.put("missionKicker", "CAPTURE / HOLD / LOW RISK");
            props.put("missionFlavor", "Capture Weigh Station, Freight Yard and Junction Dispatch together. Our militia will hold your gains while the company advances.");
            props.put("rewardValue", "12,000 credits");
            props.put("personnelSummary", "Two formations / 24 marines aboard");
            props.put("experienceSummary", "Company infantry / recovered Hound");
            props.put("missionRows", List.of(BriefingViewModel.info("requirements", "Recommended", "Two full squads; any deployable squad may launch", "")));
            props.put("sourceRows", List.of());
            props.put("sourceEmpty", "Wayline / contracted heavy lift");
            props.put("captains", List.of(new BriefingViewModel.ChoiceRow("captain-sabine", "choice-row selected",
                    "Morgan Hale", "Lieutenant / 3 squad coordination", false, () -> {}, "graphics/portraits/sabine-hale.png")));
            props.put("employerRows", List.of(BriefingViewModel.info("heavy-lift", "Mech Support", "Contracted heavy carrier", "")));
            BriefingViewModel.putFactionIdentity(props, "employer", "Wayline / Independents", null);
            BriefingViewModel.putFactionIdentity(props, "opposition", "Local raiders", null);
            BriefingViewModel.putLandingCraft(props, mission, List.of(FittedBoat.standard(ShuttleType.HERMES)), 24, "Second Wind");
            props.put("preparationClasses", "mission-preparation");
            BriefingViewModel.putDebugDrawer(props, false, false, () -> {});
            BriefingViewModel.putForceComparison(props, OperationTier.FIRST_CONTRACT, RiskLevel.LOW, 24, 24);
            var renderer = new HeadlessUiRenderer(HeadlessBattleSceneRenderer.resourceRoots(context.modRoot()),
                    new HeadlessBattleSceneRenderer(context.modRoot(), true));
            props.put("commandSummary", "0 / 5 slots");
            props.put("powerRows", List.of());
            // Junction has no callout: the lamps beside Deploy carry the checks. One squad, two wounded and one
            // empty billet lights strength and casualties (with and without the casualty hover); a whole force lights nothing.
            var members = roster.squadMembers(second);
            roster.applySoldierOutcome(Map.of(members.get(0).id(), MarineSoldierStatus.WIA,
                    members.get(1).id(), MarineSoldierStatus.WIA, members.get(2).id(), MarineSoldierStatus.KIA),
                    0f, 0.6f);
            putLamps(props, roster, Set.of(second.id()), 9, 3);
            frames.add(new SnapshotArtifact("junction-0.png", UiSnapshotSuite.renderMissionBriefing(context, renderer,
                    1920, 1080, props, 1f, false, "mission-lamp-casualties")));
            frames.add(new SnapshotArtifact("junction-1.png", UiSnapshotSuite.renderMissionBriefing(context, renderer,
                    1920, 1080, props, 1f, false, null)));
            putLamps(props, roster, Set.of(), 24, 0);
            frames.add(new SnapshotArtifact("junction-2.png", UiSnapshotSuite.renderMissionBriefing(context, renderer,
                    1920, 1080, props, 1f, false, null)));
            return frames;
        }
    }

    private static void putLamps(Map<String, Object> props, MarineRoster roster, Set<String> selected, int ready,
                                 int replenishable) {
        props.put("deployLamps", List.of(DeployWarnings.undermanned(ready, 24),
                DeployWarnings.casualties(roster, selected, 0f, replenishable),
                DeployWarnings.damage(List.of(), 0f), DeployWarnings.lift(null)));
        props.put("replenishLabel", replenishable > 0 && !selected.isEmpty() ? "REPLENISH +1" : "");
        props.put("replenishClasses", replenishable > 0 && !selected.isEmpty() ? "" : "briefing-assign-hidden");
        props.put("replenishDisabled", false);
    }
}
