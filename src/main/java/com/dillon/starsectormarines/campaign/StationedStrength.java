package com.dillon.starsectormarines.campaign;

import com.dillon.starsectormarines.battle.infantry.ExperienceTier;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.MarineSoldier;
import com.dillon.starsectormarines.marine.MarineSoldierStatus;
import com.dillon.starsectormarines.marine.MarineSquad;
import com.dillon.starsectormarines.marine.SquadExperienceStandard;

/**
 * What a stationed Garrison detachment is worth to the place it holds
 * ({@code contracts-nouns.md}, "stationed strength").
 *
 * <p>The number this produces is a flat contribution to the protected market's
 * vanilla ground-defence stat, so autoresolve and the raid intel's own forecast
 * feel the detachment whether or not the player is standing on the planet.
 *
 * <p><b>The ladder is anchored to vanilla's own unit of ground defence.</b>
 * A marine sitting in a market's cargo is worth
 * {@code MarketCMD.MARINES_IN_MARKET_CARGO_DEFENSE_BONUS} — one — to the
 * defence, so a Green seat is worth one and the better bands are multiples of
 * it. Twelve Veterans should outweigh twelve cargo marines and a Green squad
 * should not. Those multiples are a proposal to be measured, not a balance
 * decision the docs own.
 */
public final class StationedStrength {

    /** A recruit seat is worth exactly one cargo marine to the defence. */
    public static final float GREEN_SEAT_WORTH = 1.0f;
    public static final float REGULAR_SEAT_WORTH = 1.5f;
    public static final float VETERAN_SEAT_WORTH = 2.0f;
    public static final float ELITE_SEAT_WORTH = 3.0f;

    private StationedStrength() {}

    /**
     * The ground-defence contribution of the detachment stationed on this contract row.
     *
     * <p>Only {@link MarineSoldierStatus#ACTIVE} marines count. A WIA marine still holds
     * his billet — he is living strength for the assignment and is not replaced — but he
     * does not stand a line, so he contributes nothing here.
     *
     * <p>A row with no stationed squads in the roster is a legacy count-only assignment:
     * its committed marines are valued at the Green seat, which is what the player
     * handed over. A {@code null} roster takes the same path.
     *
     * @return the flat contribution, or zero for an out-of-range row
     */
    public static float valueFor(CampaignState state, int row, MarineRoster roster) {
        if (state == null || row < 0 || row >= state.contractCount) return 0f;
        long contractId = state.contractId[row];
        if (roster != null) {
            float fromSquads = 0f;
            boolean bound = false;
            for (MarineSquad squad : roster.squadsStationedOn(contractId)) {
                bound = true;
                for (MarineSoldier soldier : roster.squadMembers(squad)) {
                    if (soldier.status() != MarineSoldierStatus.ACTIVE) continue;
                    fromSquads += seatWorth(SquadExperienceStandard.bandFor(soldier));
                }
            }
            if (bound) return fromSquads;
        }
        return Math.max(0, state.contractMarinesCommitted[row]) * GREEN_SEAT_WORTH;
    }

    /** The defence value of one living seat deploying at this band. */
    public static float seatWorth(ExperienceTier band) {
        if (band == null) return GREEN_SEAT_WORTH;
        return switch (band) {
            case REGULAR -> REGULAR_SEAT_WORTH;
            case VETERAN -> VETERAN_SEAT_WORTH;
            case ELITE -> ELITE_SEAT_WORTH;
            default -> GREEN_SEAT_WORTH;
        };
    }
}
