package com.dillon.starsectormarines.campaign.systems;

import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.CampaignSystem;
import com.dillon.starsectormarines.campaign.CampaignTable;
import com.dillon.starsectormarines.marine.MarineCaptain;
import com.dillon.starsectormarines.marine.MarineRosterScript;
import com.dillon.starsectormarines.marine.Status;
import com.dillon.starsectormarines.marine.Trait;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

/** Materializes a captain-local reaction to witnessed moral choices. */
public final class CaptainTraitDriftSystem implements CampaignSystem {

    static final int MINIMUM_SERVICE_DAYS = 90;
    static final int MINIMUM_WITNESSED_CHOICES = 3;
    static final int COMBINED_THRESHOLD = 45;
    static final int AXIS_THRESHOLD = 10;
    static final int CONTRADICTION_LIMIT = 15;

    interface CaptainSource {
        List<MarineCaptain> all();
    }

    private final CaptainSource captainSource;

    public CaptainTraitDriftSystem() {
        this(CaptainTraitDriftSystem::liveCaptains);
    }

    CaptainTraitDriftSystem(CaptainSource captainSource) {
        this.captainSource = captainSource;
    }

    @Override
    public String name() {
        return "CaptainTraitDrift";
    }

    @Override
    public EnumSet<CampaignTable> reads() {
        return EnumSet.of(CampaignTable.MORAL_COMPASS);
    }

    /** The mutation target is the separately persisted marine roster graph. */
    @Override
    public EnumSet<CampaignTable> writes() {
        return EnumSet.noneOf(CampaignTable.class);
    }

    @Override
    public void tick(CampaignState state, int day) {
        if (state == null) return;
        List<MarineCaptain> captains = captainSource.all();
        if (captains == null) return;

        for (MarineCaptain captain : captains) {
            evaluateCaptain(state, captain, day);
        }
    }

    static boolean evaluateCaptain(CampaignState state, MarineCaptain captain, int day) {
        if (captain == null || captain.status() == Status.KIA
                || captain.hasResolvedMoralOutlook()
                || day - captain.createdAtDay() < MINIMUM_SERVICE_DAYS) {
            return false;
        }

        int joinedDay = (int) Math.ceil(captain.createdAtDay());
        int witnessed = 0;
        int mercy = 0;
        int integrity = 0;
        int stewardship = 0;
        for (int row = 0; row < state.moralChoiceCount; row++) {
            if (state.moralChoiceHappenedTick[row] < joinedDay) continue;
            witnessed++;
            mercy += state.moralChoiceMercyDelta[row];
            integrity += state.moralChoiceIntegrityDelta[row];
            stewardship += state.moralChoiceStewardshipDelta[row];
        }
        if (witnessed < MINIMUM_WITNESSED_CHOICES) return false;

        Trait outlook = classify(mercy, integrity, stewardship);
        return outlook != null && captain.resolveMoralOutlook(outlook, day);
    }

    static Trait classify(int mercy, int integrity, int stewardship) {
        int combined = mercy + integrity + stewardship;
        int positiveAxes = qualifyingAxes(mercy, integrity, stewardship, AXIS_THRESHOLD);
        if (combined >= COMBINED_THRESHOLD && positiveAxes >= 2
                && Math.min(mercy, Math.min(integrity, stewardship))
                    >= -CONTRADICTION_LIMIT) {
            return Trait.IDEALIST;
        }

        int negativeAxes = qualifyingAxes(-mercy, -integrity, -stewardship,
                AXIS_THRESHOLD);
        if (combined <= -COMBINED_THRESHOLD && negativeAxes >= 2
                && Math.max(mercy, Math.max(integrity, stewardship))
                    <= CONTRADICTION_LIMIT) {
            return Trait.CYNICAL;
        }
        return null;
    }

    private static int qualifyingAxes(int first, int second, int third, int threshold) {
        int count = first >= threshold ? 1 : 0;
        if (second >= threshold) count++;
        if (third >= threshold) count++;
        return count;
    }

    private static List<MarineCaptain> liveCaptains() {
        MarineRosterScript script = MarineRosterScript.getInstance();
        return script != null ? script.roster().all() : Collections.emptyList();
    }
}
