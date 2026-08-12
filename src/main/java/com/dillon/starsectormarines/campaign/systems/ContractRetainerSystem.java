package com.dillon.starsectormarines.campaign.systems;

import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.CampaignSystem;
import com.dillon.starsectormarines.campaign.CampaignTable;
import com.dillon.starsectormarines.campaign.ContractState;
import com.dillon.starsectormarines.campaign.ContractType;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;

import java.util.EnumSet;

/** Pays whole-month stationing retainers exactly once from persisted clocks. */
public final class ContractRetainerSystem implements CampaignSystem {

    static final int DAYS_PER_MONTH = 30;

    interface CreditSink {
        boolean add(int credits);
    }

    private final CreditSink creditSink;

    public ContractRetainerSystem() {
        this(ContractRetainerSystem::addPlayerCredits);
    }

    ContractRetainerSystem(CreditSink creditSink) {
        this.creditSink = creditSink;
    }

    @Override
    public String name() {
        return "ContractRetainer";
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
            ContractType type = ContractType.fromByte(state.contractType[i]);
            if (!type.isStationing()) continue;
            ContractState contractState = ContractState.fromByte(state.contractState[i]);
            if (contractState != ContractState.ACTIVE
                    && contractState != ContractState.IN_PROGRESS) {
                continue;
            }
            int monthly = state.contractRetainerPerMonth[i];
            if (monthly <= 0) continue;

            int lastPaid = state.contractLastRetainerTick[i];
            if (lastPaid < 0) {
                lastPaid = state.contractAcceptedTick[i] >= 0
                        ? state.contractAcceptedTick[i] : day;
                state.contractLastRetainerTick[i] = lastPaid;
            }
            int expires = state.contractExpiresTick[i];
            int paymentThrough = expires >= 0 ? Math.min(day, expires) : day;
            int wholeMonths = Math.max(0, paymentThrough - lastPaid) / DAYS_PER_MONTH;
            if (wholeMonths <= 0) continue;

            long due = (long) monthly * wholeMonths;
            int credits = (int) Math.min(Integer.MAX_VALUE, due);
            if (creditSink.add(credits)) {
                state.contractLastRetainerTick[i] = lastPaid + wholeMonths * DAYS_PER_MONTH;
            }
        }
    }

    private static boolean addPlayerCredits(int credits) {
        if (Global.getSector() == null) return false;
        CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
        if (fleet == null || fleet.getCargo() == null) return false;
        fleet.getCargo().getCredits().add(credits);
        return true;
    }
}
