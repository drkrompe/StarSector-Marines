package com.dillon.starsectormarines.ops;

/**
 * The marine ops dialog's music: one looping intel track for every screen except the ones
 * that own their own music ({@link ScreenId#ownsMusic()}, the battle). Moving between ordinary
 * screens never touches the track, so it neither restarts nor lets the campaign's default
 * music in; only closing the dialog stops it.
 *
 * <p>Which screens share the track is a property of the screen rather than a list kept here,
 * because a list drifts: Ship View and the after-battle Results and Loot were once missing
 * from it, and each crossing faded the track out, let the campaign music play, and restarted.
 */
final class OpsMusic {

    /** Looping intel-screen track declared in mod/data/config/sounds.json. */
    static final String INTEL_TRACK = "marines_intel_music";
    /** Crossfade duration (seconds; the sound player takes whole numbers). */
    static final int FADE_SECS = 2;

    /** The game's music controls, narrowed to what this needs, so the rules test headless. */
    interface Player {
        void suspendDefaultMusic(boolean suspended);

        void play(String track, int fadeOutSecs, int fadeInSecs, boolean loop);

        void stop(int fadeOutSecs);
    }

    private final Player player;
    private boolean intelPlaying;

    OpsMusic(Player player) {
        this.player = player;
    }

    /** A screen has attached. Called after it attached, so a screen with its own music has started it. */
    void entered(ScreenId screen) {
        if (screen.ownsMusic()) {
            // That screen replaced the track; the next ordinary screen starts it again.
            intelPlaying = false;
            return;
        }
        if (intelPlaying) return;
        intelPlaying = true;
        player.suspendDefaultMusic(true);
        player.play(INTEL_TRACK, FADE_SECS, FADE_SECS, true);
    }

    /** The dialog closed: fade the track out and give the campaign its music back. */
    void closed() {
        if (!intelPlaying) return;
        intelPlaying = false;
        player.stop(FADE_SECS);
        player.suspendDefaultMusic(false);
    }

    boolean intelPlaying() {
        return intelPlaying;
    }
}
