package com.dillon.starsectormarines.battle.ruins;

/**
 * The vault code ({@code ruins-works.md}, Vault access, {@link VaultAccess#RECORDS_CODE}): once the records are read
 * the company knows it, and with the door powered it enters it by holding the vault keypad for
 * {@link #ENTER_SECONDS}. Stepping off starts the entry again; once entered it stays entered.
 */
public final class VaultCode {

    /** How long the keypad is held to enter the code. UNTUNED. */
    public static final float ENTER_SECONDS = 3f;

    private float held;
    private boolean entered;

    /** One step of {@code dt} seconds; returns whether the code is entered. */
    public boolean tick(boolean powered, boolean known, boolean atKeypad, float dt) {
        if (entered) return true;
        if (powered && known && atKeypad) {
            held += dt;
            if (held >= ENTER_SECONDS - 1e-4f) entered = true;
        } else {
            held = 0f;
        }
        return entered;
    }

    public boolean entered() { return entered; }
}
