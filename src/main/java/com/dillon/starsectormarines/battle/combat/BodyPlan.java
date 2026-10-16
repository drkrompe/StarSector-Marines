package com.dillon.starsectormarines.battle.combat;

import java.util.List;

/**
 * The parts a kind of body is hit on, and how they stack up it. A unit type that has a plan
 * ({@code UnitType.bodyPlan()}) is struck on a part: the round takes the part whose height band
 * it arrives in, cover hides it part by part, the part scales the damage and carries the
 * consequence. A type without a plan is one whole-body contact and keeps the flat cover chance.
 *
 * <p>Bands are stacked from the feet up in the plan's band order, each as tall as its part's
 * share, so a uniform height lands on each part in proportion to its share. The band order and
 * the parts' own order ({@link #parts()}, which their {@link BodyPart#index()} follows) are
 * separate: the infantry plan lists head, arms, torso, legs, and stacks legs, torso, arms, head.
 *
 * <p>Infantry ({@link #INFANTRY}) is the only plan today. A mech plan is the next one
 * ({@code mech-locational-damage.md}): sections rather than limbs, a lateral split of a band, and
 * per-section pools. See {@code body-part-cover.md}.
 */
public final class BodyPlan {

    /** A person: head, arms, torso and legs, stacked legs, torso, arms, head. */
    public static final BodyPlan INFANTRY = new BodyPlan("infantry",
            new BodyPart[] {BodyPart.HEAD, BodyPart.ARMS, BodyPart.TORSO, BodyPart.LEGS},
            new BodyPart[] {BodyPart.LEGS, BodyPart.TORSO, BodyPart.ARMS, BodyPart.HEAD});

    private final String id;
    private final BodyPart[] parts;
    private final BodyPart[] upTheBody;
    private final List<BodyPart> partList;

    /**
     * @param parts every part, each at its own {@link BodyPart#index()}
     * @param upTheBody the same parts, bottom band first
     */
    public BodyPlan(String id, BodyPart[] parts, BodyPart[] upTheBody) {
        if (parts.length == 0 || upTheBody.length != parts.length) {
            throw new IllegalArgumentException(id + ": bands must cover every part once");
        }
        for (int i = 0; i < parts.length; i++) {
            if (parts[i].index() != i) {
                throw new IllegalArgumentException(id + ": " + parts[i] + " is not at its index " + i);
            }
        }
        boolean[] banded = new boolean[parts.length];
        for (BodyPart band : upTheBody) {
            if (!contains(parts, band)) throw new IllegalArgumentException(id + ": " + band + " is not a part");
            if (banded[band.index()]) throw new IllegalArgumentException(id + ": " + band + " has two bands");
            banded[band.index()] = true;
        }
        this.id = id;
        this.parts = parts.clone();
        this.upTheBody = upTheBody.clone();
        this.partList = List.of(this.parts);
    }

    public String id() {
        return id;
    }

    /** Every part, in index order. */
    public List<BodyPart> parts() {
        return partList;
    }

    /** How many parts this plan has; per-part tables are this long. */
    public int size() {
        return parts.length;
    }

    /** Whether {@code part} is one of this plan's parts. */
    public boolean owns(BodyPart part) {
        int i = part.index();
        return i >= 0 && i < parts.length && parts[i] == part;
    }

    /**
     * The part at {@code heightFraction} up the body, 0 at the feet and 1 at the top. A height
     * above every band (shares summing short of one, or a fraction past the top) is the top part.
     * Allocation-free: this runs per shot.
     */
    public BodyPart atHeight(float heightFraction) {
        float r = heightFraction;
        BodyPart[] bands = upTheBody;
        for (BodyPart part : bands) {
            r -= part.share;
            if (r < 0f) return part;
        }
        return bands[bands.length - 1];
    }

    @Override
    public String toString() {
        return id;
    }

    private static boolean contains(BodyPart[] parts, BodyPart part) {
        for (BodyPart p : parts) if (p == part) return true;
        return false;
    }
}
