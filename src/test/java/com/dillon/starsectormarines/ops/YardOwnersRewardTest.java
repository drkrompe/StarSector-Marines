package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.story.YardOwnersProgress;
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
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class YardOwnersRewardTest {
    @Test void actualEntitlementLearnsThenExplicitlyFitsEightRegularTwoAssaultTwoSupport() throws Exception {
        var p = afterWarrant(); var m = AgreusArcCampaign.offer(p,"Agreus"); p.begin(m.id,"agreus","Agreus");
        var roster = new MarineRoster(); var squad = roster.createSquad(); var people = roster.recruitToSquad(squad.id(),12);
        var names = people.stream().map(person -> person.name()).toList();
        var prior = people.stream().map(person -> person.armorId()).toList();
        EquipmentTemplateLearning.learn(roster.armory(),AgreusArcProgress.SPECTER_CARD);
        assertFalse(roster.armory().knowsArmorPlan(SquadEquipmentDoctrines.SALVAGE_ASSISTED_ARMOR));
        var cards = new ArrayList<SpecialItemData>(); var cargo = cargo(cards); var outcome = victory(m);
        assertEquals(36000,AgreusArcCampaign.settleYard(p,outcome,cargo,Set.of(AgreusArcProgress.SPECTER_CARD)));
        assertEquals(1,cards.size()); assertEquals(EquipmentTemplateCardItemPlugin.ITEM_ID,cards.get(0).getId());
        assertEquals(AgreusArcProgress.ARBALEST_CARD,cards.get(0).getData());
        assertFalse(roster.armory().ownsEquipmentTemplate(AgreusArcProgress.ARBALEST_CARD));
        assertEquals(EquipmentTemplateLearning.Result.LEARNED,EquipmentTemplateLearning.learn(roster.armory(),cards.get(0).getData()));
        assertEquals(prior,people.stream().map(person -> person.armorId()).toList());
        assertTrue(roster.armory().knowsArmorPlan(SquadEquipmentDoctrines.SALVAGE_ASSISTED_ARMOR));
        assertEquals(SquadEquipmentResult.APPLIED,roster.applySquadEquipment(squad.id(),SquadEquipmentDoctrines.FIELD_SECURITY_WEAPONS,SquadEquipmentDoctrines.SALVAGE_ASSISTED_ARMOR));
        assertEquals(2,people.stream().filter(person -> "armor.specter-recovered".equals(person.armorId())).count());
        assertEquals(2,people.stream().filter(person -> "armor.arbalest-recovered".equals(person.armorId())).count());
        assertEquals(names,people.stream().map(person -> person.name()).toList());
        var bytes = new ByteArrayOutputStream();
        try (var out = new ObjectOutputStream(bytes)) { out.writeObject(roster); out.writeObject(p); }
        try (var in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) { roster=(MarineRoster)in.readObject(); p=(AgreusArcProgress)in.readObject(); }
        assertTrue(p.yardCompleted()); assertNull(p.nextMissionId());
        assertTrue(roster.armory().knowsArmorPlan(SquadEquipmentDoctrines.SALVAGE_ASSISTED_ARMOR));
        assertEquals(12,CampaignMarineDeployment.freezeSelection(roster,Set.of(squad.id()),12).size());
        cards.clear(); // Sale/discard never renews an entitlement, including after reload.
        assertEquals(-1,AgreusArcCampaign.settleYard(p,outcome,cargo,Set.of())); assertTrue(cards.isEmpty());
    }
    @Test void unavailableCargoForeignOrIncompleteReceiptsDoNotConsumeAttempt() {
        var p=afterWarrant(); var m=AgreusArcCampaign.offer(p,"Agreus"); p.begin(m.id,"agreus","Agreus");
        var cards=new ArrayList<SpecialItemData>(); var cargo=cargo(cards);
        assertEquals(-1,AgreusArcCampaign.settleYard(p,victory(m),cargo,null));
        assertEquals(-1,AgreusArcCampaign.settleYard(p,victory(m),null,Set.of()));
        assertEquals(-1,AgreusArcCampaign.settleYard(p,victory(Mission.builder(m).source(MissionSource.RECURRING).build()),cargo,Set.of()));
        var incomplete=MissionOutcome.builder().mission(m).victory(true).payoutEarned(36000)
                .yardOwners(new YardOwnersProgress.Report(true,true,false,true,true,false,0,0)).build();
        assertEquals(-1,AgreusArcCampaign.settleYard(p,incomplete,cargo,Set.of())); assertFalse(p.yardCompleted());
        assertEquals(36000,AgreusArcCampaign.settleYard(p,victory(m),cargo,Set.of(AgreusArcProgress.ARBALEST_CARD)));
        assertTrue(cards.isEmpty()); assertTrue(p.yardCompleted());
    }
    @Test void lateSpecterLearningStillEnablesMixedSheetWithoutAnotherReward() {
        var roster=new MarineRoster();
        EquipmentTemplateLearning.learn(roster.armory(),AgreusArcProgress.ARBALEST_CARD);
        assertFalse(roster.armory().knowsArmorPlan(SquadEquipmentDoctrines.SALVAGE_ASSISTED_ARMOR));
        EquipmentTemplateLearning.learn(roster.armory(),AgreusArcProgress.SPECTER_CARD);
        assertTrue(roster.armory().knowsArmorPlan(SquadEquipmentDoctrines.SALVAGE_ASSISTED_ARMOR));
    }
    static MissionOutcome victory(Mission m) {
        return MissionOutcome.builder().mission(m).victory(true).payoutEarned(36000)
                .yardOwners(new YardOwnersProgress.Report(true,true,true,true,true,false,0,0)).build();
    }
    static AgreusArcProgress afterWarrant() {
        var p=new AgreusArcProgress(); String id=p.missionId(); p.begin(id,"agreus");
        p.settle(id,"agreus",true,26000,true,true,true,true,true,true,0,0,false);
        id=p.nextMissionId();p.begin(id,"agreus");p.settleWarrant(id,"agreus",true,30000,true,true,true,true,false,0,0);
        return p;
    }
    static CargoAPI cargo(List<SpecialItemData> cards) {
        return (CargoAPI)Proxy.newProxyInstance(CargoAPI.class.getClassLoader(),new Class<?>[]{CargoAPI.class},(proxy,method,args)->{
            if (method.getName().equals("addSpecial")) { assertEquals(1f,((Number)args[1]).floatValue()); cards.add((SpecialItemData)args[0]); return null; }
            throw new AssertionError("Unexpected cargo call: "+method.getName());
        });
    }
}
