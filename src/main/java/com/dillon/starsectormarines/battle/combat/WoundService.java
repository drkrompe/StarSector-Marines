package com.dillon.starsectormarines.battle.combat;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;

/**
 * Data owner for the limb wounds a person carries this battle ({@code body-part-cover.md}, Part
 * consequences). A legs or arms hit that takes structure leaves one wound on that limb; wounds
 * stack and last the battle unless a medic dresses them. What a wound does is not stored here:
 * {@link WoundModifier} reads the count, so the slowed walk and the shaken aim are named unit
 * modifiers rather than branches at the read site.
 *
 * <p>Owned by the roster. Most units are never wounded, so the counts are sparse maps keyed by
 * entity id rather than a column on every row; an id is never reused, so a dead man's entry is
 * simply never read again.
 *
 * <p><b>Threading.</b> Written only from {@code DamageResolver.resolve}, which is serialized by
 * {@code DamageService}, and from the serial medic pass; the parallel unit dispatch only reads,
 * through the modifiers. Concurrent reads of an unchanging map are safe.
 */
public final class WoundService {

    /** What one wound multiplies its stat by: a leg slows the man, an arm costs his aim. */
    public static final float WOUND_MULTIPLIER = 0.85f;

    /** No pile of wounds takes a stat below this fraction of its base: a wounded man still walks and still shoots. */
    public static final float WOUND_FLOOR = 0.60f;

    private final Long2IntOpenHashMap legWounds = new Long2IntOpenHashMap();
    private final Long2IntOpenHashMap armWounds = new Long2IntOpenHashMap();

    /** Records one wound on {@code part} of {@code unit}. A part that does not {@linkplain BodyPart#wounds wound} is ignored. */
    public void wound(long unit, BodyPart part) {
        Long2IntOpenHashMap counts = countsFor(part);
        if (counts != null) counts.addTo(unit, 1);
    }

    /** Wounds {@code unit} carries on {@code part}; zero for a part that cannot be wounded. */
    public int wounds(long unit, BodyPart part) {
        Long2IntOpenHashMap counts = countsFor(part);
        return counts == null ? 0 : counts.get(unit);
    }

    /** Whether {@code unit} carries any wound at all. */
    public boolean isWounded(long unit) {
        return legWounds.containsKey(unit) || armWounds.containsKey(unit);
    }

    /** Dresses every wound {@code unit} carries: what a medic's completed treatment does. */
    public void dress(long unit) {
        legWounds.remove(unit);
        armWounds.remove(unit);
    }

    /** The stat multiplier {@code count} wounds on one limb amount to: stacking, never below the floor. */
    public static float multiplierFor(int count) {
        if (count <= 0) return 1f;
        return Math.max(WOUND_FLOOR, (float) Math.pow(WOUND_MULTIPLIER, count));
    }

    private Long2IntOpenHashMap countsFor(BodyPart part) {
        return switch (part) {
            case LEGS -> legWounds;
            case ARMS -> armWounds;
            default -> null;
        };
    }
}
