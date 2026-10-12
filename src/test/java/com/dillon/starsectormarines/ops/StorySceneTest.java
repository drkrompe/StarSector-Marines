package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.campaign.BrokenSupplyRouteProgress;
import com.dillon.starsectormarines.campaign.CorvusWaylineProgress;
import com.dillon.starsectormarines.campaign.SilentColonyMissionKey;
import com.dillon.starsectormarines.ops.mission.story.VeteransJobStory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import javax.imageio.ImageIO;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class StorySceneTest {
    static Mission campaign(DebugMissionCatalog.Scenario scenario) {
        String route = BrokenSupplyRouteProgress.PREFIX;
        String id = switch (scenario) {
            case RELIEF -> OpeningOperationKind.RELIEF.missionId;
            case COUNTERATTACK -> OpeningOperationKind.COUNTERATTACK.missionId;
            case JUNCTION -> route + "1_0_2";
            case LAST_LIFT -> route + "2_0_3";
            case RELAY_NINE -> route + "3_0_2";
            case THUNDER_RUN -> route + "4_0_2";
            case DUST_LEDGER -> CorvusWaylineProgress.PREFIX + "0_2";
            case BLIND_WATCH -> CorvusWaylineProgress.PREFIX + "1_3";
            case COLD_ACCOUNT -> CorvusWaylineProgress.PREFIX + "2_1";
            case MILITIA_RELIEF -> OpeningOperationKind.RELIEF.recurringId;
            case DEPOT_CLEARANCE -> OpeningOperationKind.COUNTERATTACK.recurringId;
            case CREW_RECOVERY -> route + "2_4_3";
            case ROUTE_SECURITY -> route + "3_7_2";
            case VETERANS_JOB -> VeteransJobStory.ID;
            case SILENT_COLONY -> SilentColonyMissionKey.encode(42);
        };
        MissionSource source = switch (scenario) {
            case MILITIA_RELIEF, DEPOT_CLEARANCE, CREW_RECOVERY, ROUTE_SECURITY -> MissionSource.RECURRING;
            case SILENT_COLONY -> MissionSource.CAMPAIGN_EVENT;
            default -> MissionSource.STORY;
        };
        return Mission.builder(scenario.mission()).id(id).source(source).build();
    }

    @ParameterizedTest @EnumSource(DebugMissionCatalog.Scenario.class)
    void authoredArtAgreesAcrossDebugBriefingAndBothResultStates(DebugMissionCatalog.Scenario scenario) {
        var mission = campaign(scenario);
        var scene = StoryScene.forMission(mission);
        assertNotNull(scene, scenario.name());
        var debug = scenario.mission();
        var props = DebugMissionScreen.model(List.of(debug), debug, 0, 0, "All locations",
                s -> {}, m -> {}, i -> {}, i -> {}, () -> {}, () -> {});
        assertEquals(scene.image, props.get("image"));
        var detail = new LinkedHashMap<String, Object>();
        MissionSelectScreen.putDetail(detail, mission, () -> {}, () -> {});
        assertEquals(scene.image, detail.get("detailImage"));
        for (boolean victory : new boolean[]{true, false}) {
            for (var launch : List.of(mission, debug)) {
                var result = new LinkedHashMap<String, Object>();
                ResultsScreen.putStoryDebrief(result, MissionOutcome.builder().mission(launch).victory(victory).build());
                assertEquals(scene.image, result.get("sceneImage"));
                assertTrue(result.get("sceneCaption").toString().startsWith("LOCATION ARCHIVE / "));
            }
        }
        assertNull(StoryScene.forMission(Mission.builder(mission).source(MissionSource.GENERATED).build()));
    }

    @Test void choicesPreviewTheirDestinationWithoutCommittingAndUnknownIdsKeepFallback() {
        var route = new BrokenSupplyRouteProgress("jangala", "Jangala", "Corvus");
        String id = route.missionId();
        route.begin(id); route.settle(id, true, null);
        var choices = SupplyRouteCampaign.offers(route);
        assertEquals(2, choices.size());
        assertEquals(List.of(StoryScene.RILL, StoryScene.RELAY), choices.stream().map(StoryScene::forMission).toList());
        for (var choice : choices)
            assertNull(StoryScene.forMission(Mission.builder(choice).source(MissionSource.GENERATED).build()));
        for (String bad : List.of("supply_route_v1_4_0_0", "supply_route_v1_5_0_1",
                "supply_route_v1_2_1_1", "story_veterans_job_fake", "debug:story:THUNDER_RUN"))
            assertNull(StoryScene.forRecord(bad, MissionSource.STORY), bad);
        assertNull(StoryScene.forRecord("supply_route_v1_4_2_1", MissionSource.RECURRING));
        assertNull(StoryScene.forRecord("supply_route_v1_2_0_1", MissionSource.RECURRING));
        assertNull(StoryScene.forMission(null));
    }

    @ParameterizedTest @EnumSource(StoryScene.class)
    void runtimeArtworkIsPackedAtRequestedSizeWithFullSizeMasterRetained(StoryScene scene) throws Exception {
        var runtime = ImageIO.read(Path.of("mod", scene.image).toFile());
        assertNotNull(runtime);
        assertEquals(1200, runtime.getWidth());
        assertEquals(400, runtime.getHeight());
        var name = Path.of(scene.image).getFileName().toString().replace(".jpg", ".master.png");
        var master = ImageIO.read(Path.of("art-source", "story-scenes", name).toFile());
        assertTrue(master.getWidth() > runtime.getWidth());
        assertEquals(master.getHeight() * 3, master.getWidth());
    }
}
