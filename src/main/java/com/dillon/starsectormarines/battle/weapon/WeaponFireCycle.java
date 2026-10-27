package com.dillon.starsectormarines.battle.weapon;

import com.dillon.starsectormarines.battle.infantry.EquipmentGrade;
import com.dillon.starsectormarines.battle.infantry.InfantryCombatStats;
import com.dillon.starsectormarines.battle.infantry.SoldierProfile;

/**
 * A marine primary held on the trigger without pause, as a clock: when each round leaves and what the
 * magazine reads at any moment ({@code moddable-weapons-nouns.md}, catalog preview).
 *
 * <p>The same rules {@code CombatService} applies to a live marine, without a world: a pull releases
 * {@code burstCount} rounds {@code burstSpacing} apart, the next pull waits out the cycle, every
 * release spends one round of the load, and the round that empties it starts the reload, dropping
 * whatever was left of the burst. A full reload refills the load and the next pull follows at once.
 * One loop is one load and its reload; a primary without a magazine loops on one pull.
 *
 * <p>Closed-form rather than stepped, so a preview asks for any moment without keeping state and two
 * frames at the same time read the same.
 */
public final class WeaponFireCycle {

    private final float pullInterval;
    private final float burstSpacing;
    private final int burstCount;
    private final int capacity;
    private final float reloadSeconds;
    private final int releases;
    private final float lastRelease;
    private final float period;

    /**
     * @param cycleSeconds  seconds between trigger pulls
     * @param burstCount    rounds one pull releases
     * @param burstSpacing  seconds between the rounds of one pull
     * @param capacity      rounds one load holds; zero for a primary without a magazine
     * @param reloadSeconds seconds a reload takes; ignored without a magazine
     */
    public WeaponFireCycle(float cycleSeconds, int burstCount, float burstSpacing,
                           int capacity, float reloadSeconds) {
        this.burstCount = Math.max(1, burstCount);
        this.burstSpacing = Math.max(0f, burstSpacing);
        // A pull cannot come before its own burst has left the gun.
        this.pullInterval = Math.max(Math.max(0.01f, cycleSeconds),
                (this.burstCount - 1) * this.burstSpacing);
        this.capacity = Math.max(0, capacity);
        this.reloadSeconds = this.capacity > 0 ? Math.max(0.01f, reloadSeconds) : 0f;
        this.releases = this.capacity > 0 ? this.capacity : this.burstCount;
        this.lastRelease = releaseTime(releases - 1);
        float lastPull = ((releases - 1) / this.burstCount) * pullInterval;
        this.period = this.capacity > 0
                ? Math.max(lastRelease + this.reloadSeconds, lastPull + pullInterval)
                : pullInterval;
    }

    /** The cycle a catalog entry shows: the weapon at one grade, in a regular marine's hands. */
    public static WeaponFireCycle of(WeaponDef weapon, EquipmentGrade grade) {
        if (weapon == null) throw new IllegalArgumentException("a fire cycle needs a weapon");
        EquipmentGrade issued = grade != null ? grade : EquipmentGrade.SERVICE;
        SoldierProfile shooter = SoldierProfile.REGULAR;
        MagazineDef magazine = weapon.magazine();
        return new WeaponFireCycle(
                InfantryCombatStats.cooldown(weapon, issued, shooter),
                weapon.burstCount, weapon.burstSpacing,
                magazine != null ? magazine.size(issued) : 0,
                InfantryCombatStats.reload(weapon, issued, shooter));
    }

    /** Seconds one loop takes: a load and its reload, or one pull without a magazine. */
    public float period() {
        return period;
    }

    /** Rounds released in one loop. */
    public int releasesPerLoop() {
        return releases;
    }

    /** Seconds into a loop at which its {@code index}th round leaves. */
    public float releaseTime(int index) {
        return (index / burstCount) * pullInterval + (index % burstCount) * burstSpacing;
    }

    public boolean magazineFed() {
        return capacity > 0;
    }

    public int capacity() {
        return capacity;
    }

    public float reloadSeconds() {
        return reloadSeconds;
    }

    public float cycleSeconds() {
        return pullInterval;
    }

    /** What the gun reads {@code seconds} after the trigger was first pulled. */
    public State at(float seconds) {
        float t = loopTime(seconds);
        int fired = 0;
        while (fired < releases && releaseTime(fired) <= t) fired++;
        float sinceRelease = fired > 0
                ? t - releaseTime(fired - 1)
                : t + period - lastRelease;
        float sincePull = fired > 0
                ? t - releaseTime(((fired - 1) / burstCount) * burstCount)
                : t + period - releaseTime(((releases - 1) / burstCount) * burstCount);
        int burstRemaining = fired > 0
                ? Math.min(((fired - 1) / burstCount + 1) * burstCount, releases) - fired
                : 0;
        float burstTimer = burstRemaining > 0 ? releaseTime(fired) - t : 0f;
        if (!magazineFed()) {
            return new State(0, 0f, sinceRelease, sincePull, cycleFraction(sincePull), false,
                    burstRemaining, burstTimer);
        }
        boolean reloading = fired >= releases && t < lastRelease + reloadSeconds;
        if (reloading) {
            float left = lastRelease + reloadSeconds - t;
            return new State(0, left, sinceRelease, sincePull, 1f - left / reloadSeconds, true, 0, 0f);
        }
        int loaded = fired >= releases ? capacity : capacity - fired;
        return new State(loaded, 0f, sinceRelease, sincePull, cycleFraction(sincePull), false,
                burstRemaining, burstTimer);
    }

    private float cycleFraction(float sincePull) {
        return Math.max(0f, Math.min(1f, sincePull / pullInterval));
    }

    private float loopTime(float seconds) {
        float t = seconds % period;
        return t < 0f ? t + period : t;
    }

    /**
     * The gun at one moment.
     *
     * @param loaded          rounds left in the load; zero without a magazine
     * @param reloadRemaining seconds the reload still needs; zero when not reloading
     * @param sinceRelease    seconds since the last round left
     * @param sincePull       seconds since the trigger was last pulled
     * @param cycleFraction   how far the cycle or the reload has come, 0 to 1
     * @param reloading       whether the load is being changed
     * @param burstRemaining  rounds of the current pull still to leave
     * @param burstTimer      seconds until the next of them leaves; zero with none left
     */
    public record State(int loaded, float reloadRemaining, float sinceRelease,
                        float sincePull, float cycleFraction, boolean reloading,
                        int burstRemaining, float burstTimer) {

        /** Seconds left of the cycle before the trigger can be pulled again. */
        public float cooldownRemaining(WeaponFireCycle cycle) {
            return Math.max(0f, cycle.cycleSeconds() - sincePull);
        }
    }
}
