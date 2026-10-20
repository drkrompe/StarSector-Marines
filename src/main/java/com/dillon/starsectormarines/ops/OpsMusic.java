package com.dillon.starsectormarines.ops;

/**
 * The marine ops dialog's music: the {@link MenuTheme} for every screen except the ones that
 * own their own music ({@link ScreenId#ownsMusic()}, the battle). Moving between ordinary
 * screens never touches the track, so it neither restarts nor lets the campaign's default
 * music in; closing the dialog releases the dialog's hold, which stops the track unless another
 * holder (the port the yard pages left behind) still wants it.
 *
 * <p>Which screens share the track is a property of the screen rather than a list kept here,
 * because a list drifts: Ship View and the after-battle Results and Loot were once missing
 * from it, and each crossing faded the track out, let the campaign music play, and restarted.
 */
final class OpsMusic {

    private final MenuTheme theme;
    private boolean holding;

    OpsMusic(MenuTheme theme) {
        this.theme = theme;
    }

    /** A screen has attached. Called after it attached, so a screen with its own music has started it. */
    void entered(ScreenId screen) {
        if (screen.ownsMusic()) {
            // That screen replaced the track; the next ordinary screen starts it again.
            theme.displaced();
            return;
        }
        holding = true;
        theme.hold(this);
    }

    /** The dialog closed: let go of the theme. */
    void closed() {
        if (!holding) return;
        holding = false;
        theme.release(this);
    }
}
