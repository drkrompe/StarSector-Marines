package com.dillon.starsectormarines.campaign.systems;

import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.CampaignSystem;
import com.dillon.starsectormarines.campaign.CampaignTable;
import com.dillon.starsectormarines.campaign.ContractType;
import com.dillon.starsectormarines.campaign.PatronEngagementOutcome;
import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.marine.EquipmentTemplateCard;
import com.dillon.starsectormarines.marine.EquipmentTemplateCardItemPlugin;
import com.dillon.starsectormarines.marine.EquipmentTemplateCatalog;
import com.dillon.starsectormarines.marine.FactionEquipmentPicker;
import com.dillon.starsectormarines.marine.FactionEquipmentSource;
import com.dillon.starsectormarines.marine.MarineRosterScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.SpecialItemData;

import java.text.MessageFormat;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Delivers one faction-authored template card for each completed patron contract. */
public final class PatronEquipmentRewardSystem implements CampaignSystem {

    interface RewardAccess {
        /** Null means cargo is temporarily unavailable and processing must retry. */
        Set<String> unavailableTemplateIds();
        boolean grant(String templateId);
        void announce(String patronName, EquipmentTemplateCard template);
    }

    private final RewardAccess access;

    public PatronEquipmentRewardSystem() {
        this(new StarsectorRewardAccess());
    }

    PatronEquipmentRewardSystem(RewardAccess access) {
        this.access = access;
    }

    @Override
    public String name() {
        return "PatronEquipmentReward";
    }

    @Override
    public EnumSet<CampaignTable> reads() {
        return EnumSet.of(CampaignTable.HOUSES, CampaignTable.PATRON_MEMORY);
    }

    @Override
    public EnumSet<CampaignTable> writes() {
        return EnumSet.of(CampaignTable.PATRON_MEMORY);
    }

    @Override
    public void tick(CampaignState state, int day) {
        processPending(state, access);
    }

    /** Immediate bridge for battle-completed contracts; the daily system is the backstop. */
    public static int deliverPending(CampaignState state) {
        return processPending(state, new StarsectorRewardAccess());
    }

    static int processPending(CampaignState state, RewardAccess access) {
        if (state == null || access == null) return 0;
        Set<String> unavailable = access.unavailableTemplateIds();
        if (unavailable == null) return 0;
        Set<String> workingUnavailable = new HashSet<>(unavailable);
        int cursor = Math.max(0, Math.min(
                state.patronEquipmentRewardCursor, state.patronEngagementCount));
        state.patronEquipmentRewardCursor = cursor;
        int granted = 0;
        while (cursor < state.patronEngagementCount) {
            String templateId = rewardFor(state, cursor, workingUnavailable);
            if (templateId != null && !access.grant(templateId)) break;
            if (templateId != null) {
                workingUnavailable.add(templateId);
                granted++;
            }
            cursor++;
            state.patronEquipmentRewardCursor = cursor;
            if (templateId != null) {
                try {
                    int houseRow = state.houseIndex(
                            state.patronEngagementHouseId[cursor - 1]);
                    String patronName = houseRow >= 0
                            ? state.houseDisplayName[houseRow] : null;
                    access.announce(patronName,
                            EquipmentTemplateCatalog.require(templateId));
                } catch (RuntimeException ignored) {
                    // Presentation must never replay a cargo grant.
                }
            }
        }
        return granted;
    }

    private static String rewardFor(CampaignState state, int row,
                                    Set<String> unavailable) {
        ContractType contractType = safeContractType(
                state.patronEngagementContractType[row]);
        if (state.patronEngagementId[row] <= 0L
                || state.patronEngagementSourceContractId[row] <= 0L
                || state.patronEngagementHouseId[row] <= 0L
                || safeOutcome(state.patronEngagementOutcome[row])
                != PatronEngagementOutcome.COMPLETED
                || contractType == null || contractType == ContractType.EXTRACTION) {
            return null;
        }
        int houseRow = state.houseIndex(state.patronEngagementHouseId[row]);
        if (houseRow < 0) return null;
        String factionId = state.factionRegistry.get(state.houseFactionId[houseRow]);
        if (factionId == null) return null;
        long seed = rewardSeed(state.patronEngagementId[row],
                state.patronEngagementSourceContractId[row],
                state.patronEngagementHouseId[row]);
        List<String> selected = FactionEquipmentPicker.pick(factionId,
                FactionEquipmentSource.PATRON, 1, seed, unavailable);
        return selected.isEmpty() ? null : selected.get(0);
    }

    static long rewardSeed(long engagementId, long contractId, long houseId) {
        long hash = 0xcbf29ce484222325L;
        hash = mix(hash, engagementId);
        hash = mix(hash, contractId);
        return mix(hash, houseId);
    }

    private static long mix(long hash, long value) {
        for (int shift = 0; shift < Long.SIZE; shift += Byte.SIZE) {
            hash ^= (value >>> shift) & 0xffL;
            hash *= 0x100000001b3L;
        }
        return hash;
    }

    private static PatronEngagementOutcome safeOutcome(byte value) {
        try {
            return PatronEngagementOutcome.fromByte(value);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static ContractType safeContractType(byte value) {
        try {
            return ContractType.fromByte(value);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static final class StarsectorRewardAccess implements RewardAccess {
        @Override
        public Set<String> unavailableTemplateIds() {
            CargoAPI cargo = playerCargo();
            if (cargo == null) return null;
            Set<String> unavailable = new HashSet<>();
            MarineRosterScript roster = MarineRosterScript.getInstance();
            if (roster != null) {
                unavailable.addAll(roster.roster().armory().ownedEquipmentTemplateIds());
            }
            for (CargoStackAPI stack : cargo.getStacksCopy()) {
                SpecialItemData special = stack.getSpecialDataIfSpecial();
                if (special != null
                        && EquipmentTemplateCardItemPlugin.ITEM_ID.equals(special.getId())
                        && special.getData() != null) {
                    unavailable.add(special.getData());
                }
            }
            return unavailable;
        }

        @Override
        public boolean grant(String templateId) {
            CargoAPI cargo = playerCargo();
            if (cargo == null) return false;
            cargo.addSpecial(EquipmentTemplateCardItemPlugin.itemData(templateId), 1f);
            return true;
        }

        @Override
        public void announce(String patronName, EquipmentTemplateCard template) {
            if (Global.getSector() == null || Global.getSector().getCampaignUI() == null) return;
            String patron = patronName != null && !patronName.isBlank()
                    ? patronName : Strings.get("equipmentTemplateUnknownPatron");
            Global.getSector().getCampaignUI().getMessageDisplay().addMessage(
                    MessageFormat.format(Strings.get("equipmentTemplatePatronRewardFmt"),
                            patron, template.displayName()));
        }

        private static CargoAPI playerCargo() {
            if (Global.getSector() == null) return null;
            CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
            return fleet != null ? fleet.getCargo() : null;
        }
    }
}
