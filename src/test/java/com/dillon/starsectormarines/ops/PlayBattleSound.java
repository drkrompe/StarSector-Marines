package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.ops.battleview.BattleSoundMix;
import com.fs.starfarer.api.SoundAPI;
import com.fs.starfarer.api.SoundPlayerAPI;
import com.jcraft.jogg.Packet;
import com.jcraft.jogg.Page;
import com.jcraft.jogg.StreamState;
import com.jcraft.jogg.SyncState;
import com.jcraft.jorbis.Block;
import com.jcraft.jorbis.Comment;
import com.jcraft.jorbis.DspState;
import com.jcraft.jorbis.Info;
import org.lwjgl.BufferUtils;
import org.lwjgl.LWJGLException;
import org.lwjgl.openal.AL;
import org.lwjgl.util.vector.Vector2f;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.lwjgl.openal.AL10.AL_BUFFER;
import static org.lwjgl.openal.AL10.AL_FORMAT_MONO16;
import static org.lwjgl.openal.AL10.AL_FORMAT_STEREO16;
import static org.lwjgl.openal.AL10.AL_GAIN;
import static org.lwjgl.openal.AL10.AL_INVERSE_DISTANCE_CLAMPED;
import static org.lwjgl.openal.AL10.AL_LOOPING;
import static org.lwjgl.openal.AL10.AL_MAX_DISTANCE;
import static org.lwjgl.openal.AL10.AL_PITCH;
import static org.lwjgl.openal.AL10.AL_POSITION;
import static org.lwjgl.openal.AL10.AL_REFERENCE_DISTANCE;
import static org.lwjgl.openal.AL10.AL_ROLLOFF_FACTOR;
import static org.lwjgl.openal.AL10.AL_SOURCE_RELATIVE;
import static org.lwjgl.openal.AL10.AL_SOURCE_STATE;
import static org.lwjgl.openal.AL10.AL_STOPPED;
import static org.lwjgl.openal.AL10.alBufferData;
import static org.lwjgl.openal.AL10.alDistanceModel;
import static org.lwjgl.openal.AL10.alDopplerFactor;
import static org.lwjgl.openal.AL10.alGenBuffers;
import static org.lwjgl.openal.AL10.alGenSources;
import static org.lwjgl.openal.AL10.alGetSourcei;
import static org.lwjgl.openal.AL10.alListener3f;
import static org.lwjgl.openal.AL10.alListenerf;
import static org.lwjgl.openal.AL10.alSource3f;
import static org.lwjgl.openal.AL10.alSourcePlay;
import static org.lwjgl.openal.AL10.alSourceStop;
import static org.lwjgl.openal.AL10.alSourcef;
import static org.lwjgl.openal.AL10.alSourcei;

/**
 * The battle's sound through OpenAL, with no game running ({@code playBattle}). The ids resolve as the offline mix's do
 * ({@link BattleSoundMix#takes}), a take picked at random as the game picks, and the clips decode with the game's own
 * Vorbis library.
 *
 * <p>The player's contracts are the game's: a loop sounds only while it is asked for every frame, a positional sound
 * hangs off the listener the screen parks at the camera, and a stereo clip does not pan. <b>The roll-off is not the
 * game's:</b> its engine is obfuscated, so distances here ({@link #LISTENER_HEIGHT}) are chosen to pan and fall away
 * sensibly across a screen. Trust this for what plays and when; set final levels in the game.
 *
 * <p>All OpenAL calls stay on the thread that calls {@link #update}, the render thread. Clips decode on workers so a
 * first shot never stalls a frame; a one-shot still waiting after {@link #STALE_SECONDS} is dropped, since a gunshot
 * heard late is worse than one missed.
 */
final class PlayBattleSound implements SoundPlayerAPI, AutoCloseable {

    /** World units the listener floats over the map, about half a view: panning eases rather than snapping left or right. */
    private static final float LISTENER_HEIGHT = 800f;
    private static final float MAX_DISTANCE = 6000f;
    private static final int MAX_SOURCES = 128;
    /** One-shots cannot take the last few sources, so a volley never cuts off music, a voice or a loop. */
    private static final int RESERVED_SOURCES = 16;
    private static final float STALE_SECONDS = 0.25f;
    /** The offline mix's {@code LOOP_FADE_SECONDS}, for a loop that names no fade of its own. */
    private static final float LOOP_FADE_SECONDS = 0.25f;

    private final Map<String, List<BattleSoundMix.Take>> takes;
    private final Random random = new Random();
    private final Map<Path, Clip> clips = new HashMap<>();
    private final ExecutorService decoders = Executors.newFixedThreadPool(2, runnable -> {
        Thread worker = new Thread(runnable, "play-battle-decode");
        worker.setDaemon(true);
        return worker;
    });
    private final ConcurrentLinkedQueue<Decoded> decoded = new ConcurrentLinkedQueue<>();
    private final Deque<Integer> freeSources = new ArrayDeque<>();
    private final List<Voice> voices = new ArrayList<>();
    private final Map<LoopKey, Voice> loops = new HashMap<>();
    private final Set<String> unknown = new TreeSet<>();
    private final Set<String> tracks = new TreeSet<>();
    private int sourcesMade;
    private int sourcesFree;
    private Voice music;
    private Vector2f listener = new Vector2f();
    private float nextLoopFadeIn = -1f;
    private float nextLoopFadeOut = -1f;
    private int started;
    private int dropped;
    private int uploaded;

    private record LoopKey(String id, Object entity) { }

    private record Decoded(Clip clip, Pcm pcm) { }

    private record Pcm(ByteBuffer samples, int channels, int rate) { }

    /** One file: its buffer once decoded, and whatever is waiting to play it until then. */
    private static final class Clip {
        final Path file;
        final List<Voice> waiting = new ArrayList<>();
        int buffer;
        boolean failed;

        Clip(Path file) {
            this.file = file;
        }
    }

    private final class Voice implements SoundAPI {
        final Clip clip;
        final float takeVolume;
        final float takePitch;
        final boolean relative;
        final boolean looping;
        final boolean oneShot;
        float x;
        float y;
        float volume;
        float pitch;
        float fade;
        float fadeTarget = 1f;
        float fadeSeconds;
        float waited;
        int source;
        boolean asked = true;
        boolean stopped;

        Voice(BattleSoundMix.Take take, Clip clip, float volume, float pitch, boolean relative, boolean looping,
              boolean oneShot, float fadeInSeconds) {
            this.clip = clip;
            takeVolume = take.volume();
            takePitch = take.pitch();
            this.volume = volume;
            this.pitch = pitch;
            this.relative = relative;
            this.looping = looping;
            this.oneShot = oneShot;
            fadeSeconds = fadeInSeconds;
            fade = fadeInSeconds > 0f ? 0f : 1f;
        }

        void apply() {
            if (source == 0) return;
            alSourcef(source, AL_GAIN, Math.max(0f, volume * takeVolume * fade));
            alSourcef(source, AL_PITCH, Math.max(0.01f, pitch * takePitch));
            alSource3f(source, AL_POSITION, x, y, 0f);
        }

        void fadeTo(float target, float seconds) {
            fadeTarget = target;
            fadeSeconds = seconds;
        }

        @Override public void stop() {
            stopped = true;
        }

        @Override public void setVolume(float newValue) {
            volume = newValue;
        }

        @Override public void setPitch(float newPitch) {
            pitch = newPitch;
        }

        @Override public void setLocation(float newX, float newY) {
            x = newX;
            y = newY;
        }

        @Override public boolean isPlaying() {
            return !stopped;
        }
    }

    private PlayBattleSound(Map<String, List<BattleSoundMix.Take>> takes) {
        this.takes = takes;
    }

    /** Opens the machine's sound device, or returns null and says why: a battle plays fine without one. */
    static PlayBattleSound openOrNull(Path modRoot, float masterVolume) throws IOException {
        Map<String, List<BattleSoundMix.Take>> takes = BattleSoundMix.takes(modRoot);
        try {
            AL.create();
        } catch (LWJGLException | UnsatisfiedLinkError failure) {
            System.out.println("[playBattle] no sound: " + failure);
            return null;
        }
        alDistanceModel(AL_INVERSE_DISTANCE_CLAMPED);
        alDopplerFactor(0f);
        alListenerf(AL_GAIN, masterVolume);
        return new PlayBattleSound(takes);
    }

    /** Once a frame, after the screen has advanced: uploads clips, starts what waited, ages fades and unasked loops. */
    void update(float dt) {
        alListener3f(AL_POSITION, listener.x, listener.y, LISTENER_HEIGHT);
        for (Decoded done; (done = decoded.poll()) != null; ) upload(done);

        for (Iterator<Map.Entry<LoopKey, Voice>> it = loops.entrySet().iterator(); it.hasNext(); ) {
            Voice loop = it.next().getValue();
            if (loop.stopped) {
                it.remove();
            } else if (!loop.asked && loop.fadeTarget != 0f) {
                loop.fadeTo(0f, loop.fadeSeconds > 0f ? loop.fadeSeconds : LOOP_FADE_SECONDS);
            }
            loop.asked = false;
        }
        for (Iterator<Voice> it = voices.iterator(); it.hasNext(); ) {
            Voice voice = it.next();
            advance(voice, dt);
            if (voice.stopped) {
                release(voice);
                it.remove();
            }
        }
        if (music != null && music.stopped) music = null;
    }

    private void advance(Voice voice, float dt) {
        if (voice.fade != voice.fadeTarget) {
            float step = voice.fadeSeconds > 0f ? dt / voice.fadeSeconds : 1f;
            voice.fade = voice.fade < voice.fadeTarget ? Math.min(voice.fadeTarget, voice.fade + step)
                    : Math.max(voice.fadeTarget, voice.fade - step);
            if (voice.fade == 0f && voice.fadeTarget == 0f) voice.stopped = true;
        }
        if (voice.stopped) return;
        if (voice.source == 0) {
            voice.waited += dt;
            if (voice.clip.failed || voice.oneShot && voice.waited > STALE_SECONDS) {
                voice.stopped = true;
                dropped++;
            } else if (voice.clip.buffer != 0) {
                start(voice);
            }
            return;
        }
        voice.apply();
        if (alGetSourcei(voice.source, AL_SOURCE_STATE) == AL_STOPPED) voice.stopped = true;
    }

    private void start(Voice voice) {
        int source = takeSource(voice.oneShot);
        if (source == 0) {
            // A loop or a voice waits for a source; a one-shot ages out above.
            return;
        }
        voice.source = source;
        alSourcei(source, AL_BUFFER, voice.clip.buffer);
        alSourcei(source, AL_LOOPING, voice.looping ? 1 : 0);
        alSourcei(source, AL_SOURCE_RELATIVE, voice.relative ? 1 : 0);
        // A relative source sits on the listener, so a UI sound or music plays at full level.
        alSourcef(source, AL_REFERENCE_DISTANCE, voice.relative ? 1f : LISTENER_HEIGHT);
        alSourcef(source, AL_ROLLOFF_FACTOR, voice.relative ? 0f : 1f);
        alSourcef(source, AL_MAX_DISTANCE, MAX_DISTANCE);
        voice.apply();
        alSourcePlay(source);
        started++;
    }

    private int takeSource(boolean oneShot) {
        if (sourcesFree == 0 && sourcesMade < MAX_SOURCES) {
            freeSources.add(alGenSources());
            sourcesMade++;
            sourcesFree++;
        }
        int inUse = sourcesMade - sourcesFree;
        if (sourcesFree == 0 || oneShot && inUse >= MAX_SOURCES - RESERVED_SOURCES) return 0;
        sourcesFree--;
        return freeSources.poll();
    }

    private void release(Voice voice) {
        if (voice.source == 0) return;
        alSourceStop(voice.source);
        alSourcei(voice.source, AL_BUFFER, 0);
        freeSources.add(voice.source);
        sourcesFree++;
        voice.source = 0;
    }

    private void upload(Decoded done) {
        Clip clip = done.clip();
        Pcm pcm = done.pcm();
        if (pcm == null) {
            clip.failed = true;
            return;
        }
        int buffer = alGenBuffers();
        alBufferData(buffer, pcm.channels() == 1 ? AL_FORMAT_MONO16 : AL_FORMAT_STEREO16, pcm.samples(), pcm.rate());
        clip.buffer = buffer;
        uploaded++;
    }

    private Clip clip(Path file) {
        return clips.computeIfAbsent(file, path -> {
            Clip clip = new Clip(path);
            decoders.execute(() -> {
                try {
                    decoded.add(new Decoded(clip, decode(path)));
                } catch (IOException | RuntimeException failure) {
                    System.out.println("[playBattle] could not decode " + path + ": " + failure);
                    decoded.add(new Decoded(clip, null));
                }
            });
            return clip;
        });
    }

    /** A voice for {@code id}, or a stopped handle for an id with no clip: as with the game's, a caller keeps going. */
    private Voice voice(String id, float volume, float pitch, boolean relative, boolean looping, boolean oneShot,
                        float fadeIn, Vector2f location) {
        List<BattleSoundMix.Take> options = id == null ? null : takes.get(id);
        if (options == null || options.isEmpty()) {
            if (id != null) unknown.add(id);
            return null;
        }
        BattleSoundMix.Take take = options.get(random.nextInt(options.size()));
        Voice voice = new Voice(take, clip(take.file()), volume, pitch, relative, looping, oneShot, fadeIn);
        if (location != null) voice.setLocation(location.x, location.y);
        voices.add(voice);
        return voice;
    }

    private SoundAPI handle(Voice voice) {
        if (voice != null) return voice;
        Voice none = new Voice(new BattleSoundMix.Take(Path.of(""), 0f, 1f), new Clip(Path.of("")), 0f, 1f,
                true, false, true, 0f);
        none.stopped = true;
        return none;
    }

    @Override public SoundAPI playUISound(String id, float pitch, float volume) {
        // A UI sound is how the officer and the radio speak, so it waits for its clip however long that takes.
        return handle(voice(id, volume, pitch, true, false, false, 0f, null));
    }

    @Override public SoundAPI playSound(String id, float pitch, float volume, Vector2f loc, Vector2f vel) {
        return handle(voice(id, volume, pitch, false, false, true, 0f, loc));
    }

    @Override public void playLoop(String id, Object playingEntity, float pitch, float volume, Vector2f loc, Vector2f vel) {
        float fadeIn = nextLoopFadeIn >= 0f ? nextLoopFadeIn : LOOP_FADE_SECONDS;
        float fadeOut = nextLoopFadeOut >= 0f ? nextLoopFadeOut : LOOP_FADE_SECONDS;
        nextLoopFadeIn = -1f;
        nextLoopFadeOut = -1f;
        playLoop(id, playingEntity, pitch, volume, loc, vel, fadeIn, fadeOut);
    }

    @Override public void playLoop(String id, Object playingEntity, float pitch, float volume, Vector2f loc, Vector2f vel,
                                   float fadeIn, float fadeOut) {
        hold(new LoopKey(id, playingEntity), id, pitch, volume, loc, false, fadeIn, fadeOut);
    }

    @Override public void playUILoop(String id, float pitch, float volume) {
        hold(new LoopKey(id, null), id, pitch, volume, null, true, LOOP_FADE_SECONDS, LOOP_FADE_SECONDS);
    }

    private void hold(LoopKey key, String id, float pitch, float volume, Vector2f loc, boolean relative,
                      float fadeIn, float fadeOut) {
        Voice loop = loops.get(key);
        if (loop == null || loop.stopped) {
            loop = voice(id, volume, pitch, relative, true, false, fadeIn, loc);
            if (loop == null) return;
            loops.put(key, loop);
        }
        loop.asked = true;
        loop.volume = volume;
        loop.pitch = pitch;
        if (loc != null) loop.setLocation(loc.x, loc.y);
        // Asked for again while dying away: comes back up.
        loop.fadeTarget = 1f;
        // Held on the voice for the day it stops being asked for; the climb reads the same field.
        loop.fadeSeconds = loop.fade < 1f ? fadeIn : fadeOut;
    }

    @Override public void setNextLoopFadeInAndOut(float fadeIn, float fadeOut) {
        nextLoopFadeIn = fadeIn;
        nextLoopFadeOut = fadeOut;
    }

    @Override public void setListenerPosOverrideOneFrame(Vector2f position) {
        if (position != null) listener = new Vector2f(position);
    }

    @Override public Vector2f getListenerPos() {
        return new Vector2f(listener);
    }

    @Override public void playCustomMusic(int fadeOutIfAny, int fadeIn, String musicSetId) {
        playCustomMusic(fadeOutIfAny, fadeIn, musicSetId, false);
    }

    @Override public void playCustomMusic(int fadeOutIfAny, int fadeIn, String musicSetId, boolean looping) {
        if (music != null) {
            if (fadeOutIfAny > 0) music.fadeTo(0f, fadeOutIfAny);
            else music.stopped = true;
            music = null;
        }
        music = voice(musicSetId, 1f, 1f, true, looping, false, fadeIn, null);
        if (music != null) tracks.add(music.clip.file.getFileName().toString());
    }

    @Override public void playMusic(int fadeOutIfAny, int fadeIn, String musicSetId) {
        playCustomMusic(fadeOutIfAny, fadeIn, musicSetId, false);
    }

    /** The file playing, as the game answers: {@code BattleMusicPlaylist} reads a change here as a track ending. */
    @Override public String getCurrentMusicId() {
        return music == null || music.stopped ? null : music.clip.file.getFileName().toString();
    }

    // What a battle never asks of the player, or the sound device gives no route for without its effects extension.
    @Override public void setSuspendDefaultMusicPlayback(boolean suspendMusicPlayback) { }
    @Override public void restartCurrentMusic() { }
    @Override public void pauseCustomMusic() { }
    @Override public void resumeCustomMusic() { }
    @Override public void pauseMusic() { }
    @Override public void applyLowPassFilter(float gain, float gainHF) { }

    @Override public void close() {
        decoders.shutdownNow();
        System.out.println("[playBattle] sound: " + started + " started from " + uploaded + " clips, " + dropped
                + " dropped; music " + tracks + (unknown.isEmpty() ? "" : "; no clip for " + unknown));
        AL.destroy();
    }

    /** Standard JOrbis decoding: every packet of the first logical stream, to native-endian 16-bit samples. */
    private static Pcm decode(Path file) throws IOException {
        SyncState sync = new SyncState();
        StreamState stream = new StreamState();
        Page page = new Page();
        Packet packet = new Packet();
        Info info = new Info();
        Comment comment = new Comment();
        DspState dsp = new DspState();
        Block block = new Block(dsp);
        sync.init();
        info.init();
        comment.init();
        short[] out = new short[1 << 16];
        int written = 0;
        int headers = 0;
        boolean started = false;
        float[][][] pcm = new float[1][][];
        int[] index = null;
        try (InputStream in = Files.newInputStream(file)) {
            boolean eof = false;
            while (!eof) {
                int offset = sync.buffer(4096);
                int read = in.read(sync.data, offset, 4096);
                if (read <= 0) {
                    eof = true;
                    read = 0;
                }
                sync.wrote(read);
                while (sync.pageout(page) == 1) {
                    if (!started) {
                        stream.init(page.serialno());
                        started = true;
                    }
                    if (stream.pagein(page) < 0) continue;
                    while (stream.packetout(packet) == 1) {
                        if (headers < 3) {
                            if (info.synthesis_headerin(comment, packet) < 0) {
                                throw new IOException("not Vorbis: " + file);
                            }
                            if (++headers == 3) {
                                dsp.synthesis_init(info);
                                block.init(dsp);
                                index = new int[info.channels];
                            }
                            continue;
                        }
                        if (block.synthesis(packet) == 0) dsp.synthesis_blockin(block);
                        int samples;
                        while ((samples = dsp.synthesis_pcmout(pcm, index)) > 0) {
                            int needed = written + samples * info.channels;
                            if (needed > out.length) out = Arrays.copyOf(out, Math.max(needed, out.length * 2));
                            for (int s = 0; s < samples; s++) {
                                for (int c = 0; c < info.channels; c++) {
                                    float v = pcm[0][c][index[c] + s];
                                    out[written++] = (short) (Math.max(-1f, Math.min(1f, v)) * 32767f);
                                }
                            }
                            dsp.synthesis_read(samples);
                        }
                    }
                }
            }
        } finally {
            stream.clear();
            sync.clear();
        }
        if (headers < 3) throw new IOException("not Vorbis: " + file);
        ByteBuffer samples = BufferUtils.createByteBuffer(written * 2);
        samples.asShortBuffer().put(out, 0, written);
        samples.limit(written * 2);
        return new Pcm(samples, info.channels, info.rate);
    }
}
