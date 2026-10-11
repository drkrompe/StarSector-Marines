package com.dillon.starsectormarines.assets.pipeline;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AudioEncodeCacheTest {
    @TempDir Path directory;
    private final AtomicInteger encodes = new AtomicInteger();

    private AudioEncodeCache cache() {
        return new AudioEncodeCache(directory.resolve("cache"));
    }

    private void encode(Path source, Path output) throws IOException {
        encodes.incrementAndGet();
        Files.writeString(output, "encoded:" + Files.readString(source));
    }

    @Test
    void addingOneClipOnlyEncodesTheNewSource() throws Exception {
        Path first = Files.writeString(directory.resolve("first.wav"), "first");
        Path second = Files.writeString(directory.resolve("second.wav"), "second");
        AudioEncodeCache cache = cache();
        Path output = directory.resolve("sounds/first.ogg");
        assertEquals(AudioEncodeCache.Result.ENCODED, cache.materialize(first, output, "recipe", this::encode));
        assertEquals(AudioEncodeCache.Result.UP_TO_DATE, cache.materialize(first, output, "recipe", this::encode));
        assertEquals(AudioEncodeCache.Result.ENCODED,
                cache.materialize(second, directory.resolve("sounds/second.ogg"), "recipe", this::encode));
        assertEquals(2, encodes.get());
    }

    @Test
    void freshWorktreeAndRenamedClipReuseIdenticalSourceBytes() throws Exception {
        Path source = Files.writeString(directory.resolve("source.wav"), "audio");
        cache().materialize(source, directory.resolve("old/name.ogg"), "recipe", this::encode);
        Path checkout = Files.copy(source, directory.resolve("another-checkout.wav"));
        Files.setLastModifiedTime(checkout, FileTime.fromMillis(1));
        Path output = directory.resolve("fresh/renamed.ogg");
        assertEquals(AudioEncodeCache.Result.RESTORED, cache().materialize(checkout, output, "recipe", this::encode));
        assertEquals("encoded:audio", Files.readString(output));
        assertEquals(1, encodes.get());
    }

    @Test
    void sameTimestampAndLengthCannotHideChangedSourceBytes() throws Exception {
        Path source = Files.writeString(directory.resolve("source.wav"), "before");
        Path output = directory.resolve("sounds/clip.ogg");
        FileTime timestamp = Files.getLastModifiedTime(source);
        cache().materialize(source, output, "recipe", this::encode);
        Files.writeString(source, "after!");
        Files.setLastModifiedTime(source, timestamp);
        assertEquals(AudioEncodeCache.Result.ENCODED, cache().materialize(source, output, "recipe", this::encode));
        assertEquals("encoded:after!", Files.readString(output));
        assertEquals(2, encodes.get());
    }

    @Test
    void channelAndEncoderChangesInvalidateOnlyTheirOwnRecipes() throws Exception {
        Path source = Files.writeString(directory.resolve("source.wav"), "audio");
        Path output = directory.resolve("sounds/clip.ogg");
        String stereo = ProcessAudioTask.outputArguments(AudioManifest.Kind.UI).toString();
        String mono = ProcessAudioTask.outputArguments(AudioManifest.Kind.POSITIONAL).toString();
        assertEquals(stereo, ProcessAudioTask.outputArguments(AudioManifest.Kind.MUSIC).toString());
        cache().materialize(source, output, "v1" + stereo, this::encode);
        cache().materialize(source, output, "v1" + mono, this::encode);
        cache().materialize(source, output, "v2" + mono, this::encode);
        cache().materialize(source, output, "v1" + stereo, this::encode);
        assertEquals(3, encodes.get());
    }

    @Test
    void radioTreatmentHasItsOwnCacheEntryAndLeavesCleanAudioReusable() throws Exception {
        Path source = Files.writeString(directory.resolve("voice.mp3"), "voice");
        Path clean = directory.resolve("clean.ogg"), radio = directory.resolve("radio.ogg");
        String original = ProcessAudioTask.outputArguments(AudioManifest.Kind.UI).toString();
        assertEquals(original, ProcessAudioTask.outputArguments(AudioManifest.Kind.UI, null).toString());
        String comms = ProcessAudioTask.outputArguments(AudioManifest.Kind.UI, AudioManifest.Processing.COMMS).toString();
        AudioEncodeCache cache = cache();
        cache.materialize(source, clean, original, this::encode);
        cache.materialize(source, radio, comms, this::encode);
        assertEquals(AudioEncodeCache.Result.UP_TO_DATE, cache.materialize(source, clean, original, this::encode));
        assertEquals(AudioEncodeCache.Result.UP_TO_DATE, cache.materialize(source, radio, comms, this::encode));
        assertEquals(2, encodes.get());
        String dialogue = ProcessAudioTask.outputArguments(AudioManifest.Kind.UI, AudioManifest.Processing.DIALOGUE).toString();
        assertTrue(dialogue.contains("loudnorm=I=-20:TP=-2:LRA=7"));
        cache.materialize(source, directory.resolve("dialogue.ogg"), dialogue, this::encode);
        assertEquals(3, encodes.get());
    }

    @Test
    void failedAndEmptyEncodesPreservePriorOutputAndAreRetried() throws Exception {
        Path source = Files.writeString(directory.resolve("source.wav"), "audio");
        Path output = Files.writeString(directory.resolve("clip.ogg"), "previous good audio");
        assertThrows(IOException.class, () -> cache().materialize(source, output, "recipe", (input, temporary) -> {
            Files.writeString(temporary, "partial encode");
            throw new IOException("encoder failed");
        }));
        assertThrows(IOException.class, () -> cache().materialize(source, output, "recipe", (input, temporary) -> { }));
        assertEquals("previous good audio", Files.readString(output));
        try (var files = Files.list(directory.resolve("cache"))) {
            assertTrue(files.noneMatch(path -> path.toString().endsWith(".ogg")));
        }
        assertEquals(AudioEncodeCache.Result.ENCODED, cache().materialize(source, output, "recipe", this::encode));
    }

    @Test
    void damagedOutputIsRestoredWithoutEncoding() throws Exception {
        Path source = Files.writeString(directory.resolve("source.wav"), "audio");
        Path output = directory.resolve("clip.ogg");
        cache().materialize(source, output, "recipe", this::encode);
        Files.writeString(output, "truncated");
        assertEquals(AudioEncodeCache.Result.RESTORED, cache().materialize(source, output, "recipe", this::encode));
        assertEquals("encoded:audio", Files.readString(output));
        assertEquals(1, encodes.get());
    }
}
