package com.dillon.starsectormarines.battle.deployable;

/**
 * The clock of one mortar section's set-up: a fixed window that an engaged crew may push back, within a hard cap.
 *
 * <p>The window opens when the section is activated, not when the tube first fires, because the squad is leashed from the
 * moment it halts. It is {@code windowSeconds} long. Each tick the crew is <em>engaged</em> (the tube has had something to
 * shoot at lately) the deadline is pushed out to at least {@code extensionSeconds} from now, never beyond
 * {@code capSeconds} from the start. A crew that is not engaged does not extend it, so a tube with nothing to shoot is
 * packed up when the window runs out and the squad reforms.
 *
 * <p>Pure arithmetic over seconds, so the state machine is asked directly rather than through a battle.
 */
public final class MortarSectionWindow {

    private final float windowSeconds;
    private final float extensionSeconds;
    private final float capSeconds;
    private float elapsed;
    private float deadline;

    /**
     * @param windowSeconds    seconds from activation after which an unengaged crew packs up
     * @param extensionSeconds how far ahead an engaged crew holds the deadline; zero disables extension
     * @param capSeconds       the longest a section may hold, from activation; never less than the window
     */
    public MortarSectionWindow(float windowSeconds, float extensionSeconds, float capSeconds) {
        this.windowSeconds = Math.max(0f, windowSeconds);
        this.extensionSeconds = Math.max(0f, extensionSeconds);
        this.capSeconds = Math.max(this.windowSeconds, capSeconds);
        this.deadline = this.windowSeconds;
    }

    /** Ages the window by {@code dt}; {@code engaged} pushes the deadline out, within the cap. */
    public void advance(float dt, boolean engaged) {
        elapsed += dt;
        if (engaged && extensionSeconds > 0f) {
            deadline = Math.max(deadline, Math.min(elapsed + extensionSeconds, capSeconds));
        }
    }

    /** Whether the window has run out: the crew should pack up and the squad reform. */
    public boolean expired() {
        return elapsed >= deadline;
    }

    /** Seconds since activation. */
    public float elapsed() {
        return elapsed;
    }

    /** Seconds from activation at which an unengaged crew packs up, as extended so far. */
    public float deadline() {
        return deadline;
    }

    /** Whether the deadline has been pushed to the cap and can go no further. */
    public boolean atCap() {
        return deadline >= capSeconds;
    }
}
