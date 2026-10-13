package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.story.LimitedWarrantProgress;
import com.dillon.starsectormarines.campaign.AgreusArcProgress;
import com.dillon.starsectormarines.marine.EquipmentTemplateCardItemPlugin;
import com.dillon.starsectormarines.marine.EquipmentTemplateLearning;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.SquadEquipmentDoctrines;
import com.dillon.starsectormarines.marine.SquadEquipmentResult;
import com.dillon.starsectormarines.ops.detachment.CampaignMarineDeployment;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.SpecialItemData;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LimitedWarrantRewardTest {
    @Test void victoryDeliversCardOnceAndLearningEnablesAnExplicitNamedRefit() {
        var p = afterWeir(); var m = AgreusArcCampaign.offer(p,"Agreus");
        assertTrue(p.begin(m.id,"agreus","Agreus"));
        var cards = new ArrayList<SpecialItemData>(); var cargo = cargo(cards);
        var outcome = victory(m);
        var roster = new MarineRoster(); var squad = roster.createSquad();
        var people = roster.recruitToSquad(squad.id(),12);
        var names = people.stream().map(person -> person.name()).toList();
        var priorArmor = people.stream().map(person -> person.armorId()).toList();
        assertEquals(30000,AgreusArcCampaign.settleWarrant(p,outcome,cargo,Set.of()));
        assertEquals(1,cards.size()); assertEquals(EquipmentTemplateCardItemPlugin.ITEM_ID,cards.get(0).getId());
        assertEquals(AgreusArcProgress.SPECTER_CARD,cards.get(0).getData());
        assertFalse(roster.armory().ownsEquipmentTemplate(AgreusArcProgress.SPECTER_CARD));
        assertEquals(-1,AgreusArcCampaign.settleWarrant(p,outcome,cargo,Set.of()));
        cards.clear(); // Sold or discarded: reopening the outcome must not replace it.
        assertEquals(-1,AgreusArcCampaign.settleWarrant(p,outcome,cargo,Set.of())); assertTrue(cards.isEmpty());
        assertEquals(EquipmentTemplateLearning.Result.LEARNED,EquipmentTemplateLearning.learn(roster.armory(),AgreusArcProgress.SPECTER_CARD));
        assertEquals(priorArmor,people.stream().map(person -> person.armorId()).toList());
        assertTrue(roster.armory().knowsArmorPlan(SquadEquipmentDoctrines.CORPORATE_HEAVY_ARMOR));
        assertFalse(roster.armory().knowsArmorPlan(SquadEquipmentDoctrines.HEGEMONY_SHOCK_ARMOR));
        assertFalse(roster.armory().knowsArmorPlan(SquadEquipmentDoctrines.SALVAGE_ASSISTED_ARMOR));
        assertEquals(SquadEquipmentResult.APPLIED,roster.applySquadEquipment(squad.id(),
                SquadEquipmentDoctrines.FIELD_SECURITY_WEAPONS,SquadEquipmentDoctrines.CORPORATE_HEAVY_ARMOR));
        assertTrue(people.stream().allMatch(person -> "armor.specter-recovered".equals(person.armorId())));
        assertEquals(names,people.stream().map(person -> person.name()).toList());
        assertEquals(12,CampaignMarineDeployment.freezeSelection(roster,Set.of(squad.id()),12).size());
    }
    @Test void carriedOrLearnedCardSatisfiesEntitlementWithoutAnotherCopy() {
        // The production ownership view unions learned and carried ids before this boundary.
        var p=afterWeir(); var m=AgreusArcCampaign.offer(p,"Agreus"); p.begin(m.id,"agreus","Agreus");
        var cards=new ArrayList<SpecialItemData>();
        assertEquals(30000,AgreusArcCampaign.settleWarrant(p,victory(m),cargo(cards),Set.of(AgreusArcProgress.SPECTER_CARD)));
        assertTrue(cards.isEmpty()); assertTrue(p.warrantCompleted());
    }
    @Test void missingOwnershipViewForeignSourceAndIncompleteResultCannotConsumeReward() {
        var p=afterWeir(); var m=AgreusArcCampaign.offer(p,"Agreus"); p.begin(m.id,"agreus","Agreus");
        var cards=new ArrayList<SpecialItemData>(); var cargo=cargo(cards);
        assertEquals(-1,AgreusArcCampaign.settleWarrant(p,victory(m),cargo,null));
        assertEquals(-1,AgreusArcCampaign.settleWarrant(p,victory(m),null,Set.of()));
        assertEquals(-1,AgreusArcCampaign.settleWarrant(p,victory(Mission.builder(m).source(MissionSource.RECURRING).build()),cargo,Set.of()));
        var incomplete=MissionOutcome.builder().mission(m).victory(true).payoutEarned(30000)
                .limitedWarrant(new LimitedWarrantProgress.Report(true,true,false,false,false,0,0)).build();
        assertEquals(-1,AgreusArcCampaign.settleWarrant(p,incomplete,cargo,Set.of()));
        assertTrue(cards.isEmpty()); assertFalse(p.warrantCompleted());
        assertEquals(30000,AgreusArcCampaign.settleWarrant(p,victory(m),cargo,Set.of()));
    }
    private static MissionOutcome victory(Mission m) {
        return MissionOutcome.builder().mission(m).victory(true).payoutEarned(30000)
                .limitedWarrant(new LimitedWarrantProgress.Report(true,true,true,true,false,0,0)).build();
    }
    private static AgreusArcProgress afterWeir() {
        var p=new AgreusArcProgress(); String id=p.missionId(); p.begin(id,"agreus");
        p.settle(id,"agreus",true,26000,true,true,true,true,true,true,0,0,false); return p;
    }
    private static CargoAPI cargo(List<SpecialItemData> cards) {
        return (CargoAPI)Proxy.newProxyInstance(CargoAPI.class.getClassLoader(),new Class<?>[]{CargoAPI.class},(proxy,method,args)->{
            if (method.getName().equals("addSpecial")) { assertEquals(1f,((Number)args[1]).floatValue()); cards.add((SpecialItemData)args[0]); return null; }
            throw new AssertionError("Unexpected cargo call: " + method.getName());
        });
    }
}
