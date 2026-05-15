package com.dillon.starsectormarines.assets.pipeline;

import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Schema for {@code audio.json}, the per-clip manifest read by {@link ProcessAudioTask}.
 *
 * <p>JSON shape:
 * <pre>{@code
 * {
 *   "clips": [
 *     { "id": "marines_battle_music_loop",
 *       "source": "music/battle_loop_01.wav",
 *       "kind":   "music",
 *       "out":    "battle/music" },
 *     { "id": "marines_rifle_fire",
 *       "source": "sfx/rifle_fire.wav",
 *       "kind":   "ui",
 *       "out":    "battle/sfx" }
 *   ]
 * }
 * }</pre>
 *
 * <p>{@code id} is the output filename (sans extension) and the ID Starsector uses to look the clip
 * up via {@code sounds.json}. {@code source} is the path relative to the manifest dir
 * ({@code src/tool/resources/audio/}). {@code out} is the subfolder under {@code mod/sounds/} the
 * encoded {@code .ogg} is written to; null/empty means write directly into {@code mod/sounds/}.
 *
 * <p>{@code kind} drives channel count, which Starsector requires to be split by usage:
 * positional engine sounds must be mono so OpenAL can spatialize them; UI sounds must be stereo.
 *
 * <p>Comments (//, /* *&#47;) are tolerated in the manifest so it can be hand-annotated.
 */
public record AudioManifest(List<AudioClip> clips) {

    public enum Kind {
        /** Non-positional UI / 2D playback. Transcoded to stereo. */
        UI,
        /** In-world positional playback through the combat engine. Transcoded to mono (OpenAL spatializes mono only). */
        POSITIONAL,
        /** Full-length music track. Transcoded to stereo. */
        MUSIC
    }

    public record AudioClip(String id, String source, Kind kind, String out) {}

    public static AudioManifest load(Path manifestPath) throws IOException {
        JsonMapper mapper = JsonMapper.builder()
                .enable(JsonReadFeature.ALLOW_JAVA_COMMENTS)
                .enable(JsonReadFeature.ALLOW_TRAILING_COMMA)
                .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
                .build();
        return mapper.readValue(Files.readString(manifestPath), AudioManifest.class);
    }
}
