package com.dillon.starsectormarines.campaign.raid;

import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.listeners.MarineLossesStatModifier;
import com.fs.starfarer.api.combat.MutableStat;
import com.fs.starfarer.api.impl.campaign.graid.DisruptIndustryRaidObjectivePluginImpl;
import com.fs.starfarer.api.impl.campaign.graid.GroundRaidObjectivePlugin;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.MarketCMD.RaidType;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.MarketCMD.TempData;

import java.util.List;

/**
 * Turns a fought raid's outcome into the raid the game's own command then commits ({@code raid-resolution.md}).
 *
 * <p>The mod never applies a raid's consequences itself. It adjusts the raid the player set up, which the raid
 * command keeps on the market as its {@link TempData}, and the command (vanilla's {@code MarketCMD}, or
 * Nexerelin's subclass of it) does the rest exactly as for a rolled raid: hostile timeout, military response,
 * cooldown, stability, reputation, loot, disruption, listeners and the post-raid rules.
 *
 * <ul>
 *   <li>A won battle commits the market's full raid with its security's premium ({@link LedRaidReward}): at full
 *       effectiveness, its marine tokens filled to that size over the objectives the player chose.</li>
 *   <li>A lost battle commits it with no objectives: the attack still happened, but it achieves nothing, takes no
 *       loot, disrupts nothing and reports no objective to the listeners.</li>
 *   <li>Either way the fleet's cargo marines did not fight, so the command's marine-loss roll is zeroed for that
 *       one confirm through {@link #LOSSES}, armed only around it. The company's own casualties are written back
 *       by Results, as for every operation.</li>
 * </ul>
 */
public final class RaidOutcomeCommit {

    /** Market memory key under which the raid command keeps the raid being set up (vanilla's and Nexerelin's). */
    public static final String TEMP_KEY = "$MarketCMD_temp";

    static final String LOSSES_SOURCE = "starsector_marines_company_raid";

    /** Zeroes the raid command's marine-loss roll while armed; added and removed around one confirm. */
    public static final MarineLossesStatModifier LOSSES = new CompanyFoughtLosses();

    private RaidOutcomeCommit() {}

    /** The raid being set up on {@code market}, or null when there is none. */
    public static TempData raidOn(MarketAPI market) {
        if (market == null) return null;
        Object temp = market.getMemoryWithoutUpdate().get(TEMP_KEY);
        return temp instanceof TempData data ? data : null;
    }

    /**
     * Adjusts {@code temp} for the battle's outcome, before the raid command's confirm runs. A won raid is the market's
     * full raid at {@code premium} ({@link LedRaidReward}): full effectiveness, and its marine tokens filled to the
     * full raid's over the objectives the player chose, a disrupting raid's times the share of the works wrecked, so
     * every hall brought down adds days down ({@code raid-mission.md}, Break). A lost raid commits no objectives.
     */
    public static void prepare(TempData temp, boolean won, float worksShare, float premium) {
        if (temp == null) return;
        if (!won) {
            if (temp.objectives != null) temp.objectives.clear();
            return;
        }
        temp.raidMult = 1f;
        if (temp.objectives == null || temp.objectives.isEmpty()) return;
        int[] assigned = new int[temp.objectives.size()];
        for (int i = 0; i < assigned.length; i++) {
            GroundRaidObjectivePlugin objective = temp.objectives.get(i);
            assigned[i] = objective == null ? 0 : objective.getMarinesAssigned();
        }
        int[] filled = LedRaidReward.fill(assigned,
                LedRaidReward.tokens(premium, temp.raidType == RaidType.DISRUPT, worksShare));
        for (int i = 0; i < filled.length; i++) {
            GroundRaidObjectivePlugin objective = temp.objectives.get(i);
            if (objective != null && filled[i] != assigned[i]) objective.setMarinesAssigned(filled[i]);
        }
    }

    /**
     * The industry a disrupting raid targets, or null for any other raid. The intent is the raid's type: a raid for
     * valuables names no industry, even when the goods it takes come from one.
     */
    public static String targetIndustryId(TempData temp) {
        if (temp == null || temp.raidType != RaidType.DISRUPT) return null;
        if (temp.target != null) return temp.target.getId();
        if (temp.objectives == null) return null;
        for (GroundRaidObjectivePlugin objective : temp.objectives) {
            if (objective == null || objective.getMarinesAssigned() <= 0) continue;
            if (objective.getSource() != null) return objective.getSource().getId();
        }
        return null;
    }

    /**
     * The days a disrupting raid's target goes down at full effect, as the raid command reckons them for the marines
     * assigned (before its own spread of up to a quarter more, and before the share of the works wrecked), or 0 when
     * the raid disrupts nothing.
     */
    public static int disruptionDays(TempData temp) {
        if (temp == null || temp.raidType != RaidType.DISRUPT || temp.objectives == null) return 0;
        for (GroundRaidObjectivePlugin objective : temp.objectives) {
            if (objective instanceof DisruptIndustryRaidObjectivePluginImpl disrupt && objective.getMarinesAssigned() > 0) {
                return disrupt.getDisruptionDaysSort(objective.getMarinesAssigned());
            }
        }
        return 0;
    }

    static final class CompanyFoughtLosses implements MarineLossesStatModifier {
        @Override
        public void modifyMarineLossesStatPreRaid(MarketAPI market, List<GroundRaidObjectivePlugin> objectives,
                                                  MutableStat stat) {
            if (stat != null) stat.modifyMultAlways(LOSSES_SOURCE, 0f, "Fought by the company");
        }
    }
}
