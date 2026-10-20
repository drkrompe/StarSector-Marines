package com.dillon.starsectormarines.ops;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * The Marines menu theme: one looping intel track shared by every surface that plays it — the
 * Marine operations dialog and the opening storybook (prologue and yard pages). Surfaces
 * {@linkplain #hold hold} it while they are shown and {@linkplain #release release} it when they
 * close; the track starts when the first holder arrives and fades out, giving the campaign its
 * default music back, only when the last one leaves. So a hand-over between two holders neither
 * restarts the track nor lets vanilla's music in.
 *
 * <p>A score of its own (the battle, the Jangala arrival) {@linkplain #displaced displaces} or
 * {@linkplain #silence silences} the track without releasing anybody; the next hold starts it
 * again.
 *
 * <p>One instance per process, because the sound player is one per process. That is also why a
 * game load {@linkplain #reset resets} it: a dialog torn down without its dismissal callback must
 * not leave the next campaign's music suspended.
 */
public final class MenuTheme {

    /** Looping intel-screen track declared in mod/data/config/sounds.json. */
    public static final String TRACK = "marines_intel_music";
    /** Crossfade duration (seconds; the sound player takes whole numbers). */
    static final int FADE_SECS = 2;

    /** The game's music controls, narrowed to what this needs, so the rules test headless. */
    interface Player {
        void suspendDefaultMusic(boolean suspended);

        void play(String track, int fadeOutSecs, int fadeInSecs, boolean loop);

        void stop(int fadeOutSecs);
    }

    private static MenuTheme shared;

    private final Player player;
    private final Set<Object> holders = Collections.newSetFromMap(new IdentityHashMap<>());
    private boolean playing;

    MenuTheme(Player player) {
        this.player = player;
    }

    /** The process's theme, driving the game's sound player. */
    public static synchronized MenuTheme shared() {
        if (shared == null) shared = new MenuTheme(new Player() {
            @Override public void suspendDefaultMusic(boolean suspended) {
                Global.getSoundPlayer().setSuspendDefaultMusicPlayback(suspended);
            }

            @Override public void play(String track, int fadeOutSecs, int fadeInSecs, boolean loop) {
                Global.getSoundPlayer().playCustomMusic(fadeOutSecs, fadeInSecs, track, loop);
            }

            @Override public void stop(int fadeOutSecs) {
                Global.getSoundPlayer().playCustomMusic(fadeOutSecs, 0, null);
            }
        });
        return shared;
    }

    /** {@code holder} is showing; start the track unless it is already playing. */
    public void hold(Object holder) {
        holders.add(holder);
        if (playing) return;
        playing = true;
        player.suspendDefaultMusic(true);
        player.play(TRACK, FADE_SECS, FADE_SECS, true);
    }

    /** {@code holder} has closed; the last one out fades the track and restores default music. */
    public void release(Object holder) {
        if (!holders.remove(holder) || !holders.isEmpty() || !playing) return;
        playing = false;
        player.stop(FADE_SECS);
        player.suspendDefaultMusic(false);
    }

    /**
     * Another score has already replaced the track (the battle starts its own over it). Nothing
     * to stop; the next {@link #hold} starts the theme again.
     */
    public void displaced() {
        playing = false;
    }

    /**
     * A score that is not custom music (the arrival's timed UI sounds) is about to play: fade
     * the track out so the two never overlap. Suspension is left to that score, which owns it
     * until it ends; the next {@link #hold} starts the theme again.
     */
    public void silence() {
        if (!playing) return;
        playing = false;
        player.stop(FADE_SECS);
    }

    /** Forget every holder and give the campaign its default music back, whatever was playing. */
    public void reset() {
        holders.clear();
        if (playing) player.stop(0);
        playing = false;
        player.suspendDefaultMusic(false);
    }

    public boolean playing() {
        return playing;
    }

    /**
     * Keep the theme playing while {@code dialog} is the current interaction dialog, so the yard
     * pages hand over to the port menu and on into Marine operations without a restart. Undocking
     * (or any other dialog taking its place) releases it.
     */
    public void holdWhileOpen(InteractionDialogAPI dialog) {
        var sector = Global.getSector();
        if (sector == null || dialog == null) return;
        DialogLease lease = new DialogLease(this, dialog);
        hold(lease);
        sector.addTransientScript(lease);
    }

    /** Releases its hold once its interaction dialog is no longer the current one. */
    private static final class DialogLease implements EveryFrameScript {
        private final MenuTheme theme;
        private final InteractionDialogAPI dialog;
        private boolean done;

        DialogLease(MenuTheme theme, InteractionDialogAPI dialog) {
            this.theme = theme;
            this.dialog = dialog;
        }

        @Override public boolean isDone() { return done; }
        @Override public boolean runWhilePaused() { return true; }

        @Override public void advance(float amount) {
            if (done) return;
            var sector = Global.getSector();
            var ui = sector == null ? null : sector.getCampaignUI();
            if (ui != null && ui.getCurrentInteractionDialog() == dialog) return;
            done = true;
            theme.release(this);
        }
    }
}
