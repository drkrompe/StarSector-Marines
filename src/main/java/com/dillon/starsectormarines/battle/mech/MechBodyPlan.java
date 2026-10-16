package com.dillon.starsectormarines.battle.mech;

import com.dillon.starsectormarines.battle.combat.BodyPart;
import com.dillon.starsectormarines.battle.combat.BodyPlan;

/**
 * How one chassis is taken apart: the height bands a round is struck in, the lateral share of
 * torso-height hits its arms mount takes, and how its armor capacity and structure divide across
 * its {@link MechSection sections}. Authored per chassis, because a Hound's nose gun sits
 * differently from a Bulwark's arms.
 *
 * <p>Bands stack legs, torso, shoulders, head from the feet up ({@link #bodyPlan()}), so the
 * ballistic height a round arrives at picks the band exactly as it picks a marine's part. Two
 * bands split further: a torso-height hit lands on the arms mount with the plan's
 * {@link #armShare}, and a shoulder-height hit lands on the shoulder on the side the round came
 * from. A chassis that draws a lone shoulder pod on its centreline ({@link #centresLonePod})
 * takes every shoulder-height hit on it, whichever side the round came from.
 *
 * <p>The pool shares sum to one, so the sections together hold exactly the armor and structure
 * the stock frame and plating set. A mount the chassis was deployed without has no pool: its
 * share folds into the torso. The sections carry no cover rows yet, so cover still meets a mech
 * as one body with the flat chance and damage cut (slice 3 of {@code mech-locational-damage.md}).
 */
public final class MechBodyPlan {

    /** Light assault: long legs, a nose chaingun on the centreline, one centred pod. */
    public static final MechBodyPlan HOUND = new MechBodyPlan("hound",
            0.35f, 0.40f, 0.17f, 0.08f, 0.30f,
            new float[] {0.22f, 0.50f, 0.10f, 0.06f, 0.06f, 0.06f}, true);

    /** Heavy support: twin arm chainguns out wide, a pod on each shoulder. */
    public static final MechBodyPlan BULWARK = new MechBodyPlan("bulwark",
            0.35f, 0.40f, 0.18f, 0.07f, 0.40f,
            new float[] {0.22f, 0.44f, 0.12f, 0.07f, 0.07f, 0.08f}, false);

    /** Long-range support: one heavy cannon, an LRM rack on each shoulder. */
    public static final MechBodyPlan SIROCCO = new MechBodyPlan("sirocco",
            0.33f, 0.40f, 0.20f, 0.07f, 0.25f,
            new float[] {0.20f, 0.46f, 0.10f, 0.08f, 0.08f, 0.08f}, false);

    private final String id;
    private final BodyPlan bodyPlan;
    private final BodyPart legs;
    private final BodyPart torso;
    private final BodyPart shoulders;
    private final BodyPart head;
    private final float armShare;
    private final float[] poolShares;
    private final boolean centresLonePod;

    /**
     * @param legsBand, torsoBand, shoulderBand, headBand height-band shares, summing to one
     * @param armShare fraction of torso-height hits that land on the arms mount
     * @param poolShares share of armor and structure per {@link MechSection}, in its order, summing to one
     * @param centresLonePod whether a single fitted shoulder pod is drawn on the centreline
     */
    public MechBodyPlan(String id, float legsBand, float torsoBand, float shoulderBand, float headBand,
                        float armShare, float[] poolShares, boolean centresLonePod) {
        if (poolShares.length != MechSection.VALUES.length) {
            throw new IllegalArgumentException(id + ": a pool share per section");
        }
        if (Math.abs(legsBand + torsoBand + shoulderBand + headBand - 1f) > 1e-4f) {
            throw new IllegalArgumentException(id + ": band shares must sum to one");
        }
        float pools = 0f;
        for (float share : poolShares) {
            if (!(share >= 0f)) throw new IllegalArgumentException(id + ": negative pool share");
            pools += share;
        }
        if (Math.abs(pools - 1f) > 1e-4f || !(poolShares[MechSection.TORSO.ordinal()] > 0f)) {
            throw new IllegalArgumentException(id + ": pool shares must sum to one with a torso");
        }
        if (!(armShare >= 0f && armShare <= 1f)) {
            throw new IllegalArgumentException(id + ": arm share must be a fraction");
        }
        this.id = id;
        this.head = new BodyPart("HEAD", 0, headBand, 1f, BodyPart.Consequence.SENSOR_LOSS);
        this.shoulders = new BodyPart("SHOULDERS", 1, shoulderBand, 1f, BodyPart.Consequence.MOUNT_LOSS);
        this.torso = new BodyPart("TORSO", 2, torsoBand, 1f, BodyPart.Consequence.NONE);
        this.legs = new BodyPart("LEGS", 3, legsBand, 1f, BodyPart.Consequence.CRIPPLE);
        this.bodyPlan = new BodyPlan("mech-" + id,
                new BodyPart[] {head, shoulders, torso, legs},
                new BodyPart[] {legs, torso, shoulders, head});
        this.armShare = armShare;
        this.poolShares = poolShares.clone();
        this.centresLonePod = centresLonePod;
    }

    public String id() {
        return id;
    }

    /** The height bands, legs at the bottom: what the ballistic height picks. */
    public BodyPlan bodyPlan() {
        return bodyPlan;
    }

    /** Fraction of torso-height hits the arms mount takes. */
    public float armShare() {
        return armShare;
    }

    /** Authored share of the chassis pools {@code section} holds when every mount is fitted. */
    public float poolShare(MechSection section) {
        return poolShares[section.ordinal()];
    }

    public boolean centresLonePod() {
        return centresLonePod;
    }

    /**
     * The section a round in band {@code band} lands on.
     *
     * @param fromLeft whether the round arrived on the chassis's left
     * @param armRoll a uniform roll in [0, 1) deciding the torso band's lateral split
     * @param fitted which sections exist, indexed by {@link MechSection}; an unfitted mount has none
     */
    public MechSection section(BodyPart band, boolean fromLeft, float armRoll, boolean[] fitted) {
        if (band == legs) return MechSection.LEGS;
        if (band == head) return MechSection.HEAD;
        if (band == shoulders) {
            MechSection near = fromLeft ? MechSection.LEFT_SHOULDER : MechSection.RIGHT_SHOULDER;
            MechSection far = fromLeft ? MechSection.RIGHT_SHOULDER : MechSection.LEFT_SHOULDER;
            if (fitted[near.ordinal()]) return near;
            // A lone pod on the centreline faces both sides; an empty shoulder is the torso's plate.
            if (centresLonePod && fitted[far.ordinal()]) return far;
            return MechSection.TORSO;
        }
        if (band == torso) {
            return armRoll < armShare && fitted[MechSection.ARMS.ordinal()] ? MechSection.ARMS : MechSection.TORSO;
        }
        throw new IllegalArgumentException(id + ": " + band + " is not one of this plan's bands");
    }

    /** Whether {@code part} is one of this plan's bands. */
    public boolean owns(BodyPart part) {
        return part != null && bodyPlan.owns(part);
    }

    @Override
    public String toString() {
        return id;
    }
}
