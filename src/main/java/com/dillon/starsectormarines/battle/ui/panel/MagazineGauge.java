package com.dillon.starsectormarines.battle.ui.panel;

import com.dillon.starsectormarines.battle.infantry.EquipmentGrade;
import com.dillon.starsectormarines.battle.weapon.WeaponDef;

import java.util.Locale;

/**
 * What a weapon's counted load looks like, round by round, for the strip under its weapon row: a
 * Marine's magazine, a mech's missile rack or a vehicle's belt.
 *
 * <p>Rounds are counted from the first to leave the gun. A belt is drawn in blocks of one trigger pull's
 * burst and a rack in salvos, which is what turns fifty rounds back into something read at a glance:
 * six blocks and a bit. A belt or rack too long for the row folds, one icon to a burst or salvo (to as
 * many whole bursts as it takes to fit, for a long belt), and an icon part spent reads as
 * {@link Round#PART}. While reloading, every round reads as spent: not one
 * can fire until the reload ends, and the row's countdown carries its clock. The rounds a held rack trigger has fired
 * so far read as {@link Round#HELD}, lit, so the salvos building into one spike can be counted as they go.
 *
 * @param loaded rounds left in the load
 * @param group rounds per block, one for a weapon that fires single rounds
 * @param reloadProgress how far the reload has come, from 0 to 1; negative when not reloading
 * @param held rounds the current hold of a rack's trigger has fired, drawn lit next to the live ones
 */
public record MagazineGauge(Kind kind, int capacity, int loaded, int group, float reloadProgress, int held)
        implements AmmoStrip {

    /** How a round is drawn: a rifle cartridge, a shotgun shell, a belt link, or a missile. */
    public enum Kind { RIFLE, SHELL, LINK, MISSILE }

    public enum Round { LIVE, SPENT, PART, HELD }

    public MagazineGauge {
        capacity = Math.max(0, capacity);
        loaded = Math.max(0, Math.min(capacity, loaded));
        group = Math.max(1, group);
        held = Math.max(0, Math.min(capacity - loaded, held));
    }

    public MagazineGauge(Kind kind, int capacity, int loaded, int group, float reloadProgress) {
        this(kind, capacity, loaded, group, reloadProgress, 0);
    }

    /** The same load with {@code rounds} fired by the trigger's current hold. */
    public MagazineGauge withHeld(int rounds) {
        return new MagazineGauge(kind, capacity, loaded, group, reloadProgress, rounds);
    }

    /**
     * The gauge for a magazine-fed primary, or {@code null} for one without a magazine. A weapon that
     * throws several projectiles a pull shows shells; one that bursts shows a belt in blocks of its burst.
     */
    public static MagazineGauge of(WeaponDef weapon, EquipmentGrade grade, int loaded,
                                   float reloadRemaining, float reloadDuration) {
        if (weapon == null || weapon.magazine() == null) return null;
        return of(weapon.projectilesPerShot, weapon.burstCount, weapon.magazine().size(grade), loaded,
                reloadRemaining, reloadDuration);
    }

    /** The same gauge from a weapon's packet and burst sizes, for callers that hold only its status. */
    public static MagazineGauge of(int projectilesPerShot, int burstCount, int capacity, int loaded,
                                   float reloadRemaining, float reloadDuration) {
        return of(Ordnance.BALLISTIC, projectilesPerShot, burstCount, capacity, loaded,
                reloadRemaining, reloadDuration);
    }

    /** A missile load is a rack in salvos of {@code perTrigger}; anything else is rounds as above. */
    public static MagazineGauge of(Ordnance ordnance, int projectilesPerShot, int perTrigger, int capacity,
                                   int loaded, float reloadRemaining, float reloadDuration) {
        Kind kind = ordnance == Ordnance.MISSILE ? Kind.MISSILE
                : projectilesPerShot > 1 ? Kind.SHELL : perTrigger > 1 ? Kind.LINK : Kind.RIFLE;
        float progress = reloadRemaining > 0f && reloadDuration > 0f
                ? Math.max(0f, Math.min(1f, 1f - reloadRemaining / reloadDuration)) : -1f;
        int group = kind == Kind.LINK || kind == Kind.MISSILE ? perTrigger : 1;
        return new MagazineGauge(kind, capacity, loaded, group, progress);
    }

    public boolean reloading() { return reloadProgress >= 0f; }

    public Round round(int index) {
        if (reloading()) return Round.SPENT;
        if (index < loaded) return Round.LIVE;
        return index < loaded + held ? Round.HELD : Round.SPENT;
    }

    /** Whether a block ends after this round, so the next one is set apart. */
    public boolean groupEnd(int index) {
        return group > 1 && (index + 1) % group == 0 && index + 1 < capacity;
    }

    /** A quarter of the load or less, and at least the last round: time to think about reloading. */
    public boolean low() {
        return !reloading() && loaded <= Math.max(1, capacity / 4);
    }

    /** Whether the strip draws one icon to a burst or salvo, because round by round it would not fit. */
    public boolean folded() { return fold() > 0; }

    /**
     * Rounds per icon when folded, a whole number of bursts or salvos: the fewest that fit the row.
     * Zero when the strip is drawn round by round.
     */
    public int fold() {
        if (group <= 1 || (kind != Kind.LINK && kind != Kind.MISSILE)) return 0;
        int blocks = (capacity + group - 1) / group;
        if (capacity * roundWidth(kind) + (capacity - blocks) * roundGap(kind)
                + (blocks - 1) * GROUP_GAP <= MAX_WIDTH) return 0;
        int pitch = foldedWidth() + 3;
        int fitting = Math.max(1, (MAX_WIDTH + 3) / pitch);
        int bursts = Math.max(1, (blocks + fitting - 1) / fitting);
        return bursts * group;
    }

    private int foldedWidth() { return kind == Kind.MISSILE ? 12 : 9; }

    @Override public int icons() {
        int fold = fold();
        return fold > 0 ? (capacity + fold - 1) / fold : capacity;
    }

    @Override public String iconKind() {
        if (folded()) return kind == Kind.MISSILE ? "salvo" : "belt";
        return kind.name().toLowerCase(Locale.ROOT);
    }

    @Override public String iconState(int index) {
        return (folded() ? block(index) : round(index)).name().toLowerCase(Locale.ROOT);
    }

    /** A folded icon: its burst whole, gone, or part spent. */
    private Round block(int index) {
        int fold = fold();
        int first = index * fold;
        int last = Math.min(capacity, first + fold);
        if (reloading()) return Round.SPENT;
        if (loaded >= last) return Round.LIVE;
        if (loaded > first) return Round.PART;
        // A salvo the hold has fired, or begun to, lights up.
        return loaded + held > first ? Round.HELD : Round.SPENT;
    }

    @Override public boolean iconGroupEnd(int index) { return !folded() && groupEnd(index); }

    @Override public int iconWidth() {
        return folded() ? foldedWidth() : roundWidth(kind);
    }

    @Override public int iconGap() { return folded() ? 3 : roundGap(kind); }

    private static int roundWidth(Kind kind) {
        return switch (kind) { case RIFLE -> 10; case SHELL -> 11; case LINK -> 3; case MISSILE -> 6; };
    }

    private static int roundGap(Kind kind) {
        return switch (kind) { case RIFLE, SHELL -> 4; case LINK -> 1; case MISSILE -> 2; };
    }
}
