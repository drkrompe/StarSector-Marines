package com.dillon.starsectormarines.campaign.polity;

import com.dillon.starsectormarines.battle.infantry.EquipmentGrade;

/**
 * What the polity can <em>make</em>, as opposed to what it knows how to make.
 * The ground-side parallel of vanilla's ship production quality: a four-step
 * ladder read off the best of the polity's own markets, and the only authority
 * over the grade and armour tier its troops are issued
 * ({@code polity-ground-doctrine.md}, law 3).
 *
 * <p>Knowing a Masterwork pattern and being able to build one are different
 * facts, which is the whole point of the blueprint parallel: released kit
 * supplies the <em>set</em> a derived roster draws from, and this step supplies
 * the <em>quality</em> it comes out at. Doctrine may tighten a grade table
 * toward the top of what this admits; it may never reach past the cap.
 *
 * <p>Pure by construction — {@link #of} takes four booleans and reads no
 * campaign state. The adapter that decides whether a market has Heavy Industry
 * or is running a supplies deficit lives with the campaign tier and calls in
 * here.
 */
public enum GroundProductionQuality {

    /** No ground production at all: worn Common kit, fatigues, and no motor pool. */
    NONE(EquipmentGrade.SURPLUS, 1, false),

    /** Heavy Industry: Service kit, tier-2 patterns, and a shed that can build a mech. */
    BASIC(EquipmentGrade.SERVICE, 2, true),

    /** Orbital Works, or Orbital Works pulled down by a deficit: a Milspec tail. */
    ADVANCED(EquipmentGrade.MILSPEC, 3, true),

    /** Orbital Works with nothing missing: the only step that can make Masterwork. */
    ADVANCED_FULL(EquipmentGrade.MASTERWORK, 4, true);

    private final EquipmentGrade highestGrade;
    private final int highestArmorTier;
    private final boolean canFabricateMech;

    GroundProductionQuality(EquipmentGrade highestGrade, int highestArmorTier,
                            boolean canFabricateMech) {
        this.highestGrade = highestGrade;
        this.highestArmorTier = highestArmorTier;
        this.canFabricateMech = canFabricateMech;
    }

    /**
     * The step the polity's best producing market sits at.
     *
     * <p>The base ladder is the industry it has: nothing is {@link #NONE},
     * Heavy Industry is {@link #BASIC}, and Orbital Works is
     * {@link #ADVANCED_FULL}. A deficit in supplies or in heavy armaments then
     * pulls that result down <em>one</em> step, never below {@link #NONE} —
     * one step whether one shortage or both, because the shortage is a fact
     * about the market's output rather than a tally to be summed. That is why
     * Orbital Works running short reads {@link #ADVANCED} and a Heavy Industry
     * running short falls all the way back to importing.
     */
    public static GroundProductionQuality of(boolean heavyIndustry, boolean orbitalWorks,
                                             boolean suppliesDeficit,
                                             boolean heavyArmamentsDeficit) {
        GroundProductionQuality base = orbitalWorks ? ADVANCED_FULL
                : heavyIndustry ? BASIC
                : NONE;
        if (!suppliesDeficit && !heavyArmamentsDeficit) return base;
        return stepDown(base);
    }

    /** One rung lower, floored at {@link #NONE}. */
    public GroundProductionQuality lowered() {
        return stepDown(this);
    }

    /** The best grade this step can manufacture. Nothing above it is ever issued. */
    public EquipmentGrade highestGrade() {
        return highestGrade;
    }

    /** The best armour tier this step can manufacture, on the catalog's 1..4 scale. */
    public int highestArmorTier() {
        return highestArmorTier;
    }

    /** Whether the polity's sheds can turn out a mech at all. */
    public boolean canFabricateMech() {
        return canFabricateMech;
    }

    /** Whether this step could issue the given grade. */
    public boolean admits(EquipmentGrade grade) {
        return grade != null && grade.tier <= highestGrade.tier;
    }

    /** Whether this step could issue armour of the given catalog tier. */
    public boolean admitsArmorTier(int tier) {
        return tier <= highestArmorTier;
    }

    private static GroundProductionQuality stepDown(GroundProductionQuality step) {
        int lowered = Math.max(0, step.ordinal() - 1);
        return values()[lowered];
    }
}
