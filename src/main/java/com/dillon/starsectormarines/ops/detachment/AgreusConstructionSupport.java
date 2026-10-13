package com.dillon.starsectormarines.ops.detachment;

import com.dillon.starsectormarines.battle.power.CommandPower;
import com.dillon.starsectormarines.battle.power.ShipOnStation;
import com.dillon.starsectormarines.battle.warship.ShipSupportSource;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.Global;

import java.util.Collection;
import java.util.List;

/** North Weir permits working company tenders, with ordinary call costs and ship settlement. */
public final class AgreusConstructionSupport {
    private AgreusConstructionSupport() { }

    public static List<ShipSupportSource> sources(List<FleetMemberAPI> committed) {
        if (committed == null) return List.of();
        return committed.stream().filter(AgreusConstructionSupport::operational)
                .map(PowerCatalog::shipSourceOf).filter(AgreusConstructionSupport::ownedTender).toList();
    }

    private static boolean operational(FleetMemberAPI member) {
        return member != null && !member.isFighterWing() && !member.isMothballed()
                && member.getStatus() != null && member.getStatus().getHullFraction() > 0f
                && member.getRepairTracker() != null && member.getRepairTracker().getCR() > 0f;
    }

    public static boolean ownedTender(ShipSupportSource source) {
        return source != null && source.tender() && source.memberId() != null && !source.memberId().isBlank()
                && ShipSupportSource.TENDER_HULL_ID.equals(source.hullId());
    }

    public static boolean selected(List<CommandPower> powers) {
        return powers != null && powers.stream().anyMatch(power -> power instanceof ShipOnStation ship
                && ship.sources().stream().anyMatch(AgreusConstructionSupport::ownedTender));
    }

    /** Recheck the prepared briefing against the actual fleet at the moment of launch. */
    public static void requireLive(List<CommandPower> powers, float supplies) {
        if (!selected(powers))
            throw new IllegalStateException("Commit a working company Shepherd and select Ship on Station in Support.");
        var fleet = Global.getSector() == null ? null : Global.getSector().getPlayerFleet();
        var live = sources(fleet == null || fleet.getFleetData() == null ? List.of()
                : fleet.getFleetData().getMembersListCopy());
        for (var power : powers) if (power instanceof ShipOnStation ship) {
            for (var source : ship.sources()) if (!ownedTender(source)
                    || live.stream().noneMatch(current -> current.equals(source)))
                throw new IllegalStateException("The committed tender has changed or is unavailable. Review the support commitment.");
        }
        if (supplies < ShipOnStation.SUPPLY_COST)
            throw new IllegalStateException("Carry at least " + ShipOnStation.SUPPLY_COST + " supplies to call the construction tender.");
    }

    public static String blocker(List<CommandPower> available, Collection<String> selected, float supplies) {
        if (!selected(CommandDeck.filter(available, selected)))
            return "Commit a working company Shepherd and select Ship on Station in Support.";
        if (supplies < ShipOnStation.SUPPLY_COST)
            return "Carry at least " + ShipOnStation.SUPPLY_COST + " supplies to call the construction tender.";
        return null;
    }
}
