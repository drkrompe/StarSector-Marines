package com.dillon.starsectormarines.cinematic;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The shipped timelines hold keys and no English: every key resolves in strings.json, and a voiced
 * line's string is the catalog's text, so the captions and the recorded lines cannot drift apart. The
 * key, speaker and sound rules hold for every film under data/cinematic; the catalog rules are the
 * arrival's.
 */
class ArrivalCinematicStringsTest {
    private static final Path MOD = Path.of("mod");
    /** Catalog lines whose words already live under another key; the rule is cinematic_export.caption_key. */
    private static final Map<String, String> OVERRIDES = Map.of();

    private static Map<String, String> strings() throws Exception {
        String text = Files.readAllLines(MOD.resolve("data/strings/strings.json")).stream()
                .filter(line -> !line.stripLeading().startsWith("#")).collect(Collectors.joining("\n"));
        JSONObject ns = new JSONObject(text).getJSONObject("marineOps");
        Map<String, String> out = new HashMap<>();
        ns.keys().forEachRemaining(k -> out.put((String) k, ns.optString((String) k)));
        return out;
    }

    private static List<CinematicShot> timelines() throws Exception {
        return timelines("jangala-arrival");
    }

    /** Every film's shots. */
    private static List<CinematicShot> allTimelines() throws Exception {
        List<CinematicShot> shots = new ArrayList<>();
        try (Stream<Path> films = Files.list(MOD.resolve("data/cinematic"))) {
            for (Path film : films.filter(Files::isDirectory).toList()) shots.addAll(timelines(film.getFileName().toString()));
        }
        return shots;
    }

    private static List<CinematicShot> timelines(String film) throws Exception {
        List<CinematicShot> shots = new ArrayList<>();
        try (Stream<Path> files = Files.list(MOD.resolve("data/cinematic").resolve(film))) {
            for (Path file : files.filter(p -> p.toString().endsWith(".json"))
                    .filter(p -> !p.getFileName().toString().equals("sequence.json")).toList())
                shots.add(CinematicShot.parse(new JSONObject(Files.readString(file))));
        }
        return shots;
    }

    private static String keyOf(String lineId) {
        if (OVERRIDES.containsKey(lineId)) return OVERRIDES.get(lineId);
        String[] parts = lineId.split("_");
        StringBuilder key = new StringBuilder(parts[0]);
        for (int i = 1; i < parts.length; i++)
            key.append(Character.toUpperCase(parts[i].charAt(0))).append(parts[i].substring(1));
        return key.toString();
    }

    @Test void everyKeyAShotNamesResolvesInStringsJson() throws Exception {
        Map<String, String> strings = strings();
        List<CinematicShot> shots = allTimelines();
        assertFalse(shots.isEmpty());
        for (CinematicShot shot : shots) {
            List<String> keys = new ArrayList<>();
            shot.captions().forEach(c -> keys.add(c.key()));
            shot.speakers().values().forEach(s -> {
                keys.add(s.labelKey());
                if (s.roleKey() != null) keys.add(s.roleKey());
            });
            shot.locks().forEach(l -> {
                keys.add(l.titleKey());
                l.detail().forEach(d -> keys.add(d.key()));
            });
            for (String key : keys) assertTrue(strings.containsKey(key), shot.id() + " names missing key " + key);
        }
    }

    @Test void everyVoicedLinesStringIsItsCatalogText() throws Exception {
        Map<String, String> strings = strings();
        JSONArray lines = new JSONObject(Files.readString(MOD.resolve("data/marines/jangala_arrival_hail.json")))
                .getJSONArray("lines");
        for (int i = 0; i < lines.length(); i++) {
            JSONObject line = lines.getJSONObject(i);
            assertEquals(line.getString("text"), strings.get(keyOf(line.getString("id"))), line.getString("id"));
        }
    }

    /**
     * Every catalog line, the hail and Castle's lines alike, is cued once across the arrival and
     * captioned by its own key in the shot that plays it; nothing outside the catalog is captioned.
     */
    @Test void everyCatalogLineIsCuedOnceAndCaptionedWhereItPlays() throws Exception {
        JSONArray lines = new JSONObject(Files.readString(MOD.resolve("data/marines/jangala_arrival_hail.json")))
                .getJSONArray("lines");
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < lines.length(); i++) ids.add(lines.getJSONObject(i).getString("id"));
        List<CinematicShot> shots = timelines();
        List<String> captioned = new ArrayList<>();
        for (CinematicShot shot : shots) shot.captions().forEach(c -> captioned.add(c.key()));
        for (String id : ids) {
            List<CinematicShot> playing = shots.stream()
                    .filter(s -> s.cues().stream().anyMatch(c -> c.sound().equals(id))).toList();
            assertEquals(1, playing.size(), id + " is cued in " + playing.size() + " shots");
            long cues = playing.get(0).cues().stream().filter(c -> c.sound().equals(id)).count();
            assertEquals(1, cues, id + " is cued more than once");
            assertTrue(playing.get(0).captions().stream().anyMatch(c -> c.key().equals(keyOf(id))),
                    playing.get(0).id() + " plays " + id + " without its caption");
        }
        List<String> keys = ids.stream().map(ArrivalCinematicStringsTest::keyOf).toList();
        for (String key : captioned) assertTrue(keys.contains(key), key + " is captioned but is no catalog line");
    }

    @Test void everyCueNamesARegisteredSound() throws Exception {
        String sounds = Files.readString(MOD.resolve("data/config/sounds.json"));
        for (CinematicShot shot : allTimelines())
            shot.cues().forEach(c -> assertTrue(sounds.contains("\"" + c.sound() + "\":"), shot.id() + " cues unregistered " + c.sound()));
    }

    @Test void everyCaptionIsSpokenBySomeoneTheShotDeclares() throws Exception {
        for (CinematicShot shot : allTimelines())
            shot.captions().forEach(c -> assertTrue(shot.speakers().containsKey(c.speaker()), shot.id() + " " + c.key()));
    }
}
