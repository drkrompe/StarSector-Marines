package com.dillon.starsectormarines.battle.ruins;

/**
 * The vault door's two keys ({@code ruins-works.md}, Vault access): once its door is powered, the company turns them
 * by holding the records console and the vault keypad at the same moment for {@link #TURN_SECONDS}. Letting either go
 * starts the count again; once turned they stay turned.
 */
public final class VaultKeys {

    /** How long both keys are held together to turn them. UNTUNED. */
    public static final float TURN_SECONDS = 3f;

    private float held;
    private boolean turned;

    /**
     * One step of {@code dt} seconds: counts while the door is powered and both keys are held, and returns whether the
     * keys are turned.
     */
    public boolean tick(boolean powered, boolean first, boolean second, float dt) {
        if (turned) return true;
        if (powered && first && second) {
            held += dt;
            if (held >= TURN_SECONDS - 1e-4f) turned = true;
        } else {
            held = 0f;
        }
        return turned;
    }

    public boolean turned() { return turned; }

    /** How far the turn has got, 0 to 1. */
    public float progress() { return turned ? 1f : Math.min(1f, held / TURN_SECONDS); }
}
