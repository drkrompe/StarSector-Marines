package com.dillon.starsectormarines.battle.audio;

import com.dillon.starsectormarines.ops.DebugMissionCatalog;
import com.dillon.starsectormarines.ops.Mission;
import com.dillon.starsectormarines.ops.MissionSource;
import com.dillon.starsectormarines.ops.OpeningOperationKind;
import com.dillon.starsectormarines.battle.story.StoryBeatLog;
import com.dillon.starsectormarines.battle.story.ColdAccountStoryHooks;
import com.dillon.starsectormarines.battle.story.OpeningOperationStoryHooks;
import com.dillon.starsectormarines.battle.story.JunctionStoryHooks;
import com.dillon.starsectormarines.battle.story.RelayNineStoryHooks;
import com.dillon.starsectormarines.battle.command.compound.CompoundService.CompoundState;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.*;

class WaylineNarrationTest {
    @Test void briefingTurnsRetainAuthoredOrderAndExcludeOtherEventsAndMissions() {
        var first = new WaylineNarration.Line("first", "chapter", "briefing", "First", 2, 1, 10, List.of());
        var second = new WaylineNarration.Line("second", "chapter", "briefing.castle", "Second", 2, 1, 10,
                List.of(), "CASTLE / COMPANY OPERATIONS", "graphics/portraits/castle.png");
        var tactical = new WaylineNarration.Line("battle", "chapter", "contact", "Battle", 2, 1, 10, List.of());
        var other = new WaylineNarration.Line("other", "other", "briefing", "Other", 2, 1, 10, List.of());
        var catalog = new WaylineNarration(List.of(first, tactical, other, second));
        assertEquals(List.of(first, second), catalog.briefing("chapter"));
        assertEquals(List.of(other), catalog.briefing("other"));
        assertTrue(catalog.briefing(null).isEmpty());
        assertEquals(first, catalog.find("chapter", "briefing"));
    }
    @Test void shepherdLinesRetainCastingAndConfirmedPassageReports() throws Exception {
        JSONObject root = new JSONObject(Files.readString(Path.of("mod", WaylineNarration.PATH)));
        var entries = root.getJSONArray("lines");
        for (int i = 0; i < entries.length(); i++) {
            var entry = entries.getJSONObject(i);
            if (!entry.getString("missionId").startsWith("story_shepherd_")) continue;
            boolean recorded = List.of("basin_briefing", "basin_cold_start_turret_ready", "basin_passage_underway",
                    "basin_cold_start_power_restored", "basin_cold_start_complete", "basin_passage_briefing",
                    "basin_passage_bend_build", "basin_passage_militia_hold_0", "basin_passage_narrows_build",
                    "basin_passage_militia_hold_1", "basin_passage_departure", "basin_passage_complete")
                    .contains(entry.getString("id"));
            assertEquals(recorded, entry.getBoolean("voiced"), "Only recorded takes may be voiced");
            assertTrue(entry.getString("synthesisText").contains(entry.getString("text")));
            assertEquals(entry.getString("speaker").startsWith("CASTLE") ? "cuwCdnt9CKAB9XrTA1j0" : "cZH6erlGkOyAQheipyno",
                    entry.getString("voiceId"), "Retain each chosen character voice, including unrecorded drafts");
        }
        var catalog = catalog();
        assertTrue(catalog.find("story_shepherd_cold_start", "briefing").text().contains("already ashore"));
        assertTrue(catalog.find("story_shepherd_cold_start", "cold_start.turret_ready").speaker().startsWith("CASTLE"));
        for (String event : List.of("underway", "militia_hold_0", "militia_hold_1"))
            assertNotNull(catalog.find("story_shepherd_working_passage", "working_passage." + event));
    }

    @Test void shepherdLaterFactsRetireInstructionsAndTerminalReportsSurviveCompletion() throws Exception {
        var catalog = catalog();
        String chapter = "story_shepherd_cold_start";
        var log = new StoryBeatLog();
        var queue = new StoryNarrationQueue(catalog, chapter);
        log.emitOnce(1, "cold_start.power_restored", StoryBeatLog.Audience.PLAYER);
        queue.advance(0, log.events(), false);
        log.emitOnce(2, "cold_start.turret_ready", StoryBeatLog.Audience.PLAYER);
        queue.advance(0, log.events(), false);
        assertEquals("cold_start.turret_ready", queue.poll().event());
        assertNull(queue.poll(), "Do not ask for a road gun after it was built");

        for (String terminal : List.of("complete", "company_lost", "builders_lost", "crew_lost", "carrier_lost")) {
            log = new StoryBeatLog();
            queue = new StoryNarrationQueue(catalog, chapter);
            log.emitOnce(1, "cold_start.approach", StoryBeatLog.Audience.PLAYER);
            queue.advance(0, log.events(), false);
            log.emitOnce(2, "cold_start." + terminal, StoryBeatLog.Audience.PLAYER);
            queue.advance(0, log.events(), true);
            assertEquals("cold_start." + terminal, queue.poll().event());
            assertNull(queue.poll(), "No tactical chatter after a terminal fact");
        }
        chapter = "story_shepherd_working_passage";
        log = new StoryBeatLog();
        queue = new StoryNarrationQueue(catalog, chapter);
        log.emitOnce(1, "working_passage.bend_build", StoryBeatLog.Audience.PLAYER);
        queue.advance(0, log.events(), false);
        log.emitOnce(2, "working_passage.narrows", StoryBeatLog.Audience.PLAYER);
        queue.advance(0, log.events(), false);
        assertEquals("working_passage.narrows", queue.poll().event());
        assertNull(queue.poll(), "No pump-bend construction instruction after the gun exists");
    }

    @Test void coldStartRetriesShareTheirOwnConstructionGuidance() throws Exception {
        String chapter = "story_shepherd_cold_start";
        assertEquals(chapter, WaylineNarration.storyMissionId(Mission.builder()
                .id("basin_cold_start_v1_2").source(MissionSource.STORY).build()));
        assertEquals(chapter, WaylineNarration.storyMissionId(
                DebugMissionCatalog.Scenario.COLD_START.mission()));
        var catalog = catalog();
        assertTrue(catalog.find(chapter, "briefing").voiced());
        for (String event : List.of("convoy_arrived", "workshop", "pump_started", "power_restored",
                "turret_ready", "complete", "crew_lost", "carrier_lost", "approach")) {
            var line = catalog.find(chapter, "cold_start." + event);
            assertNotNull(line);
            assertEquals(List.of("power_restored", "turret_ready", "complete").contains(event), line.voiced());
        }
        assertTrue(catalog.find(chapter, "cold_start.power_restored").text().contains("Build Vulcan Turret"));
    }

    @Test
    void everyVoicedLineHasARecordedTakeAndSoundRegistration() throws Exception {
        JSONObject root = new JSONObject(Files.readString(Path.of("mod", WaylineNarration.PATH)));
        WaylineNarration catalog = WaylineNarration.parse(root);
        JSONObject sounds = new JSONObject(Files.readString(Path.of("mod/data/config/sounds.json")).replaceAll("(?m)#.*$", ""));
        Path sources = Path.of("asset-pipeline/src/tool/resources/audio");
        var clips = new JSONObject(Files.readString(sources.resolve("audio.json")).replaceAll("(?m)^\\s*//.*$", "")).getJSONArray("clips");
        var ids = new HashSet<String>();
        for (var line : catalog.lines()) {
            assertTrue(ids.add(line.id()));
            if (!line.voiced()) {
                assertFalse(sounds.has(line.id()), "Text-only lines must not masquerade as recorded audio");
                continue;
            }
            assertEquals("sounds/story/wayline/" + line.id() + ".ogg",
                    sounds.getJSONArray(line.id()).getJSONObject(0).getString("file"));
            JSONObject receipt = new JSONObject(Files.readString(sources.resolve("wayline/" + line.id() + ".json")));
            JSONObject authored = root.getJSONArray("lines").getJSONObject(catalog.lines().indexOf(line));
            assertEquals(authored.optString("voiceId", root.getString("voiceId")), receipt.getString("voiceId"));
            assertEquals(root.getString("modelId"), receipt.getString("modelId"));
            assertEquals(receipt.getDouble("durationSeconds"), line.durationSeconds(), 0.001);
            assertTrue(Files.size(sources.resolve("wayline/" + line.id() + ".mp3")) > 1000);
            assertEquals(receipt.getString("audioSha256"), HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(
                            Files.readAllBytes(sources.resolve("wayline/" + line.id() + ".mp3")))));
            int matching = 0;
            for (int i = 0; i < clips.length(); i++) {
                JSONObject clip = clips.getJSONObject(i);
                if (!line.id().equals(clip.getString("id"))) continue;
                matching++;
                assertEquals("ui", clip.getString("kind"));
                assertEquals("wayline/" + line.id() + ".mp3", clip.getString("source"));
                assertEquals("story/wayline", clip.getString("out"));
            }
            assertEquals(1, matching);
            for (String prior : line.supersedes()) assertNotNull(catalog.find(line.missionId(), prior));
        }
        assertEquals(132, catalog.lines().stream().filter(WaylineNarration.Line::voiced).count());
        assertEquals(catalog.lines().size(), ids.size());
    }

    @Test void convoyBriefingFollowsJunctionWithoutPlayingTheRetiredRelayIntroduction() throws Exception {
        var line = catalog().find("story_supply_route_thunder_run", "briefing");
        assertTrue(line.text().startsWith("The junction is open."));
        assertTrue(line.voiced(), "The replacement recording follows Junction directly");
    }

    @Test void thunderDefenseGuidanceIsVoicedAndExplainsHandback() throws Exception {
        var catalog = catalog();
        String chapter = "story_supply_route_thunder_run";
        for (String key : List.of("advance_distress", "defend_dispatch", "defend_crossing",
                "defend_order_placed", "dispatch_window_complete", "crossing_window_complete",
                "militia_gun_0_ready", "militia_gun_1_ready", "advance_load_lost", "advance_load_clear")) {
            var line = catalog.find(chapter, "supply_route.thunder_run." + key);
            assertNotNull(line);
            assertTrue(line.voiced());
            assertTrue(line.speaker().startsWith("CASTLE"));
            for (String prior : line.supersedes()) assertNotNull(catalog.find(chapter, prior));
        }
        assertTrue(catalog.find(chapter, "supply_route.thunder_run.defend_dispatch").text().contains("DEFEND AREA"));
        assertTrue(catalog.find(chapter, "supply_route.thunder_run.dispatch_window_complete").text().contains("does not expire"));
    }

    @Test void junctionAndThunderRunHaveNoSilentDialoguePlaceholders() throws Exception {
        for (var line : catalog().lines()) {
            if (!Set.of("story_supply_route_junction", "story_supply_route_thunder_run").contains(line.missionId())) continue;
            assertTrue(line.voiced(), line.id());
            assertEquals(line.event().endsWith("gunship_missile_warning")
                    || line.event().endsWith("gunship_hit") || line.event().endsWith("gunship_lost"),
                    line.interrupts(), "Only immediate aircraft emergencies interrupt routine speech: " + line.id());
        }
    }

    @Test void coldAccountRetriesShareKordsPublicDialogue() throws Exception {
        for (int attempt : new int[]{1, 12}) {
            String id = "wayline_corvus_v1_2_" + attempt;
            assertEquals("story_corvus_cold_account", WaylineNarration.storyMissionId(
                    Mission.builder().id(id).source(MissionSource.STORY).build()));
            assertNull(WaylineNarration.storyMissionId(Mission.builder().id(id).source(MissionSource.RECURRING).build()));
        }
        var line = catalog().find("story_corvus_cold_account", "corvus.cold_account.stronghold_destroyed");
        assertTrue(line.terminal());
        assertTrue(line.voiced());
        assertTrue(line.speaker().contains("KORD"));
    }

    @Test void patrolExchangeKeepsTurnOrderAndCaptureRetiresUnspokenApproach() throws Exception {
        var catalog = catalog();
        String chapter = "story_corvus_cold_account";
        var log = new StoryBeatLog();
        var hooks = new ColdAccountStoryHooks(log);
        hooks.observe(1, true, true, false);
        var queue = new StoryNarrationQueue(catalog, chapter);
        queue.advance(0, log.events(), false);
        for (var event : log.events()) {
            var line = queue.poll();
            assertNotNull(line);
            assertEquals(event.id(), line.event());
            assertTrue(line.voiced());
        }
        assertNull(queue.poll());

        queue = new StoryNarrationQueue(catalog, chapter);
        queue.advance(0, log.events(), false);
        assertEquals(ColdAccountStoryHooks.PREFIX + "patrol_hail", queue.poll().event());
        log.emitOnce(2, ColdAccountStoryHooks.PREFIX + "pirate_evidence_confirmed", StoryBeatLog.Audience.PLAYER);
        queue.advance(1, log.events(), false);
        assertEquals(ColdAccountStoryHooks.PREFIX + "pirate_evidence_confirmed", queue.poll().event());
        assertNull(queue.poll(), "No request for proof should play after proof was obtained");
        for (var line : catalog.lines()) if (line.speaker().contains("KORD")) {
            assertEquals("graphics/portraits/irena-kord.png", line.portrait());
            assertTrue(Files.isRegularFile(Path.of("mod", line.portrait())));
        }
    }

    @Test
    void chapterIdentityRequiresStorySource() {
        for (OpeningOperationKind kind : OpeningOperationKind.values()) {
            assertEquals(kind.missionId, WaylineNarration.storyMissionId(
                    Mission.builder().id(kind.missionId).source(MissionSource.STORY).build()));
            assertNull(WaylineNarration.storyMissionId(
                    Mission.builder().id(kind.missionId).source(MissionSource.RECURRING).build()));
        }
        assertNull(WaylineNarration.storyMissionId(null));
    }

    @Test
    void debugScenariosHearTheirChapterButRepeatablesStaySilent() throws Exception {
        WaylineNarration catalog = catalog();
        for (DebugMissionCatalog.Scenario scenario : DebugMissionCatalog.Scenario.values()) {
            String id = WaylineNarration.storyMissionId(scenario.mission());
            boolean silent = switch (scenario) {
                case MILITIA_RELIEF, DEPOT_CLEARANCE, CREW_RECOVERY, ROUTE_SECURITY, VETERANS_JOB,
                     SILENT_COLONY, DUST_LEDGER, BLIND_WATCH -> true;
                default -> false;
            };
            if (silent) {
                assertNull(id, scenario.name());
            } else {
                assertNotNull(id, scenario.name());
                assertTrue(catalog.lines().stream().anyMatch(l -> l.missionId().equals(id)), scenario.name());
            }
        }
    }

    @Test
    void activeSupplyChaptersResolveAcrossRetriesWithoutVoicingShelvedOrRecurringJobs() throws Exception {
        WaylineNarration catalog = catalog();
        String[] chapters = {"junction", "thunder_run"};
        int[] operations = {1, 4};
        for (int i = 0; i < chapters.length; i++) {
            String chapter = "story_supply_route_" + chapters[i];
            for (int attempt : new int[]{1, 2, 12}) {
                String id = "supply_route_v1_" + operations[i] + "_0_" + attempt;
                assertEquals(chapter, WaylineNarration.storyMissionId(
                        Mission.builder().id(id).source(MissionSource.STORY).build()));
                assertNotNull(catalog.find(chapter, "briefing"));
                assertNull(WaylineNarration.storyMissionId(
                        Mission.builder().id(id).source(MissionSource.RECURRING).build()));
            }
        }
        for (String id : List.of("supply_route_choice_2", "supply_route_choice_3",
                "supply_route_v1_2_0_1", "supply_route_v1_3_0_1",
                "supply_route_v1_2_1_1", "supply_route_v1_3_4_2", "supply_route_v1_5_0_1",
                "supply_route_v1_1_0_0", "supply_route_v1_1_0_1_extra", "unknown")) {
            assertNull(WaylineNarration.storyMissionId(
                    Mission.builder().id(id).source(MissionSource.STORY).build()), id);
        }
    }

    @Test
    void everyPublicOpeningAndTerritorialBeatHasDialogueOrAnOutcomeOwner() throws Exception {
        WaylineNarration catalog = catalog();
        for (OpeningOperationKind kind : OpeningOperationKind.values()) {
            StoryBeatLog log = new StoryBeatLog();
            OpeningOperationStoryHooks hooks = new OpeningOperationStoryHooks(kind, log);
            hooks.observe(new OpeningOperationStoryHooks.Facts(1, true, true, true, true, true,
                    6, 3, 2, true, true, OpeningOperationStoryHooks.Result.RUNNING));
            hooks.observe(new OpeningOperationStoryHooks.Facts(2, true, true, true, true, true,
                    6, 0, 2, true, true, OpeningOperationStoryHooks.Result.VICTORY));
            assertCoverage(catalog, kind.missionId, log);
        }
        StoryBeatLog junction = new StoryBeatLog(), relay = new StoryBeatLog();
        JunctionStoryHooks junctionHooks = new JunctionStoryHooks(junction);
        RelayNineStoryHooks relayHooks = new RelayNineStoryHooks(relay);
        for (int tick = 1; tick <= 3; tick++) {
            CompoundState state = tick == 2 ? CompoundState.DEFENDER_HELD : CompoundState.MARINE_HELD;
            List<CompoundState> states = List.of(state, state, state, state);
            junctionHooks.observe(new JunctionStoryHooks.Facts(tick, true, true, true, true, states,
                    JunctionStoryHooks.Result.RUNNING));
            relayHooks.observe(new RelayNineStoryHooks.Facts(tick, true, true, List.of(1, 2, 3), true, states,
                    RelayNineStoryHooks.Result.RUNNING));
        }
        junctionHooks.finish(4, JunctionStoryHooks.Result.VICTORY);
        relayHooks.finish(4, RelayNineStoryHooks.Result.VICTORY);
        assertCoverage(catalog, "story_supply_route_junction", junction);
        assertCoverage(catalog, "story_supply_route_relay_nine", relay);
    }

    @Test
    void evacuationAndFreightPublicContentContractsAreRecorded() throws Exception {
        WaylineNarration catalog = catalog();
        for (String key : List.of("start", "crew_contacted", "crew_released", "crossing_cleared",
                "first_boarding", "escort_hold"))
            assertNotNull(catalog.find("story_supply_route_last_lift", "supply_route.last_lift." + key), key);
        for (String key : List.of("started", "player_landed", "column_released", "crossing_secured",
                "final_run", "freight_committed", "heavy_lead_advancing", "rear_clear",
                "cargo_0_delivered", "cargo_1_delivered", "cargo_2_delivered",
                "cargo_0_lost", "cargo_1_lost", "cargo_2_lost"))
            assertNotNull(catalog.find("story_supply_route_thunder_run", "supply_route.thunder_run." + key), key);
        // Resolution belongs to outcome presentation; hidden arrivals are never radio intelligence.
        for (String key : List.of("cohort_resolved", "last_boarding", "road_arrival_1", "road_arrival_2"))
            assertNull(catalog.find("story_supply_route_last_lift", "supply_route.last_lift." + key));
        assertNull(catalog.find("story_supply_route_thunder_run", "supply_route.thunder_run.road_reserve_committed"));
        for (var line : catalog.lines()) {
            String suffix = line.event().substring(line.event().lastIndexOf('.') + 1);
            assertFalse(Set.of("victory", "defeat", "retreat").contains(suffix));
        }
    }

    private static WaylineNarration catalog() throws Exception {
        return WaylineNarration.parse(new JSONObject(Files.readString(Path.of("mod", WaylineNarration.PATH))));
    }

    private static void assertCoverage(WaylineNarration catalog, String chapter, StoryBeatLog log) {
        assertNotNull(catalog.find(chapter, "briefing"));
        for (var event : log.events()) {
            String suffix = event.id().substring(event.id().lastIndexOf('.') + 1);
            if (event.audience() == StoryBeatLog.Audience.OBSERVER_ONLY
                    || Set.of("victory", "defeat", "retreat").contains(suffix))
                assertNull(catalog.find(chapter, event.id()), event.id());
            else assertNotNull(catalog.find(chapter, event.id()), event.id());
        }
    }
}
