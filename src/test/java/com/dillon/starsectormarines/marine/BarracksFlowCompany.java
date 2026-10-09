package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.campaign.CommodityPresentation;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Test-only company for a continuous UI walkthrough using the real personnel transaction. */
public final class BarracksFlowCompany implements EquipmentIssueResources {
    public final MarineRoster roster = new MarineRoster();
    public final List<MarineSquad> squads;
    public final MarineCaptain captain;
    private final CommodityPresentation presentation;
    private final Map<String, Float> cargo = new HashMap<>();
    private final CargoAPI cargoApi;

    public BarracksFlowCompany(CommodityPresentation presentation) {
        this.presentation = presentation;
        roster.bootstrapInitialComplement(36);
        squads = roster.squads().stream().filter(s -> !s.reserve()).toList();
        captain = new MarineCaptain("Tamsin Vale", "graphics/portraits/portrait_hegemony06.png", Rank.MAJOR, 0f);
        roster.add(captain);
        squads.forEach(s -> roster.assignCaptainToSquad(captain.id(), s.id()));
        roster.applySoldierOutcome(Map.of(
                squads.get(0).memberIds().get(0), MarineSoldierStatus.KIA,
                squads.get(0).memberIds().get(1), MarineSoldierStatus.KIA,
                squads.get(1).memberIds().get(0), MarineSoldierStatus.KIA,
                squads.get(1).memberIds().get(1), MarineSoldierStatus.KIA,
                squads.get(2).memberIds().get(0), MarineSoldierStatus.WIA), 100f, 20f);
        roster.recruitToSquad(roster.reserveSquad().id());
        cargo.put(Commodities.MARINES, 20f);
        for (String id : List.of(Commodities.SUPPLIES, Commodities.HAND_WEAPONS, Commodities.HEAVY_MACHINERY, Commodities.FOOD)) {
            cargo.put(id, 200f);
        }
        cargoApi = (CargoAPI) Proxy.newProxyInstance(CargoAPI.class.getClassLoader(), new Class<?>[]{CargoAPI.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getCommodityQuantity" -> cargo.getOrDefault((String) args[0], 0f);
                    case "removeCommodity" -> { cargo.compute((String) args[0], (key, amount) -> amount - ((Number) args[1]).floatValue()); yield null; }
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }
    public int recruits() { return cargo.get(Commodities.MARINES).intValue(); }
    public MarinePersonnelLogistics.PersonnelDrawResult reinforce(String id) {
        return MarinePersonnelLogistics.reinforceSquad(roster, id, cargoApi);
    }
    @Override public String commodityIcon(String id) { return presentation.commodityIcon(id); }
    @Override public String commodityName(String id) { return presentation.commodityName(id); }
    @Override public EquipmentTemplateCost available() {
        return new EquipmentTemplateCost(cargo.get(Commodities.SUPPLIES).intValue(),
                cargo.get(Commodities.HAND_WEAPONS).intValue(), cargo.get(Commodities.HEAVY_MACHINERY).intValue(),
                cargo.get(Commodities.FOOD).intValue());
    }
    @Override public boolean spend(EquipmentTemplateCost cost) {
        if (!canAfford(cost)) return false;
        cargo.compute(Commodities.SUPPLIES, (k, v) -> v - cost.supplies());
        cargo.compute(Commodities.HAND_WEAPONS, (k, v) -> v - cost.heavyArmaments());
        cargo.compute(Commodities.HEAVY_MACHINERY, (k, v) -> v - cost.heavyMachinery());
        cargo.compute(Commodities.FOOD, (k, v) -> v - cost.food());
        return true;
    }
}
