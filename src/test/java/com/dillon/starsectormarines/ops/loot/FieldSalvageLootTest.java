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
                new LootCandidate(LootKind.COMMODITY,r.commodityId,r.displayName,null,30,1,1,1,1));
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
    @Test void defeatAndDebugCannotTurnSecuredFindsIntoCampaignLoot() {
        var finds = List.of(new SalvageCache.Recovered("cache",SalvageResource.MACHINERY,8));
        assertTrue(LootGenerator.generate(MissionOutcome.builder().victory(false).fieldSalvage(finds).build()).isEmpty());
        assertTrue(LootGenerator.generate(MissionOutcome.builder().victory(true)
                .missionSource(MissionSource.DEBUG).fieldSalvage(finds).build()).isEmpty());
    }
}
