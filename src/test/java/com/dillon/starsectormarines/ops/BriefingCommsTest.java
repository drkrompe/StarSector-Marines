package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.audio.NarrationPlayer;
import com.dillon.starsectormarines.battle.audio.WaylineNarration;
import org.junit.jupiter.api.Test;
import com.fs.starfarer.api.SoundAPI;
import com.dillon.starsectormarines.testsupport.HeadlessStrings;
import com.dillon.starsectormarines.ui.retained.UiDocument;
import com.dillon.starsectormarines.ui.retained.markup.MarkupLoader;
import com.dillon.starsectormarines.ui.retained.markup.MarkupInstance;
import com.dillon.starsectormarines.ui.retained.reactive.Reactor;
import java.nio.file.Path;
import java.nio.file.Files;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class BriefingCommsTest {
    @Test void inlineConversationKeepsPlanningInteractiveAndChangesSinglePortrait() throws Exception {
        try (var strings = new HeadlessStrings(Path.of("mod"))) {
            var comms = new BriefingComms(new NarrationPlayer(line -> fail("Text-only dialogue has no playback")));
            comms.enter(conversation(), List.of());
            comms.advance(0, false);
            var props = BriefingViewModel.campaignPreviewProps(false);
            comms.put(props);
            BriefingViewModel.putNarration(props, comms.contact(), false);
            int[] planned = {0};
            props.put("deployDisabled", false);
            props.put("deployAction", (Runnable) () -> planned[0]++);
            props.put("replayNarrationAction", (Runnable) comms::replay);
            var loader = new MarkupLoader(path -> Files.readString(Path.of("mod").resolve(path)),
                    BriefingScreen.COMPONENT_PATHS);
            try (var instance = loader.reloadAndBuild(new Reactor(), BriefingScreen.ROOT_COMPONENT, props)) {
                BriefingViewModel.applyVisibility(instance, props);
                var document = new UiDocument(instance.root()).theme(MarineOpsThemes.standard());
                instance.styles().forEach(document::addStyleSheet);
                comms.update(instance);
                document.layout(MarineOpsUiViewport.REFERENCE_WIDTH, MarineOpsUiViewport.REFERENCE_HEIGHT);
                assertEquals("graphics/portraits/castle.png", instance.requireElement("mission-briefing-speaker-portrait").imageSource());
                click(document, instance, "mission-deploy");
                assertEquals(1, planned[0]);
                assertThrows(IllegalArgumentException.class, () -> instance.requireElement("mission-comms-next"));
                assertThrows(IllegalArgumentException.class, () -> instance.requireElement("mission-comms-previous"));
                assertFalse(props.containsKey("commsNextAction"));
                advanceTurn(comms);
                comms.update(instance);
                assertEquals("vale", comms.current().key());
                assertEquals("graphics/portraits/tamsin-vale.png", instance.requireElement("mission-briefing-speaker-portrait").imageSource());
                document.layout(MarineOpsUiViewport.REFERENCE_WIDTH, MarineOpsUiViewport.REFERENCE_HEIGHT);
                click(document, instance, "mission-comms-skip");
                comms.update(instance);
                assertFalse(comms.visible());
                assertNull(instance.requireElement("mission-comms-dialog").parent());
                document.layout(MarineOpsUiViewport.REFERENCE_WIDTH, MarineOpsUiViewport.REFERENCE_HEIGHT);
                click(document, instance, "mission-deploy");
                assertEquals(2, planned[0]);
                click(document, instance, "mission-narration-replay");
                comms.advance(0, false);
                comms.update(instance);
                assertTrue(comms.visible());
                assertEquals("castle", comms.current().key());
                assertNotNull(instance.requireElement("mission-comms-dialog").parent());
            }
        }
    }

    private static void click(UiDocument document, MarkupInstance instance, String id) {
        var box = instance.requireElement(id).box().borderBox();
        assertTrue(box.width() > 0 && box.height() > 0, "Control has a real hit box: " + id);
        float x = box.x() + box.width() / 2f, y = box.y() + box.height() / 2f;
        document.pointerMoved(x, y);
        document.pointerDown(x, y);
        document.pointerUp(x, y);
        instance.flush();
        document.advance(.25f);
    }

    private static SoundAPI completedSound() {
        return (SoundAPI) Proxy.newProxyInstance(SoundAPI.class.getClassLoader(), new Class<?>[] { SoundAPI.class },
                (proxy, method, args) -> method.getName().equals("isPlaying") ? false : null);
    }
    @Test void missingAudioHandleUsesReadableFallbackAndFinishesAutomatically() {
        var comms = new BriefingComms(new NarrationPlayer(line -> null));
        var line = new WaylineNarration.Line("missing", "mission", "briefing", "Report.", 1, 1, 10, List.of());
        comms.enter(conversation(), List.of(line));
        comms.advance(0, false);
        comms.advance(1, false);
        assertTrue(comms.visible());
        assertFalse(comms.busy());
        advanceTurn(comms);
        assertFalse(comms.visible());
    }
    @Test void returningToSameMissionDoesNotAutoplayButNewMissionDoes() {
        var comms = new BriefingComms(new NarrationPlayer(line -> null));
        Mission mission = conversation();
        comms.enter(mission, List.of()); comms.advance(0, false); comms.stop();
        comms.enter(Mission.builder(mission).payout(20).build(), List.of());
        comms.advance(0, false);
        assertFalse(comms.visible());
        comms.replay(); comms.advance(0, false); advanceTurn(comms); advanceTurn(comms);
        comms.enter(mission, List.of()); comms.advance(0, false);
        assertFalse(comms.visible());
        comms.enter(Mission.builder(mission).id("new-mission").build(), List.of()); comms.advance(0, false);
        assertTrue(comms.visible());
        assertEquals("castle", comms.current().key());
    }
    @Test void refreshedSameMissionKeepsDismissalButDropsStaleRecording() {
        var comms = new BriefingComms(new NarrationPlayer(line -> completedSound()));
        Mission mission = conversation();
        var legacy = new WaylineNarration.Line("old", "mission", "briefing", "Old work.", 1, 1, 10, List.of());
        comms.enter(mission, List.of(legacy)); comms.advance(0, false); comms.stop();
        comms.enter(mission, List.of()); comms.advance(0, false);
        assertFalse(comms.visible());
        assertEquals("First.", comms.current().text());
        assertNull(comms.current().recording());
        comms.replay(); comms.advance(0, false); advanceTurn(comms); advanceTurn(comms);
        Mission rewritten = Mission.builder(mission).dialogue(List.of(MissionDialogue.say("Vale", "New work."))).build();
        comms.enter(rewritten, List.of()); comms.advance(0, false);
        assertFalse(comms.visible());
        comms.replay(); comms.advance(0, false);
        assertEquals("New work.", comms.current().text());
    }
    @Test void transcriptScrollResetsOnAutomaticTurnReplayAndChangedTextButNotFrameRefresh() {
        String template = "<template><div id='root'><div id='mission-comms-dialog'>"
                + "<div id='mission-briefing-speaker'><div id='mission-briefing-speaker-name'/>"
                + "<div id='mission-briefing-speaker-role'/><img id='mission-briefing-speaker-portrait'/></div>"
                + "<div id='mission-comms-text'/><div id='mission-comms-transcript'/><div id='mission-comms-status'/>"
                + "</div></div></template>";
        var markup = new MarkupLoader(path -> template, List.of("comms-test.mlx"))
                .reloadAndBuild(new Reactor(), "comms-test", Map.of());
        var comms = new BriefingComms(new NarrationPlayer(line -> null));
        Mission mission = conversation();
        comms.enter(mission, List.of()); comms.advance(0, false); comms.update(markup);
        var transcript = markup.requireElement("mission-comms-transcript");
        transcript.scrollTop(100); comms.update(markup);
        assertEquals(100, transcript.scrollTop());
        advanceTurn(comms); comms.update(markup);
        assertEquals(0, transcript.scrollTop());
        transcript.scrollTop(100); comms.replay(); comms.update(markup);
        assertEquals(0, transcript.scrollTop());
        transcript.scrollTop(100);
        comms.enter(Mission.builder(mission).dialogue(List.of(MissionDialogue.say("Castle", "Changed."))).build(), List.of());
        comms.update(markup);
        assertEquals(0, transcript.scrollTop());
        markup.close();
    }
    private static Mission conversation() {
        return Mission.builder().id("conversation").dialogue(List.of(
                MissionDialogue.say("Castle", "First."), MissionDialogue.say("Vale", "Second."))).build();
    }
    @Test void unrecordedTurnsAutomaticallyContinueAndReplayAfterSkipOrCompletion() {
        var comms = new BriefingComms(new NarrationPlayer(line -> fail("Unrecorded text must not play audio")));
        comms.enter(conversation(), List.of());
        comms.advance(0, false);
        comms.advance(1, false);
        assertTrue(comms.visible());
        assertEquals("castle", comms.current().key());
        advanceTurn(comms);
        assertEquals("vale", comms.current().key());
        assertTrue(comms.visible());
        advanceTurn(comms);
        assertFalse(comms.visible());
        comms.replay(); comms.advance(0, false);
        assertEquals("castle", comms.current().key());
        comms.stop(); comms.advance(1000, false);
        assertFalse(comms.visible());
        comms.replay(); comms.advance(0, false);
        assertTrue(comms.visible());
    }
    @Test void longSubtitleUsesReadableFallbackDespiteMissingShortRecording() {
        var comms = new BriefingComms(new NarrationPlayer(line -> null));
        String text = "The landing zone remains under observation until every transport clears the approach. ".repeat(8);
        Mission mission = Mission.builder().id("long").dialogue(List.of(MissionDialogue.say("Castle", text))).build();
        var missing = new WaylineNarration.Line("missing-long", "mission", "briefing", text, 1, 1, 10, List.of());
        comms.enter(mission, List.of(missing));
        comms.advance(0, false);
        for (int i = 0; i < 200; i++) comms.advance(.1f, false);
        assertTrue(comms.visible(), "A long briefing needs more than twenty seconds to read");
        advanceTurn(comms);
        assertFalse(comms.visible());
    }
    private static void advanceTurn(BriefingComms comms) {
        var previous = comms.current();
        for (int i = 0; i < 1200 && comms.visible() && comms.current() == previous; i++) {
            comms.advance(.1f, false);
        }
        assertTrue(!comms.visible() || comms.current() != previous, "Conversation must advance within two minutes");
    }
    @Test void explicitRecordingIdentitySurvivesTranslationAndCatalogOrder() {
        Mission mission = Mission.builder().id("translated").dialogue(List.of(
                new MissionDialogue.Turn("castle", "Castle", "Translated current text.", "stable-turn"))).build();
        var wanted = new WaylineNarration.Line("stable-turn", "mission", "briefing.02", "Original spoken text.",
                1, 1, 10, List.of(), "Castle", "graphics/portraits/castle.png");
        var other = new WaylineNarration.Line("other", "mission", "briefing.01", "Other.", 1, 1, 10, List.of());
        var turns = BriefingComms.conversation(mission, List.of(other, wanted));
        assertEquals("Translated current text.", turns.get(0).text());
        assertSame(wanted, turns.get(0).recording());
    }
    @Test void tutorialDefersAndSuspendsConversationWithoutAdvancingText() {
        var heard = new ArrayList<String>();
        var comms = new BriefingComms(new NarrationPlayer(line -> { heard.add(line.id()); return completedSound(); }));
        var line = new WaylineNarration.Line("legacy", "mission", "briefing", "Report.", 1, 1, 10, List.of());
        comms.enter(conversation(), List.of(line));
        comms.advance(50, true);
        assertFalse(comms.visible()); assertTrue(heard.isEmpty());
        comms.advance(0, false);
        assertEquals(List.of("legacy"), heard);
        comms.advance(0, true);
        assertFalse(comms.visible()); assertFalse(comms.busy());
        comms.advance(0, false);
        assertEquals(List.of("legacy", "legacy"), heard);
        advanceTurn(comms);
        assertFalse(comms.visible());
    }
    @Test void matchingRecordingFinishesThenStaleRecordingUsesTextFallback() {
        var heard = new ArrayList<String>();
        var comms = new BriefingComms(new NarrationPlayer(line -> { heard.add(line.id()); return completedSound(); }));
        var first = new WaylineNarration.Line("castle", "mission", "briefing.01", "First.", 1, 1, 10,
                List.of(), "Castle", "graphics/portraits/castle.png");
        var stale = new WaylineNarration.Line("vale", "mission", "briefing.02", "Old text.", 1, 1, 10, List.of());
        comms.enter(conversation(), List.of(first, stale));
        comms.advance(0, false); advanceTurn(comms);
        assertEquals("vale", comms.current().key());
        comms.advance(0, false); comms.advance(1, false);
        assertTrue(comms.visible()); assertEquals(List.of("castle"), heard);
        advanceTurn(comms);
        assertFalse(comms.visible());
    }
    @Test void playingRecordingIsNeverTruncatedAndSkipAndReplayStopItsHandle() {
        boolean[] playing = {true};
        int[] stops = {0};
        SoundAPI sound = (SoundAPI) Proxy.newProxyInstance(SoundAPI.class.getClassLoader(),
                new Class<?>[] { SoundAPI.class }, (proxy, method, args) -> {
                    if (method.getName().equals("isPlaying")) return playing[0];
                    if (method.getName().equals("stop")) stops[0]++;
                    return null;
                });
        var line = new WaylineNarration.Line("castle", "mission", "briefing.01", "First.", 1, 1, 10, List.of());
        var comms = new BriefingComms(new NarrationPlayer(recording -> sound));
        comms.enter(conversation(), List.of(line)); comms.advance(0, false);
        for (int i = 0; i < 1200; i++) comms.advance(.1f, false);
        assertTrue(comms.visible()); assertTrue(comms.busy());
        assertEquals("castle", comms.current().key()); assertEquals(0, stops[0]);
        comms.replay(); assertEquals(1, stops[0]); assertFalse(comms.busy());
        comms.advance(0, false); comms.stop();
        assertEquals(2, stops[0]); assertFalse(comms.visible());
        comms.replay(); comms.advance(0, false);
        playing[0] = false; advanceTurn(comms);
        assertEquals("vale", comms.current().key());
    }
    @Test void tutorialRestartsTextFallbackInsteadOfConsumingItsRemainingTime() {
        var comms = new BriefingComms(new NarrationPlayer(line -> null));
        comms.enter(conversation(), List.of()); comms.advance(0, false);
        comms.advance(3, false);
        comms.advance(100, true);
        assertFalse(comms.visible()); assertEquals("castle", comms.current().key());
        comms.advance(0, false); comms.advance(2, false);
        assertTrue(comms.visible()); assertEquals("castle", comms.current().key());
        advanceTurn(comms);
        assertEquals("vale", comms.current().key());
    }
}
