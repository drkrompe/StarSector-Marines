package com.dillon.starsectormarines.cinematic;

import com.dillon.starsectormarines.cinematic.CaptionTrack.Page;
import com.dillon.starsectormarines.cinematic.CinematicSequence.Scheduled;
import com.dillon.starsectormarines.cinematic.CinematicShot.Detail;
import com.dillon.starsectormarines.cinematic.CinematicShot.LockOverlay;
import com.dillon.starsectormarines.cinematic.CinematicShot.Speaker;
import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.ops.MarineOpsContext;
import com.dillon.starsectormarines.ops.MenuTheme;
import com.dillon.starsectormarines.ops.Screen;
import com.dillon.starsectormarines.render2d.GlStateBracket;
import com.dillon.starsectormarines.ui.BitmapFont;
import com.dillon.starsectormarines.ui.Fonts;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SoundAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import org.apache.log4j.Logger;
import org.lwjgl.input.Keyboard;

import java.awt.Color;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.opengl.GL11.GL_LINEAR;
import static org.lwjgl.opengl.GL11.GL_LINE_SMOOTH;
import static org.lwjgl.opengl.GL11.GL_LINE_STRIP;
import static org.lwjgl.opengl.GL11.GL_QUADS;
import static org.lwjgl.opengl.GL11.GL_RGB;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_MAG_FILTER;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_MIN_FILTER;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_WRAP_S;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_WRAP_T;
import static org.lwjgl.opengl.GL11.GL_UNPACK_ALIGNMENT;
import static org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE;
import static org.lwjgl.opengl.GL11.glBegin;
import static org.lwjgl.opengl.GL11.glBindTexture;
import static org.lwjgl.opengl.GL11.glColor4f;
import static org.lwjgl.opengl.GL11.glDeleteTextures;
import static org.lwjgl.opengl.GL11.glDisable;
import static org.lwjgl.opengl.GL11.glEnable;
import static org.lwjgl.opengl.GL11.glEnd;
import static org.lwjgl.opengl.GL11.glGenTextures;
import static org.lwjgl.opengl.GL11.glGetInteger;
import static org.lwjgl.opengl.GL11.glLineWidth;
import static org.lwjgl.opengl.GL11.glPixelStorei;
import static org.lwjgl.opengl.GL11.glTexImage2D;
import static org.lwjgl.opengl.GL11.glTexParameteri;
import static org.lwjgl.opengl.GL11.glTexSubImage2D;
import static org.lwjgl.opengl.GL11.glTexCoord2f;
import static org.lwjgl.opengl.GL11.glVertex2f;
import static org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE;

/**
 * Plays a film full screen: decoded video on one streamed texture, with captions and sensor
 * locks drawn live in the game's fonts. The wall clock is the only master. Audio cues fire against it,
 * the frame shown is the newest decoded one at or before it, and a slow decode drops or holds pictures
 * rather than moving sound. Escape or a click skips. The receipt is the caller's.
 */
public final class CinematicScreen implements Screen {
    private static final Logger LOG = Global.getLogger(CinematicScreen.class);
    private static final double FADE_IN = 0.5;
    private static final double FADE_OUT = 0.7;
    private static final double INPUT_GUARD = 0.25;
    private static final double LOAD_TIMEOUT_SECONDS = 15;
    private static final double PREFETCH_SECONDS = 4;
    /** Decoded frames held ahead of the picture: bounds memory (3.1 MB each). */
    private static final int WINDOW = 48;
    private static final double SRC_W = 1920, SRC_H = 1080;

    private static final Color PLAYER = new Color(170, 222, 255);
    private static final Color HEGEMONY = new Color(245, 150, 30);
    private static final Color PIRATE = new Color(235, 90, 70);
    private static final Color TEXT = new Color(238, 242, 246);

    private final CinematicSequence sequence;
    private final VideoReader videos;
    private final ShotDecoder[] decoders;
    /** Caption width in the caption font's own units: 960 source pixels at the 1.5x the text is drawn. */
    private static final double CAPTION_WIDTH = 640;
    /** Source pixels one transmission takes in a stack of overlapping ones: its tag and two rows. */
    private static final float STACKED_BLOCK = 132;
    /** Whether this film's captions draw in the active language ({@link CinematicSequence.Captions}). */
    private final boolean captionsDrawn;
    private final CaptionTrack[] captionTracks;
    private final LockTrack[][] lockTracks;
    private final List<SoundAPI> heard = new ArrayList<>();
    private final List<Scheduled> loops = new ArrayList<>();
    /** The sound each of {@link #loops} holds, by index; null where it could not play. */
    private final List<SoundAPI> loopSounds = new ArrayList<>();

    private PositionAPI position;
    private Runnable dismiss;
    private FramePump pump;
    private int texture;
    private boolean begun;
    private boolean finished;
    private boolean clockRunning;
    private long wallStart;
    private long clockStart;
    private double clock;
    private int shot = -1;
    private int nextCue;
    private int musicPaused;

    /** Reads a shot's whole mp4 by its mod path. */
    public interface VideoReader {
        byte[] read(String modPath) throws IOException;
    }

    public CinematicScreen(CinematicSequence sequence, VideoReader videos) {
        this.sequence = sequence;
        this.videos = videos;
        decoders = new ShotDecoder[sequence.size()];
        captionTracks = new CaptionTrack[sequence.size()];
        lockTracks = new LockTrack[sequence.size()][];
        captionsDrawn = sequence.captions().drawn(Strings.locale());
        for (int i = 0; i < sequence.size(); i++) {
            var s = sequence.shot(i);
            captionTracks[i] = new CaptionTrack(s.captions(), Strings::get, Fonts.INSIGNIA_LARGE::measureWidth, CAPTION_WIDTH);
            lockTracks[i] = s.locks().stream().map(LockTrack::new).toArray(LockTrack[]::new);
        }
    }

    @Override public void attach(PositionAPI position, MarineOpsContext ctx, Runnable dismissDialog) {
        this.position = position;
        this.dismiss = dismissDialog;
        if (!begun) begin();
    }

    private void begin() {
        begun = true;
        wallStart = System.nanoTime();
        try {
            pump = new FramePump((int) SRC_W, (int) SRC_H);
            openShot(0, WINDOW);
            shot = 0;
            pump.attach(decoders[0]);
            // The film carries its own sound; the menu theme and the campaign music must not play under it.
            MenuTheme.shared().silence();
            Global.getSoundPlayer().setSuspendDefaultMusicPlayback(true);
            musicPaused = 1;
        } catch (RuntimeException e) {
            LOG.warn("Film cannot start", e);
            finish();
        }
    }

    private void openShot(int index, int window) {
        var s = sequence.shot(index);
        int workers = Math.max(2, Math.min(4, Runtime.getRuntime().availableProcessors() / 2));
        decoders[index] = new ShotDecoder(() -> videos.read(s.video()), s.frames(), s.width(), s.height(), window).start(workers);
    }

    // ---------------------------------------------------------------- clock and audio

    @Override public void advance(float dt) {
        if (finished || !begun) return;
        long now = System.nanoTime();
        if (!clockRunning) {
            ShotDecoder first = decoders[0];
            if (first.failure() != null) {
                LOG.warn("Film video cannot be decoded", first.failure());
                finish();
            } else if (first.frame(ShotDecoder.GOP - 1) != null || first.frameCount() < ShotDecoder.GOP && first.frame(first.frameCount() - 1) != null) {
                clockRunning = true;
                clockStart = now;
            } else if ((now - wallStart) / 1e9 > LOAD_TIMEOUT_SECONDS) {
                LOG.warn("Film video did not buffer in time");
                finish();
            }
            return;
        }
        clock = (now - clockStart) / 1e9;
        if (clock >= sequence.duration() || pump.failed()) {
            if (pump.failed()) LOG.warn("Film video failed mid-play", decoders[Math.max(0, shot)].failure());
            finish();
            return;
        }
        int at = sequence.shotAt(clock);
        if (at != shot) switchTo(at);
        prefetch(at);
        pump.request(sequence.frameAt(at, clock));
        runCues();
    }

    private void switchTo(int next) {
        if (decoders[next] == null) openShot(next, WINDOW);
        decoders[next].setWindow(WINDOW);
        pump.attach(decoders[next]);
        for (int i = 0; i < next; i++) if (decoders[i] != null) {
            decoders[i].close();
            decoders[i] = null;
        }
        shot = next;
    }

    private void prefetch(int at) {
        int next = at + 1;
        if (next < sequence.size() && decoders[next] == null
                && clock >= sequence.offset(next) - PREFETCH_SECONDS) openShot(next, ShotDecoder.GOP);
    }

    private void runCues() {
        var cues = sequence.cues();
        while (nextCue < cues.size() && cues.get(nextCue).start() <= clock) {
            Scheduled cue = cues.get(nextCue++);
            if (cue.cue().looped()) {
                loops.add(cue);
                // Never started silent, in case a zero-volume sound is dropped; it ramps from here.
                loopSounds.add(play(cue.cue().sound(), Math.max(0.001f, sequence.volumeAt(cue, clock))));
            } else {
                play(cue.cue().sound(), cue.cue().volume());
            }
        }
        for (int i = loops.size() - 1; i >= 0; i--) {
            Scheduled c = loops.get(i);
            SoundAPI playing = loopSounds.get(i);
            if (clock >= c.until()) {
                stop(playing);
                loops.remove(i);
                loopSounds.remove(i);
                continue;
            }
            // One held sound ramped in place, not playUILoop: that restarts its file whenever a frame
            // misses the call, and a shot switch's hitch did that at every cut. So a held cue's file
            // must cover its span.
            if (playing != null) playing.setVolume(sequence.volumeAt(c, clock));
        }
    }

    private SoundAPI play(String sound, float volume) {
        try {
            SoundAPI playing = Global.getSoundPlayer().playUISound(sound, 1f, volume);
            if (playing != null) heard.add(playing);
            return playing;
        } catch (RuntimeException e) {
            LOG.warn("Film cannot play " + sound, e);
            return null;
        }
    }

    private static void stop(SoundAPI s) {
        if (s == null) return;
        try {
            s.stop();
        } catch (RuntimeException ignored) {
            // The sound already ended; nothing to stop.
        }
    }

    // ---------------------------------------------------------------- input

    @Override public void processInput(List<InputEventAPI> events) {
        for (InputEventAPI event : events) {
            if (event.isConsumed()) continue;
            boolean escape = event.isKeyDownEvent() && event.getEventValue() == Keyboard.KEY_ESCAPE;
            boolean click = event.isMouseDownEvent();
            if (escape || click) {
                if ((System.nanoTime() - wallStart) / 1e9 > INPUT_GUARD) finish();
                event.consume();
            } else if (event.isKeyboardEvent() || event.isMouseEvent()) {
                event.consume();
            }
        }
    }

    private void finish() {
        if (finished) return;
        finished = true;
        for (SoundAPI s : heard) stop(s);
        heard.clear();
        loops.clear();
        loopSounds.clear();
        if (dismiss != null) dismiss.run();
    }

    @Override public void detach() {
        finished = true;
        if (musicPaused > 0) {
            Global.getSoundPlayer().setSuspendDefaultMusicPlayback(false);
            musicPaused = 0;
        }
        if (pump != null) pump.close();
        for (int i = 0; i < decoders.length; i++) if (decoders[i] != null) decoders[i].close();
        if (texture != 0) {
            glDeleteTextures(texture);
            texture = 0;
        }
    }

    // ---------------------------------------------------------------- drawing

    @Override public void render(float alphaMult) {
        if (position == null) return;
        float x = position.getX(), y = position.getY(), w = position.getWidth(), h = position.getHeight();
        float scale = (float) Math.min(w / SRC_W, h / SRC_H);
        float vw = (float) (SRC_W * scale), vh = (float) (SRC_H * scale);
        float vx = x + (w - vw) / 2f, vy = y + (h - vh) / 2f;
        try (GlStateBracket gl = GlStateBracket.textured2D()) {
            glDisable(GL_TEXTURE_2D);
            glColor4f(0f, 0f, 0f, alphaMult);
            quad(x, y, x + w, y + h);
            if (!clockRunning) return;
            upload();
            float fade = (float) Math.min(Math.min(1, clock / FADE_IN), Math.min(1, (sequence.duration() - clock) / FADE_OUT));
            if (texture != 0) {
                glEnable(GL_TEXTURE_2D);
                glBindTexture(GL_TEXTURE_2D, texture);
                glColor4f(fade, fade, fade, alphaMult);
                glBegin(GL_QUADS);
                glTexCoord2f(0, 1); glVertex2f(vx, vy);
                glTexCoord2f(1, 1); glVertex2f(vx + vw, vy);
                glTexCoord2f(1, 0); glVertex2f(vx + vw, vy + vh);
                glTexCoord2f(0, 0); glVertex2f(vx, vy + vh);
                glEnd();
                glDisable(GL_TEXTURE_2D);
            }
            if (shot >= 0 && shot < sequence.size()) {
                Layout layout = new Layout(vx, vy, scale, alphaMult * Math.max(0f, fade));
                double t = clock - sequence.offset(shot);
                drawLocks(layout, t);
                drawCaptions(layout, t);
            }
        }
    }

    /** Source pixels (1920x1080, y down) to screen. */
    private record Layout(float x, float y, float scale, float alpha) {
        float sx(double px) { return (float) (x + px * scale); }
        float sy(double py) { return (float) (y + (SRC_H - py) * scale); }
    }

    private void upload() {
        int slot = pump.take();
        if (slot < 0) return;
        try {
            ByteBuffer pixels = pump.buffer(slot);
            pixels.limit((int) (SRC_W * SRC_H * 3));
            int previousAlignment = glGetInteger(GL_UNPACK_ALIGNMENT);
            glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
            glEnable(GL_TEXTURE_2D);
            if (texture == 0) {
                texture = glGenTextures();
                glBindTexture(GL_TEXTURE_2D, texture);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
                glTexImage2D(GL_TEXTURE_2D, 0, GL_RGB, (int) SRC_W, (int) SRC_H, 0, GL_RGB, GL_UNSIGNED_BYTE, pixels);
            } else {
                glBindTexture(GL_TEXTURE_2D, texture);
                glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, (int) SRC_W, (int) SRC_H, GL_RGB, GL_UNSIGNED_BYTE, pixels);
            }
            glPixelStorei(GL_UNPACK_ALIGNMENT, previousAlignment);
        } finally {
            pump.done(slot);
        }
    }

    private void drawCaptions(Layout l, double t) {
        if (!captionsDrawn) return;
        var state = captionTracks[shot].at(t);
        if (state.empty()) return;
        float s = l.scale();
        float centre = l.sx(SRC_W / 2);
        float top = l.sy(SRC_H - 168);
        if (state.overlapping()) {
            drawTransmissions(l, state, centre, top);
            return;
        }
        // The field: a dark feathered band under the exchange, not a boxed panel.
        field(centre, top - 66 * s, 1020 * s, 140 * s, 36 * s, 0.42f * (float) state.fieldAlpha() * l.alpha());
        if (state.tagSpeaker() != null) drawTag(state.tagSpeaker(), centre, top, s, (float) state.tagAlpha() * l.alpha());
        drawPages(state.pages(), centre, top, s, l.alpha());
    }

    /**
     * Overlapping transmissions as stacked blocks, earliest on top, each with its own tag over its own
     * pages. The latest block sits where a lone transmission would, and the field grows upward over
     * the rest. Every block keeps room for two rows so the stack does not jump as pages turn.
     */
    private void drawTransmissions(Layout l, CaptionTrack.State state, float centre, float top) {
        float s = l.scale();
        float block = STACKED_BLOCK * s;
        int count = state.transmissions().size();
        float raised = (count - 1) * block;
        field(centre, top - 66 * s + raised / 2f, 1020 * s, 140 * s + raised, 36 * s,
                0.42f * (float) state.fieldAlpha() * l.alpha());
        for (int i = 0; i < count; i++) {
            CaptionTrack.Transmission tx = state.transmissions().get(i);
            float blockTop = top + (count - 1 - i) * block;
            drawTag(tx.speaker(), centre, blockTop, s, (float) tx.tagAlpha() * l.alpha());
            drawPages(tx.pages(), centre, blockTop, s, l.alpha());
        }
    }

    /** The speaker's callsign, centred, and after it the hull's role in a smaller, dimmer face when it has one. */
    private void drawTag(String id, float centre, float top, float s, float alpha) {
        Speaker speaker = sequence.shot(shot).speakers().get(id);
        String label = speaker == null ? id.toUpperCase(Strings.locale()) : Strings.get(speaker.labelKey());
        Color color = speaker == null ? PLAYER : color(speaker.color());
        BitmapFont font = Fonts.ORBITRON_12_BOLD;
        float sc = 1.15f * s;
        String role = speaker == null || speaker.roleKey() == null ? null : Strings.get(speaker.roleKey());
        if (role == null || role.isBlank()) {
            text(font, label, centre - font.measureWidth(label) * sc / 2f, top, sc, color, alpha);
            return;
        }
        BitmapFont roleFont = Fonts.ORBITRON_10;
        float roleScale = 1.1f * s;
        float gap = 10 * s;
        float labelW = font.measureWidth(label) * sc, roleW = roleFont.measureWidth(role) * roleScale;
        float x = centre - (labelW + gap + roleW) / 2f;
        text(font, label, x, top, sc, color, alpha);
        // Bottom-aligned with the callsign: the smaller face drops by the difference in line height.
        float drop = font.getLineHeight() * sc - roleFont.getLineHeight() * roleScale;
        text(roleFont, role, x + labelW + gap, top - drop, roleScale, TEXT, 0.6f * alpha);
    }

    private static void drawPages(List<Page> pages, float centre, float top, float s, float alpha) {
        BitmapFont font = Fonts.INSIGNIA_LARGE;
        float sc = 1.5f * s;
        for (Page page : pages) {
            float rowY = top - 30 * s;
            for (String row : page.rows()) {
                text(font, row, centre - font.measureWidth(row) * sc / 2f, rowY, sc, TEXT, (float) page.alpha() * alpha);
                rowY -= 44 * s;
            }
        }
    }

    private void drawLocks(Layout l, double t) {
        for (LockTrack track : lockTracks[shot]) {
            var st = track.at(t);
            if (st == null) continue;
            LockOverlay lock = track.lock();
            float s = l.scale();
            double rr = st.ringRadius();
            double cx = st.centerX(), cy = st.centerY();
            float lineAlpha = (float) st.ringAlpha() * l.alpha();
            glDisable(GL_TEXTURE_2D);
            glEnable(GL_LINE_SMOOTH);
            glLineWidth(Math.max(1f, 1.6f * s));
            for (int mid = 45; mid < 360; mid += 90) {
                glColor4f(PLAYER.getRed() / 255f, PLAYER.getGreen() / 255f, PLAYER.getBlue() / 255f, lineAlpha);
                glBegin(GL_LINE_STRIP);
                for (int i = 0; i <= 24; i++) {
                    double a = Math.toRadians(mid + lock.arcDegrees() * (i / 24.0 - 0.5));
                    glVertex2f(l.sx(cx + rr * Math.cos(a)), l.sy(cy - rr * Math.sin(a)));
                }
                glEnd();
            }
            BitmapFont nameFont = Fonts.ORBITRON_24_BOLD, detailFont = Fonts.ORBITRON_12_BOLD;
            float nameScale = 1.25f * s, detailScale = 1.15f * s;
            double nameW = nameFont.measureWidth(Strings.get(lock.titleKey())) * 1.25, detailW = 0;
            for (Detail d : lock.detail()) detailW += detailFont.measureWidth(Strings.get(d.key())) * 1.15;
            double width = Math.max(nameW, detailW);
            // From the upper-right arc a leader runs up and out to a rule the label sits on: ships
            // cross the lower right of the approach, and the label must not cover them.
            double ax = cx + rr * Math.cos(Math.toRadians(45)), ay = cy - rr * Math.sin(Math.toRadians(45));
            double ex = ax + lock.leader(), ey = ay - lock.leader();
            if (st.run() > 0) {
                double k = Math.min(1, st.run() * 2);
                glColor4f(PLAYER.getRed() / 255f, PLAYER.getGreen() / 255f, PLAYER.getBlue() / 255f, lineAlpha);
                glBegin(GL_LINE_STRIP);
                glVertex2f(l.sx(ax + 4), l.sy(ay - 4));
                glVertex2f(l.sx(ax + 4 + (ex - ax - 4) * k), l.sy(ay - 4 + (ey - ay + 4) * k));
                if (st.run() > 0.5) glVertex2f(l.sx(ex + (width + 6) * (st.run() - 0.5) * 2), l.sy(ey));
                glEnd();
            }
            glDisable(GL_LINE_SMOOTH);
            glLineWidth(1f);
            float textAlpha = (float) st.textAlpha() * l.alpha();
            if (textAlpha <= 0) continue;
            double tx = ex + 2 - st.slide();
            double nameH = nameFont.getLineHeight() * 1.25 + 2;
            double top = ey - 4 - nameH - detailFont.getLineHeight() * 1.15;
            text(nameFont, Strings.get(lock.titleKey()), l.sx(tx), l.sy(top), nameScale, PLAYER, textAlpha);
            float detailX = l.sx(tx);
            float detailY = l.sy(top + nameH);
            for (Detail d : lock.detail()) {
                text(detailFont, Strings.get(d.key()), detailX, detailY, detailScale, color(d.color()), 0.9f * textAlpha);
                detailX += detailFont.measureWidth(Strings.get(d.key())) * detailScale;
            }
        }
    }

    /** A drop shadow and then the text, from its top-left corner. */
    private static void text(BitmapFont font, String s, float x, float y, float scale, Color color, float alpha) {
        if (alpha <= 0f) return;
        font.drawStringScaled(s, x + 1.5f * scale, y - 1.5f * scale, scale, scale, Color.BLACK, alpha * 0.8f);
        font.drawStringScaled(s, x, y, scale, scale, color, alpha);
    }

    private static Color color(String name) {
        return switch (name) {
            case "hegemony" -> HEGEMONY;
            case "pirate", "pirates" -> PIRATE;
            default -> PLAYER;
        };
    }

    /** A dark rectangle whose edges fade out over {@code feather}. */
    private static void field(float cx, float cy, float w, float h, float feather, float alpha) {
        if (alpha <= 0f) return;
        glDisable(GL_TEXTURE_2D);
        float[] xs = {cx - w / 2 - feather, cx - w / 2, cx + w / 2, cx + w / 2 + feather};
        float[] ys = {cy - h / 2 - feather, cy - h / 2, cy + h / 2, cy + h / 2 + feather};
        glBegin(GL_QUADS);
        for (int i = 0; i < 3; i++)
            for (int j = 0; j < 3; j++) {
                vertex(xs[i], ys[j], alpha, i, j);
                vertex(xs[i + 1], ys[j], alpha, i + 1, j);
                vertex(xs[i + 1], ys[j + 1], alpha, i + 1, j + 1);
                vertex(xs[i], ys[j + 1], alpha, i, j + 1);
            }
        glEnd();
    }

    private static void vertex(float x, float y, float alpha, int i, int j) {
        boolean inner = (i == 1 || i == 2) && (j == 1 || j == 2);
        glColor4f(0.01f, 0.015f, 0.025f, inner ? alpha : 0f);
        glVertex2f(x, y);
    }

    private static void quad(float x0, float y0, float x1, float y1) {
        glBegin(GL_QUADS);
        glVertex2f(x0, y0);
        glVertex2f(x1, y0);
        glVertex2f(x1, y1);
        glVertex2f(x0, y1);
        glEnd();
    }
}
