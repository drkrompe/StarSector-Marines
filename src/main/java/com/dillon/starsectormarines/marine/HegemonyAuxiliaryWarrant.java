package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.campaign.AuxiliaryWarrantProgress;
import com.dillon.starsectormarines.campaign.CampaignState;
import com.fs.starfarer.api.campaign.RepLevel;

import java.util.List;

/**
 * The Hegemony auxiliary warrant: the first military warrant a company earns by story rather
 * than by standing ({@code progression-nouns.md}, Kit and armory).
 *
 * <p>After *A Small War* settles, a Hegemony procurement office offers enrolment on the record
 * of the Garnir after-action report and the company's Agreus service. The standing rule's
 * higher bar does not apply; the authored route asks for a relationship at
 * {@link #RELATIONSHIP_FLOOR}, the licence threshold. Acceptance opens the Auxiliary Section
 * sheet, the section in approved Arbalests (stage one). The Hegemony Shock sheet, in XIV
 * pattern (stage two), follows the first honoured obligation period, reported by the
 * warrant's obligations through {@link #honouredPeriod}. Both stages name cards.
 *
 * <p>Every transition is once-only on {@link AuxiliaryWarrantProgress}, and the grants go
 * through {@link MilitaryWarrants#grant}, which is idempotent per card, so a reloaded
 * answer or a repeated report grants nothing further.
 */
public final class HegemonyAuxiliaryWarrant {

    /** The relationship the offer asks for; it also excludes any hostility. */
    public static final RepLevel RELATIONSHIP_FLOOR = RepLevel.FAVORABLE;
    /** Stage one: the section in approved suits, the upgrade of a sheet the company already fields. */
    public static final String STAGE_ONE = new LoadoutCard(LoadoutCard.Kind.SHEET,
            SquadEquipmentDoctrines.HEGEMONY_AUXILIARY_SECTION_ARMOR).id();
    /** Stage two: the XIV shock sheet, trusted only after the terms have been kept once. */
    public static final String STAGE_TWO = new LoadoutCard(LoadoutCard.Kind.SHEET,
            SquadEquipmentDoctrines.HEGEMONY_SHOCK_ARMOR).id();

    private HegemonyAuxiliaryWarrant() {}

    /**
     * Whether the terms are due to be offered: *A Small War* settled, the Hegemony at
     * {@link #RELATIONSHIP_FLOOR} or better, and never offered before.
     */
    public static boolean offerDue(boolean smallWarSettled, RepLevel hegemonyRelationship,
                                   AuxiliaryWarrantProgress progress) {
        return smallWarSettled && hegemonyRelationship != null
                && hegemonyRelationship.isAtWorst(RELATIONSHIP_FLOOR)
                && progress != null && progress.stage() == AuxiliaryWarrantProgress.Stage.NONE;
    }

    /** Puts the terms in the company's correspondence; false when already offered or answered. */
    public static boolean offer(CampaignState state, int day) {
        return state != null && state.auxiliaryWarrant().offer(day);
    }

    /**
     * Accepts the offered terms and records stage one.
     *
     * @return true when this call enrolled the company; false when there was no open offer
     */
    public static boolean accept(CampaignState state, int day) {
        if (state == null || state.auxiliaryWarrant().stage() != AuxiliaryWarrantProgress.Stage.OFFERED) {
            return false;
        }
        MilitaryWarrants.grant(state, ArmorTradition.HEGEMONY, List.of(STAGE_ONE),
                MilitaryWarrants.Reason.STORY, day);
        return state.auxiliaryWarrant().accept(day);
    }

    /** Declines the offered terms. They are never offered again; every other route stays open. */
    public static boolean decline(CampaignState state, int day) {
        return state != null && state.auxiliaryWarrant().decline(day);
    }

    /**
     * The warrant's first honoured obligation period: opens stage two once, for an enrolled
     * company. The obligation clock is the one caller.
     *
     * @return true when this call opened stage two
     */
    public static boolean honouredPeriod(CampaignState state, int day) {
        if (state == null || state.auxiliaryWarrant().stage() != AuxiliaryWarrantProgress.Stage.ACCEPTED
                || state.auxiliaryWarrant().secondStage()) {
            return false;
        }
        MilitaryWarrants.grant(state, ArmorTradition.HEGEMONY, List.of(STAGE_TWO),
                MilitaryWarrants.Reason.STORY, day);
        return state.auxiliaryWarrant().openSecondStage(day);
    }
}
