package com.dillon.starsectormarines.campaign.systems;

import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.CampaignSystem;
import com.dillon.starsectormarines.campaign.CampaignTable;
import com.dillon.starsectormarines.campaign.ContractType;
import com.dillon.starsectormarines.campaign.PatronEngagementOutcome;
import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.marine.EquipmentAcquisitionEligibility;
import com.dillon.starsectormarines.marine.MilitaryWarrants;
import com.dillon.starsectormarines.marine.FactionEquipmentSource;
import com.dillon.starsectormarines.marine.LoadoutCard;
import com.dillon.starsectormarines.marine.LoadoutCardInventory;
import com.dillon.starsectormarines.marine.LoadoutCardItemPlugin;
import com.dillon.starsectormarines.marine.LoadoutCardPicker;
import com.dillon.starsectormarines.marine.MarineRosterScript;
import com.dillon.starsectormarines.marine.MechPattern;
import com.dillon.starsectormarines.marine.MechPatternInventory;
import com.dillon.starsectormarines.marine.MechPatternPicker;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CargoAPI;

import java.text.MessageFormat;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Delivers one faction-authored reward card for each completed patron contract: ordinarily a
 * loadout card, and on one contract in {@link #MECH_PATTERN_ODDS} a mech pattern card instead.
 * A weapon pattern is awarded only to a company whose bay holds a mech; a chassis pattern may
 * go to any company. When no pattern qualifies the contract pays its loadout card as before.
 */
public final class PatronEquipmentRewardSystem implements CampaignSystem {

    interface RewardAccess {
        /** Null means cargo is temporarily unavailable and processing must retry. */
        Set<String> unavailableTemplateIds();
        int victories();
        boolean grant(String templateId);
        void announce(String patronName, LoadoutCard card);

        /**
         * Mech patterns no reward should repeat. Null means this access awards no patterns;
         * the loadout card is paid instead.
         */
        default Set<String> unavailablePatternIds() { return null; }
        /** Whether the company's bay holds at least one mech. */
        default boolean fieldsMechs() { return false; }
        default boolean grantPattern(String patternId) { return false; }
        default void announcePattern(String patronName, MechPattern pattern) {}
    }

    /** One completed contract in this many pays a mech pattern card when one qualifies. */
    static final int MECH_PATTERN_ODDS = 4;

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
        Set<String> patterns = access.unavailablePatternIds();
        Set<String> workingPatterns = patterns != null ? new HashSet<>(patterns) : null;
        boolean fieldsMechs = access.fieldsMechs();
        int cursor = Math.max(0, Math.min(
                state.patronEquipmentRewardCursor, state.patronEngagementCount));
        state.patronEquipmentRewardCursor = cursor;
        int granted = 0;
        while (cursor < state.patronEngagementCount) {
            Reward reward = rewardFor(state, cursor, workingUnavailable,
                    workingPatterns, fieldsMechs, access.victories());
            if (reward != null && !(reward.pattern()
                    ? access.grantPattern(reward.id()) : access.grant(reward.id()))) {
                break;
            }
            if (reward != null) {
                (reward.pattern() ? workingPatterns : workingUnavailable).add(reward.id());
                granted++;
            }
            cursor++;
            state.patronEquipmentRewardCursor = cursor;
            if (reward != null) {
                try {
                    int houseRow = state.houseIndex(
                            state.patronEngagementHouseId[cursor - 1]);
                    String patronName = houseRow >= 0
                            ? state.houseDisplayName[houseRow] : null;
                    if (reward.pattern()) {
                        access.announcePattern(patronName, MechPattern.parse(reward.id()));
                    } else {
                        access.announce(patronName, LoadoutCard.parse(reward.id()));
                    }
                } catch (RuntimeException ignored) {
                    // Presentation must never replay a cargo grant.
                }
            }
        }
        return granted;
    }

    private static Reward rewardFor(CampaignState state, int row,
                                    Set<String> unavailable, Set<String> unavailablePatterns,
                                    boolean fieldsMechs, int victories) {
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
        EquipmentAcquisitionEligibility.Progress progress =
                new EquipmentAcquisitionEligibility.Progress(
                        victories, state.playerMrbRep,
                        MilitaryWarrants.warrantedCardIds(state));
        if (unavailablePatterns != null && paysMechPattern(seed)) {
            List<String> pattern = MechPatternPicker.pick(factionId,
                    FactionEquipmentSource.PATRON, 1, seed, unavailablePatterns, progress,
                    fieldsMechs ? EnumSet.allOf(MechPattern.Kind.class)
                            : EnumSet.of(MechPattern.Kind.CHASSIS, MechPattern.Kind.VEHICLE));
            if (!pattern.isEmpty()) return new Reward(pattern.get(0), true);
        }
        List<String> selected = LoadoutCardPicker.pick(factionId,
                FactionEquipmentSource.PATRON, 1, seed, unavailable, progress);
        return selected.isEmpty() ? null : new Reward(selected.get(0), false);
    }

    /** Whether a contract's reward seed draws a mech pattern; fixed for the ledger row. */
    static boolean paysMechPattern(long seed) {
        return Math.floorMod(Long.rotateRight(seed, 17), (long) MECH_PATTERN_ODDS) == 0L;
    }

    private record Reward(String id, boolean pattern) {}

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
            return LoadoutCardInventory.playerUnavailableCardIds();
        }

        @Override
        public int victories() {
            MarineRosterScript script = MarineRosterScript.getInstance();
            return script != null ? script.roster().armory().victories() : 0;
        }

        @Override
        public boolean grant(String templateId) {
            CargoAPI cargo = playerCargo();
            if (cargo == null) return false;
            cargo.addSpecial(LoadoutCardItemPlugin.itemData(templateId), 1f);
            return true;
        }

        @Override
        public void announce(String patronName, LoadoutCard card) {
            if (Global.getSector() == null || Global.getSector().getCampaignUI() == null) return;
            String patron = patronName != null && !patronName.isBlank()
                    ? patronName : Strings.get("loadoutCardUnknownPatron");
            Global.getSector().getCampaignUI().getMessageDisplay().addMessage(
                    MessageFormat.format(Strings.get("loadoutCardPatronRewardFmt"),
                            patron, card.displayName()));
        }

        @Override
        public Set<String> unavailablePatternIds() {
            return MechPatternInventory.playerUnavailablePatternIds();
        }

        @Override
        public boolean fieldsMechs() {
            return MechPatternInventory.fieldsMechs(MechPatternInventory.playerBay());
        }

        @Override
        public boolean grantPattern(String patternId) {
            CargoAPI cargo = playerCargo();
            if (cargo == null) return false;
            cargo.addSpecial(MechPatternInventory.itemData(patternId), 1f);
            return true;
        }

        @Override
        public void announcePattern(String patronName, MechPattern pattern) {
            if (pattern == null || Global.getSector() == null
                    || Global.getSector().getCampaignUI() == null) return;
            String patron = patronName != null && !patronName.isBlank()
                    ? patronName : Strings.get("loadoutCardUnknownPatron");
            Global.getSector().getCampaignUI().getMessageDisplay().addMessage(
                    MessageFormat.format(Strings.get("mechPatternCardPatronRewardFmt"),
                            patron, pattern.displayName()));
        }

        private static CargoAPI playerCargo() {
            if (Global.getSector() == null) return null;
            CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
            return fleet != null ? fleet.getCargo() : null;
        }
    }
}
