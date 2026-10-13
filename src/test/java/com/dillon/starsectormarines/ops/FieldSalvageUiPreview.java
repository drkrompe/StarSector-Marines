package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.salvage.SalvageCache;
import com.dillon.starsectormarines.ops.loot.FieldSalvageLoot;
import com.dillon.starsectormarines.ops.loot.LootCandidate;
import com.dillon.starsectormarines.ops.loot.LootKind;
import com.dillon.starsectormarines.ops.loot.LootManifest;
import com.dillon.starsectormarines.ops.loot.LootSelection;
import com.dillon.starsectormarines.ops.loot.LootSettlementPlanner;
import com.dillon.starsectormarines.ops.loot.LootCapacitySnapshot;
import com.dillon.starsectormarines.testsupport.HeadlessSvgAssets;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.ui.retained.headless.HeadlessUiRenderer;
import java.awt.image.BufferedImage;
import java.util.List;

/** Exact live row projection and capacity planner for the scene's secured finds. */
public final class FieldSalvageUiPreview {
    private FieldSalvageUiPreview() { }
    public static BufferedImage render(SnapshotContext context, List<SalvageCache.Recovered> finds) throws Exception {
        HeadlessSvgAssets.install(context.modRoot());
        var manifest = FieldSalvageLoot.append(LootManifest.EMPTY, finds, r -> new LootCandidate(
                LootKind.COMMODITY, r.commodityId, r.displayName,
                "graphics/icons/cargo/" + switch (r) {
                    case METALS -> "materials";
                    case MACHINERY -> "heavymachinery";
                    case SUPPLIES -> "supplies";
                } + ".png",
                switch (r) { case METALS -> 30; case MACHINERY -> 150; case SUPPLIES -> 100; },
                1, 1, 1, 1));
        var selection = new LootSelection(manifest);
        var plan = LootSettlementPlanner.plan(selection,new LootCapacitySnapshot(100,0,0));
        var props = LootScreen.previewProps();
        props.put("missionName","Thunder Run / secured exploration salvage");
        props.put("budgetLabel","Contract claim: Cr. 0 / Cr. 0");
        props.put("poolLabel",manifest.stacks.size() + " secured field find");
        props.put("instructions","Field finds are preselected and do not spend your contract claim budget. Confirm to carry them; cargo overflow is fenced.");
        props.put("lootRows",LootScreen.lootRows(selection, i -> {}, ModStrings.fromDisk(context.modRoot())));
        props.put("carryLabel",plan.keptUnits + " units / Cr. " + plan.keptValue + " value");
        props.put("fenceLabel",plan.fencedUnits + " units / Cr. " + plan.fencedCredits + " paid");
        props.put("notice","Secured finds are ready to transfer to fleet cargo.");
        var renderer = new HeadlessUiRenderer(context.modRoot(),context.starsectorCore());
        return UiSnapshotSuite.renderMissionFlow(context,renderer,LootScreen.COMPONENT_PATHS,LootScreen.ROOT_COMPONENT,props);
    }
}
