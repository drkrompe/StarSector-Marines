package com.dillon.starsectormarines.campaign.systems;

import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.CampaignSystem;
import com.dillon.starsectormarines.campaign.CampaignTable;
import com.dillon.starsectormarines.campaign.ContractState;
import com.dillon.starsectormarines.campaign.ContractType;
import com.dillon.starsectormarines.marine.MarineCaptain;
import com.dillon.starsectormarines.marine.MarineRosterScript;
import com.dillon.starsectormarines.marine.Rank;

import java.text.MessageFormat;
import java.util.EnumSet;

/** Awards whole-month passive captain XP for active Cadre contracts. */
public final class CadreTrainingSystem implements CampaignSystem {

    static final int XP_PER_MONTH = 200;

    interface CaptainLookup {
        MarineCaptain find(String id);
    }

    private final CaptainLookup captainLookup;

    public CadreTrainingSystem() {
        this(CadreTrainingSystem::findLiveCaptain);
    }

    CadreTrainingSystem(CaptainLookup captainLookup) {
        this.captainLookup = captainLookup;
    }

    @Override
    public String name() {
        return "CadreTraining";
    }

    @Override
    public EnumSet<CampaignTable> reads() {
        return EnumSet.of(CampaignTable.CONTRACTS);
    }

    @Override
    public EnumSet<CampaignTable> writes() {
        return EnumSet.of(CampaignTable.CONTRACTS);
    }

    @Override
    public void tick(CampaignState state, int day) {
        for (int i = 0; i < state.contractCount; i++) {
            if (ContractType.fromByte(state.contractType[i]) != ContractType.CADRE) continue;
            ContractState contractState = ContractState.fromByte(state.contractState[i]);
            if (contractState != ContractState.ACTIVE
                    && contractState != ContractState.IN_PROGRESS) {
                continue;
            }
            int captainSlot = state.contractCaptainId[i];
            String captainId = captainSlot >= 0 ? state.captainRegistry.get(captainSlot) : null;
            MarineCaptain captain = captainId != null ? captainLookup.find(captainId) : null;
            if (captain == null) continue;

            int lastTrained = state.contractLastTrainingTick[i];
            if (lastTrained < 0) {
                lastTrained = state.contractAcceptedTick[i] >= 0
                        ? state.contractAcceptedTick[i] : day;
                state.contractLastTrainingTick[i] = lastTrained;
            }
            int expires = state.contractExpiresTick[i];
            int trainingThrough = expires >= 0 ? Math.min(day, expires) : day;
            int wholeMonths = Math.max(0, trainingThrough - lastTrained)
                    / ContractRetainerSystem.DAYS_PER_MONTH;
            if (wholeMonths <= 0) continue;

            long earned = (long) wholeMonths * XP_PER_MONTH;
            awardXp(captain, (int) Math.min(Integer.MAX_VALUE, earned), trainingThrough);
            state.contractLastTrainingTick[i] = lastTrained
                    + wholeMonths * ContractRetainerSystem.DAYS_PER_MONTH;
        }
    }

    static void awardXp(MarineCaptain captain, int amount, int day) {
        if (captain == null || amount <= 0) return;
        captain.addXp(amount);
        while (captain.rank() != Rank.GENERAL
                && captain.xp() >= captain.rank().xpToNext()) {
            captain.addXp(-captain.rank().xpToNext());
            Rank next = captain.rank().promote();
            captain.setRank(next);
            captain.commendations().add(MessageFormat.format(
                    "Day {0}: Promoted to {1} during cadre duty.",
                    day, next.displayName()));
        }
    }

    private static MarineCaptain findLiveCaptain(String id) {
        MarineRosterScript script = MarineRosterScript.getInstance();
        return script != null ? script.roster().byId(id) : null;
    }
}
