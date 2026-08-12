package com.dillon.starsectormarines.ops.loot;

import com.dillon.starsectormarines.ops.MarineOpsContext;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import org.apache.log4j.Logger;

/** Live-campaign adapter for previewing and exactly-once applying settlement. */
public final class LootSettlementService {

    private static final Logger LOG = Global.getLogger(LootSettlementService.class);

    private LootSettlementService() {}

    public static LootSettlementPlan preview(LootSelection selection) {
        CargoAPI cargo = playerCargo();
        if (cargo == null) return null;
        return LootSettlementPlanner.plan(selection, capacityOf(cargo));
    }

    /**
     * Applies one settlement. Returns the prior receipt on a duplicate call,
     * or {@code null} when campaign cargo is unavailable and nothing changed.
     */
    public static LootSettlementPlan settle(MarineOpsContext ctx, LootSelection selection) {
        if (ctx == null || selection == null) return null;
        if (ctx.isLootSettlementStarted()) return ctx.getLootSettlement();

        CargoAPI cargo = playerCargo();
        if (cargo == null) return null;
        LootSettlementPlan plan = LootSettlementPlanner.plan(selection, capacityOf(cargo));
        if (!ctx.tryBeginLootSettlement()) return ctx.getLootSettlement();

        try {
            for (LootSettlementLine line : plan.lines) {
                addKept(cargo, line);
            }
            if (plan.fencedCredits > 0) cargo.getCredits().add(plan.fencedCredits);
        } catch (RuntimeException ex) {
            // The gate remains closed after a partial failure: retrying would
            // duplicate every line that already made it into cargo.
            LOG.error("Loot settlement failed after confirmation; retry suppressed", ex);
        } finally {
            ctx.completeLootSettlement(plan);
        }
        return plan;
    }

    private static LootCapacitySnapshot capacityOf(CargoAPI cargo) {
        return new LootCapacitySnapshot(cargo.getSpaceLeft(),
                cargo.getFreeFuelSpace(), cargo.getFreeCrewSpace());
    }

    private static void addKept(CargoAPI cargo, LootSettlementLine line) {
        int quantity = line.keptQuantity;
        if (quantity <= 0) return;
        if (line.stack.kind == LootKind.WEAPON) {
            cargo.addWeapons(line.stack.itemId, quantity);
            return;
        }
        switch (line.bucket) {
            case FUEL:
                cargo.addFuel(quantity);
                break;
            case PERSONNEL:
                cargo.addMarines(quantity);
                break;
            case CARGO:
            default:
                cargo.addCommodity(line.stack.itemId, quantity);
                break;
        }
    }

    private static CargoAPI playerCargo() {
        if (Global.getSector() == null) return null;
        CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
        return fleet != null ? fleet.getCargo() : null;
    }
}
