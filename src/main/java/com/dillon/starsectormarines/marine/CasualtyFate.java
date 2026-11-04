package com.dillon.starsectormarines.marine;

import java.util.Random;
import java.util.SplittableRandom;

/**
 * The dice on one marine the battle put down: {@link MarineSoldierStatus#WIA wounded}, who
 * comes home fit for duty, or {@link MarineSoldierStatus#KIA killed}, who leaves an empty
 * billet to replace. There is no wounded list and no third outcome. Deterministic for the same
 * seed, so a settlement replayed from the same inputs writes the same service records.
 *
 * <p>A lost fight leaves fewer recoverable bodies than a won one, which is the whole reason
 * the roll reads the result at all. {@link #STRAGGLER_LOSS} is the same die for a marine still
 * on their feet but cut off from the landing pads when the company pulls out.
 */
public final class CasualtyFate {

    /** A casualty's chance of dying on a field the company won. */
    static final float VICTORY_LOSS = 0.40f;
    /** A casualty's chance of dying on a field the company lost. */
    static final float DEFEAT_LOSS = 0.70f;
    /** A marine cut off at a withdrawal: up and moving, so better placed than one down on lost ground. */
    static final float STRAGGLER_LOSS = VICTORY_LOSS;

    private static final long SAVE_SALT = 0x5349434b42415951L;

    private CasualtyFate() {}

    public static MarineSoldierStatus roll(long seed, boolean victory) {
        return roll(seed, victory, 0f);
    }

    /** The casualty's roll with {@code saved} of the would-be dead pulled through. */
    public static MarineSoldierStatus roll(long seed, boolean victory, float saved) {
        return rollAt(seed, victory ? VICTORY_LOSS : DEFEAT_LOSS, saved);
    }

    /** The roll for a marine left away from every pad at a withdrawal. */
    public static MarineSoldierStatus rollStraggler(long seed, float saved) {
        return rollAt(seed, STRAGGLER_LOSS, saved);
    }

    /**
     * {@code saved} (a share in {@code [0, 1]}) is what the company home's sick bay brings to the
     * roll. Only the would-be dead move, and a higher share saves everyone a lower one did. The save is its own draw, mixed
     * from the seed, because the fate roll is not evenly spread across neighbouring marines and
     * a share of it would not be the share it says.
     */
    private static MarineSoldierStatus rollAt(long seed, float loss, float saved) {
        if (new Random(seed).nextFloat() >= loss) return MarineSoldierStatus.WIA;
        return saved > 0f && new SplittableRandom(seed ^ SAVE_SALT).nextDouble() < saved
                ? MarineSoldierStatus.WIA : MarineSoldierStatus.KIA;
    }

    /** Stable per-marine seed: the event that put them down, mixed with who they were. */
    public static long seed(long eventKey, String soldierId) {
        return (eventKey << 32) ^ (soldierId != null ? soldierId.hashCode() : 0);
    }
}
