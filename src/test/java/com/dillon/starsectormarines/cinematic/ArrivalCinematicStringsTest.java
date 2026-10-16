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
 * line's string is the catalog's text, so the captions and the recorded lines cannot drift apart.
 */
class ArrivalCinematicStringsTest {
    private static final Path MOD = Path.of("mod");
    /** Catalog lines whose words already live under another key; the rule is cinematic_export.caption_key. */
    private static final Map<String, String> OVERRIDES = Map.of("jangala_arrival_nessa",
            "openingCompanyPrologueNessaVennWeRanOreTogetherBefore");

    private static Map<String, String> strings() throws Exception {
        String text = Files.readAllLines(MOD.resolve("data/strings/strings.json")).stream()
                .filter(line -> !line.stripLeading().startsWith("#")).collect(Collectors.joining("\n"));
        JSONObject ns = new JSONObject(text).getJSONObject("marineOps");
        Map<String, String> out = new HashMap<>();
        ns.keys().forEachRemaining(k -> out.put((String) k, ns.optString((String) k)));
        return out;
    }

    private static List<CinematicShot> timelines() throws Exception {
        List<CinematicShot> shots = new ArrayList<>();
        try (Stream<Path> files = Files.list(MOD.resolve("data/cinematic/jangala-arrival"))) {
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
        List<CinematicShot> shots = timelines();
        assertFalse(shots.isEmpty());
        for (CinematicShot shot : shots) {
            List<String> keys = new ArrayList<>();
            shot.captions().forEach(c -> keys.add(c.key()));
            shot.speakers().values().forEach(s -> keys.add(s.labelKey()));
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

    @Test void everyCaptionIsSpokenBySomeoneTheShotDeclares() throws Exception {
        for (CinematicShot shot : timelines())
            shot.captions().forEach(c -> assertTrue(shot.speakers().containsKey(c.speaker()), shot.id() + " " + c.key()));
    }
}
