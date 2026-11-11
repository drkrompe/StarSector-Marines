package com.dillon.starsectormarines.ops.loot;

import com.dillon.starsectormarines.battle.salvage.SalvageCache;
import com.dillon.starsectormarines.battle.salvage.SalvageResource;
import com.dillon.starsectormarines.ops.MissionOutcome;
import com.dillon.starsectormarines.ops.MissionSource;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class FieldSalvageLootTest {
    @Test void securedFindCannotBuyUnrelatedLootAndKeepsOrdinaryCapacityRules() {
        var weapon = new LootStack(new LootCandidate(LootKind.WEAPON,"gun","Gun",null,500,5,1,1,1),1);
        var base = new LootManifest(42,20,100,List.of(weapon));
        var find = new SalvageCache.Recovered("cache", SalvageResource.METALS,40);
        var manifest = FieldSalvageLoot.append(base,List.of(find,find), r ->
                new LootCandidate(LootKind.COMMODITY,r,r,null,30,1,1,1,1));
        assertEquals(2,manifest.stacks.size());
        var selection = new LootSelection(manifest);
        assertTrue(selection.isSelected(1)); assertEquals(0,selection.selectedValue());
        assertFalse(selection.canSelect(0));
        selection.toggle(1);
        assertEquals(100,selection.remainingBudget()); assertFalse(selection.canSelect(0));
        selection.toggle(1);
        var plan = LootSettlementPlanner.plan(selection,new LootCapacitySnapshot(15,0,0));
        assertEquals(15,plan.keptUnits); assertEquals(25,plan.fencedUnits);
        assertEquals(562,plan.fencedCredits);
        assertEquals("metals",plan.lines.get(0).stack.itemId);
    }
    @Test void aTechCacheIsNoCommodityStack() {
        var tech = new SalvageCache.Recovered("sealed-store-1", SalvageResource.MACHINERY, 8, true);
        var manifest = FieldSalvageLoot.append(LootManifest.EMPTY, List.of(tech), r ->
                new LootCandidate(LootKind.COMMODITY,r,r,null,30,1,1,1,1));
        assertTrue(manifest.stacks.isEmpty());
    }
    @Test void defeatAndDebugCannotTurnSecuredFindsIntoCampaignLoot() {
        var finds = List.of(new SalvageCache.Recovered("cache",SalvageResource.MACHINERY,8));
        assertTrue(LootGenerator.generate(MissionOutcome.builder().victory(false).fieldSalvage(finds).build()).isEmpty());
        assertTrue(LootGenerator.generate(MissionOutcome.builder().victory(true)
                .missionSource(MissionSource.DEBUG).fieldSalvage(finds).build()).isEmpty());
    }

    @Test void aStoreFindSettlesAsItsOwnGoods() {
        var find = new SalvageCache("store-find-0", 3, 4, SalvageResource.METALS, 6)
                .ofGoods("rare_metals", "Transplutonics").recovered();
        assertEquals("rare_metals", find.commodity());
        assertEquals("6 Transplutonics", find.label());
        var manifest = FieldSalvageLoot.append(LootManifest.EMPTY, List.of(find), id ->
                new LootCandidate(LootKind.COMMODITY,id,id,null,200,1,1,1,1));
        assertEquals("rare_metals", manifest.stacks.get(0).itemId);
        assertEquals(6, manifest.stacks.get(0).quantity);
    }
}
