package com.dillon.starsectormarines.campaign.systems;

import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.CampaignSystem;
import com.dillon.starsectormarines.campaign.CampaignTable;
import com.dillon.starsectormarines.campaign.ContractState;
import com.dillon.starsectormarines.campaign.ContractType;
import com.dillon.starsectormarines.campaign.HouseStatus;

import java.util.EnumSet;
import java.util.Random;

/** Evaluates patron-fall and deterministic monthly stationing defaults. */
public final class StationingDefaultSystem implements CampaignSystem {

    static final int DAYS_PER_MONTH = 30;
    static final int MAX_DEFAULT_CHANCE_PERCENT = 8;
    static final int MIN_DEFAULT_CHANCE_PERCENT = 1;
    static final int POWER_PER_PERCENT_REDUCTION = 100;

    interface DefaultRoll {
        boolean defaults(long contractId, int checkpointDay, int chancePercent);
    }

    private final DefaultRoll roll;

    public StationingDefaultSystem() {
        this(StationingDefaultSystem::deterministicRoll);
    }

    StationingDefaultSystem(DefaultRoll roll) {
        this.roll = roll;
    }

    @Override
    public String name() {
        return "StationingDefault";
    }

    @Override
    public EnumSet<CampaignTable> reads() {
        return EnumSet.of(CampaignTable.HOUSES, CampaignTable.CONTRACTS);
    }

    @Override
    public EnumSet<CampaignTable> writes() {
        return EnumSet.of(CampaignTable.CONTRACTS);
    }

    @Override
    public void tick(CampaignState state, int day) {
        for (int row = 0; row < state.contractCount; row++) {
            if (!ContractType.fromByte(state.contractType[row]).isStationing()) continue;
            ContractState contractState = ContractState.fromByte(state.contractState[row]);
            if (contractState != ContractState.ACTIVE
                    && contractState != ContractState.IN_PROGRESS) {
                continue;
            }

            int patronRow = state.houseIndex(state.contractPatronHouseId[row]);
            if (patronRow >= 0
                    && HouseStatus.fromByte(state.houseStatus[patronRow]) == HouseStatus.DEPOSED) {
                state.contractState[row] = ContractState.DEFAULTED.toByte();
                continue;
            }

            int lastCheck = state.contractLastDefaultCheckTick[row];
            if (lastCheck < 0) {
                state.contractLastDefaultCheckTick[row] = day;
                continue;
            }
            int expires = state.contractExpiresTick[row];
            int checkThrough = expires >= 0 ? Math.min(day, expires) : day;
            int chance = defaultChancePercent(patronRow >= 0 ? state.housePower[patronRow] : 0);
            while (lastCheck + DAYS_PER_MONTH <= checkThrough) {
                int checkpoint = lastCheck + DAYS_PER_MONTH;
                state.contractLastDefaultCheckTick[row] = checkpoint;
                if (roll.defaults(state.contractId[row], checkpoint, chance)) {
                    state.contractState[row] = ContractState.DEFAULTED.toByte();
                    break;
                }
                lastCheck = checkpoint;
            }
        }
    }

    static int defaultChancePercent(int housePower) {
        int reduction = Math.max(0, housePower) / POWER_PER_PERCENT_REDUCTION;
        return Math.max(MIN_DEFAULT_CHANCE_PERCENT,
                MAX_DEFAULT_CHANCE_PERCENT - reduction);
    }

    private static boolean deterministicRoll(long contractId, int checkpointDay,
                                             int chancePercent) {
        long seed = contractId * 0x9E3779B97F4A7C15L
                ^ (long) checkpointDay * 0xC2B2AE3D27D4EB4FL;
        return new Random(seed).nextInt(100) < chancePercent;
    }
}
