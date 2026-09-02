package com.dillon.starsectormarines.campaign;

import com.dillon.starsectormarines.battle.infantry.ExperienceTier;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.MarineSoldier;
import com.dillon.starsectormarines.marine.MarineSoldierStatus;
import com.dillon.starsectormarines.marine.MarineSquad;
import com.dillon.starsectormarines.campaign.systems.StationedStrengthSystem;
import com.dillon.starsectormarines.marine.SquadExperienceStandard;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.combat.MutableStat.StatMod;
import com.fs.starfarer.api.combat.StatBonus;
import com.fs.starfarer.api.impl.campaign.ids.Stats;

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

    /**
     * Everything the company is currently contributing to this market's ground
     * defence, read back off vanilla's own stat rather than recomputed from
     * campaign rows.
     *
     * <p>Reading the stat is what makes the number <em>exactly</em> what was
     * added: {@link StationedStrengthSystem} writes one flat modifier per active
     * Garrison contract under {@link StationedStrengthSystem#MODIFIER_ID_PREFIX},
     * and this sums that same set. A contract that settled, a row that was
     * compacted away, a modifier the daily sweep already removed — none of them
     * are here, because none of them are on the market any more. Nothing has to
     * agree with anything.
     *
     * <p><b>Why it has to be subtracted at all.</b> {@code MarketCMD.getDefenderStr}
     * is {@code stat.computeEffective(0f)}, and {@code computeEffective} is
     * {@code (base + percent + flat) * mult} — so a flat modifier written here is
     * inside the defender strength the bridge carries. Counting it as the
     * colony's own militia as well would field the company's stationed marines
     * twice: once as themselves and once as somebody else's garrison.
     *
     * <p>Because {@code computeEffective} multiplies the flat total by the
     * industry and stability chain, the detachment's real effect on defender
     * strength is its flat sum times {@code stat.getMult()} — at a stable colony
     * with Heavy Batteries, roughly twice the flats. This returns that scaled
     * figure, so what comes back out is exactly what went in.
     *
     * @return the detachment's effect on the stat, never negative; {@code 0} for a null market
     */
    public static float totalAt(MarketAPI market) {
        if (market == null || market.getStats() == null
                || market.getStats().getDynamic() == null) {
            return 0f;
        }
        return totalIn(market.getStats().getDynamic().getMod(Stats.GROUND_DEFENSES_MOD));
    }

    /** The same sum over an already-resolved ground-defence stat. */
    public static float totalIn(StatBonus groundDefence) {
        if (groundDefence == null) return 0f;
        float total = 0f;
        for (StatMod mod : groundDefence.getFlatBonuses().values()) {
            if (mod == null || mod.getSource() == null) continue;
            if (!mod.getSource().startsWith(StationedStrengthSystem.MODIFIER_ID_PREFIX)) {
                continue;
            }
            total += mod.getValue();
        }
        return Math.max(0f, total * groundDefence.getMult());
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
