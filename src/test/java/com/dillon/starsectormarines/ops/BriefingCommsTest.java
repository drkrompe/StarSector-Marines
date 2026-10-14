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
    @Test void productionModalButtonsBlockPlanningThenRestoreItAfterSkipAndReplay() throws Exception {
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
                document.inputRegions(() -> List.of(instance.requireElement(
                        comms.visible() ? "mission-comms-dialog" : "mission-briefing-root")));
                comms.update(instance);
                document.layout(MarineOpsUiViewport.REFERENCE_WIDTH, MarineOpsUiViewport.REFERENCE_HEIGHT);
                assertEquals("graphics/portraits/castle.png", instance.requireElement("mission-comms-portrait-left").imageSource());
                click(document, instance, "mission-deploy");
                assertEquals(0, planned[0]);
                click(document, instance, "mission-comms-next");
                comms.update(instance);
                assertEquals("vale", comms.current().key());
                assertEquals("graphics/portraits/tamsin-vale.png", instance.requireElement("mission-comms-portrait-right").imageSource());
                assertTrue(instance.requireElement("mission-comms-portrait-left").imageSource().isBlank());
                document.layout(MarineOpsUiViewport.REFERENCE_WIDTH, MarineOpsUiViewport.REFERENCE_HEIGHT);
                click(document, instance, "mission-comms-skip");
                comms.update(instance);
                assertFalse(comms.visible());
                assertNull(instance.requireElement("mission-comms-dialog").parent());
                document.layout(MarineOpsUiViewport.REFERENCE_WIDTH, MarineOpsUiViewport.REFERENCE_HEIGHT);
                click(document, instance, "mission-deploy");
                assertEquals(1, planned[0]);
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
    @Test void missingAudioHandleKeepsVoicedTextReadableUntilNext() {
        var comms = new BriefingComms(new NarrationPlayer(line -> null));
        var line = new WaylineNarration.Line("missing", "mission", "briefing", "Report.", 1, 1, 10, List.of());
        comms.enter(conversation(), List.of(line));
        comms.advance(1000, false);
        assertTrue(comms.visible());
        assertFalse(comms.busy());
        comms.next();
        assertFalse(comms.visible());
    }
    @Test void returningToSameMissionDoesNotAutoplayButNewMissionDoes() {
        var comms = new BriefingComms(new NarrationPlayer(line -> null));
        Mission mission = conversation();
        comms.enter(mission, List.of()); comms.advance(0, false); comms.stop();
        comms.enter(Mission.builder(mission).payout(20).build(), List.of());
        comms.advance(0, false);
        assertFalse(comms.visible());
        comms.replay(); comms.advance(0, false); comms.next(); comms.next();
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
        comms.replay(); comms.advance(0, false); comms.next(); comms.next();
        Mission rewritten = Mission.builder(mission).dialogue(List.of(MissionDialogue.say("Vale", "New work."))).build();
        comms.enter(rewritten, List.of()); comms.advance(0, false);
        assertFalse(comms.visible());
        comms.replay(); comms.advance(0, false);
        assertEquals("New work.", comms.current().text());
    }
    @Test void transcriptScrollResetsOnNextReplayAndChangedTextButNotFrameRefresh() {
        String template = "<template><div id='root'><div id='mission-comms-dialog'>"
                + "<div id='mission-comms-name'/><div id='mission-comms-role'/><div id='mission-comms-text'/>"
                + "<div id='mission-comms-transcript'/><img id='mission-comms-portrait-left'/>"
                + "<img id='mission-comms-portrait-right'/><div id='mission-comms-status'/>"
                + "<button id='mission-comms-next'/></div></div></template>";
        var markup = new MarkupLoader(path -> template, List.of("comms-test.mlx"))
                .reloadAndBuild(new Reactor(), "comms-test", Map.of());
        var comms = new BriefingComms(new NarrationPlayer(line -> null));
        Mission mission = conversation();
        comms.enter(mission, List.of()); comms.advance(0, false); comms.update(markup);
        var transcript = markup.requireElement("mission-comms-transcript");
        transcript.scrollTop(100); comms.update(markup);
        assertEquals(100, transcript.scrollTop());
        comms.next(); comms.update(markup);
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
    @Test void unrecordedTurnsWaitForReaderAndReplayAfterSkipOrCompletion() {
        var comms = new BriefingComms(new NarrationPlayer(line -> fail("Unrecorded text must not play audio")));
        comms.enter(conversation(), List.of());
        comms.advance(1000, false);
        assertTrue(comms.visible());
        assertEquals("castle", comms.current().key());
        comms.next();
        comms.advance(1000, false);
        assertEquals("vale", comms.current().key());
        assertTrue(comms.visible());
        comms.next();
        assertFalse(comms.visible());
        comms.replay(); comms.advance(0, false);
        assertEquals("castle", comms.current().key());
        comms.stop(); comms.advance(1000, false);
        assertFalse(comms.visible());
        comms.replay(); comms.advance(0, false);
        assertTrue(comms.visible());
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
        comms.advance(2, false);
        assertFalse(comms.visible());
    }
    @Test void onlyMatchingRecordedTurnAutoAdvancesAndManualNextStopsItsAudio() {
        var heard = new ArrayList<String>();
        var comms = new BriefingComms(new NarrationPlayer(line -> { heard.add(line.id()); return completedSound(); }));
        var first = new WaylineNarration.Line("castle", "mission", "briefing.01", "First.", 1, 1, 10,
                List.of(), "Castle", "graphics/portraits/castle.png");
        var stale = new WaylineNarration.Line("vale", "mission", "briefing.02", "Old text.", 1, 1, 10, List.of());
        comms.enter(conversation(), List.of(first, stale));
        comms.advance(0, false); comms.advance(2, false);
        assertEquals("vale", comms.current().key());
        comms.advance(100, false);
        assertTrue(comms.visible()); assertEquals(List.of("castle"), heard);
        comms.replay(); comms.advance(0, false); comms.next();
        assertFalse(comms.busy());
    }
}
