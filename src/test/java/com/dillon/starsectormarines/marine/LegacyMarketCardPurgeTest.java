package com.dillon.starsectormarines.marine;

import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyMarketCardPurgeTest {

    @Test
    void removesEveryMarineCardAndNothingElse() {
        CargoStackAPI template = stack(new SpecialItemData(
                EquipmentTemplateCardItemPlugin.ITEM_ID, "equipment-template:weapon.field-rifle:service"));
        CargoStackAPI loadout = stack(new SpecialItemData(
                LoadoutCardItemPlugin.ITEM_ID, "loadout-card:weapon:weapons:assault"));
        CargoStackAPI pattern = stack(new SpecialItemData(MechPatternLearning.ITEM_ID, "mech-pattern:any"));
        CargoStackAPI unrelated = stack(new SpecialItemData("other_item", "payload"));
        CargoStackAPI plain = stack(null);
        List<CargoStackAPI> removed = new ArrayList<>();
        boolean[] cleaned = {false};
        CargoAPI cargo = proxy(CargoAPI.class, (method, args) -> switch (method) {
            case "getStacksCopy" -> List.of(template, loadout, pattern, unrelated, plain);
            case "removeStack" -> {
                removed.add((CargoStackAPI) args[0]);
                yield null;
            }
            case "removeEmptyStacks" -> {
                cleaned[0] = true;
                yield null;
            }
            default -> null;
        });
        SubmarketAPI submarket = proxy(SubmarketAPI.class, (method, args) ->
                method.equals("getCargo") ? cargo : null);

        assertEquals(3, LegacyMarketCardPurge.purge(submarket));

        assertEquals(List.of(template, loadout, pattern), removed);
        assertTrue(cleaned[0]);
    }

    private static CargoStackAPI stack(SpecialItemData data) {
        return proxy(CargoStackAPI.class, (method, args) ->
                method.equals("getSpecialDataIfSpecial") ? data : null);
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, Invocation invocation) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (proxy, method, args) -> invocation.invoke(method.getName(),
                        args != null ? args : new Object[0]));
    }

    @FunctionalInterface
    private interface Invocation {
        Object invoke(String method, Object[] args) throws Throwable;
    }
}
