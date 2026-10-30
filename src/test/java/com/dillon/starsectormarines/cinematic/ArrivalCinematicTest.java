package com.dillon.starsectormarines.cinematic;

import com.dillon.starsectormarines.campaign.start.CerberusCompanyStart;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArrivalCinematicTest {
    private static final String SHOT = """
            {"id":"%s","video":"cinematic/%s.mp4","fps":24,"width":1920,"height":1080,"frames":48,"duration":2.0,
             "speakers":{"approach":{"label_key":"A","color":"hegemony"}},
             "captions":[{"t_in":0.5,"t_out":1.5,"speaker":"approach","key":"hello"}],
             "overlays":[{"kind":"lock","t_in":0.2,"t_out":1.0,"title_key":"X","detail":[{"key":"Y","color":"player"}],
                          "track":[[0.2,10,20,30],[1.9,11,21,31]]},{"kind":"unknown"}],
             "cues":[{"t":0.0,"sound":"bed","volume":0.5,"until":2.0,"fade_in":1.0},{"t":1.0,"sound":"hail"}]}""";

    @Test void shotsWithoutAVideoOrATimelineAreLeftOut() throws Exception {
        Map<String, JSONObject> files = new HashMap<>();
        files.put(ArrivalCinematic.FILM.data() + "sequence.json", new JSONObject("{\"shots\":[\"c1\",\"c2\",\"c3\"]}"));
        files.put(ArrivalCinematic.FILM.data() + "c1.json", new JSONObject(SHOT.formatted("c1", "c1")));
        files.put(ArrivalCinematic.FILM.data() + "c3.json", new JSONObject(SHOT.formatted("c3", "c3")));   // c2 has no timeline
        // c3 has a timeline but its video is not on disk.
        var sequence = ArrivalCinematic.FILM.load(path -> path.contains("c1"), path -> {
            var json = files.get(path);
            if (json == null) throw new IllegalStateException("no " + path);
            return json;
        });
        assertEquals(1, sequence.size());
        assertEquals("c1", sequence.shot(0).id());
        assertEquals(CinematicSequence.Captions.ALWAYS, sequence.captions());
    }

    @Test void aFilmReadsItsOwnFolderAndItsCaptionOption() throws Exception {
        var film = new CinematicFilm("other-film", "receipt");
        Map<String, JSONObject> files = Map.of(
                "data/cinematic/other-film/sequence.json", new JSONObject("{\"shots\":[\"c1\"],\"captions\":\"translated\"}"),
                "data/cinematic/other-film/c1.json", new JSONObject(SHOT.formatted("c1", "c1")));
        var sequence = film.load(path -> true, path -> {
            var json = files.get(path);
            if (json == null) throw new IllegalStateException("no " + path);
            return json;
        });
        assertEquals(1, sequence.size());
        assertEquals(CinematicSequence.Captions.TRANSLATED, sequence.captions());
    }

    @Test void aSpeakersRoleIsOptional() throws Exception {
        var shot = CinematicShot.parse(new JSONObject("""
                {"id":"s","video":"v.mp4","frames":1,"duration":1,
                 "speakers":{"caravel":{"label_key":"L","role_key":"R","color":"player"},"raider":{"label_key":"P"}}}"""));
        assertEquals("R", shot.speakers().get("caravel").roleKey());
        assertNull(shot.speakers().get("raider").roleKey());
    }

    @Test void aTimelineParsesCaptionsLocksAndCuesAndIgnoresUnknownOverlays() throws Exception {
        var shot = CinematicShot.parse(new JSONObject(SHOT.formatted("c1", "c1")));
        assertEquals(1, shot.captions().size());
        assertEquals(1, shot.locks().size());
        assertEquals(2, shot.cues().size());
        assertTrue(shot.cues().get(0).looped());
        assertFalse(shot.cues().get(1).looped());
        assertEquals("hegemony", shot.speakers().get("approach").color());
    }

    @Test void itIsDueOnlyAtJangalaOnTheDestinationAfterThePrologueAndOnce() {
        Map<String, Object> saved = new HashMap<>();
        saved.put(CerberusCompanyStart.SHIP_KEY, "ship");
        var jangala = new PlanetStub("jangala").api();
        var other = new PlanetStub("other").api();
        assertTrue(ArrivalCinematic.due(saved, jangala, jangala));
        assertFalse(ArrivalCinematic.due(saved, other, jangala));
        assertFalse(ArrivalCinematic.due(saved, jangala, other));
        assertFalse(ArrivalCinematic.due(new HashMap<>(), jangala, jangala));
        saved.put("sm_jangala_arrival_played", true);
        assertFalse(ArrivalCinematic.due(saved, jangala, jangala));
    }
}
