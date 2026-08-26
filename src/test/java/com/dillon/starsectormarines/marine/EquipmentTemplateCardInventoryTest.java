package com.dillon.starsectormarines.marine;

import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.SpecialItemData;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EquipmentTemplateCardInventoryTest {

    @Test
    void carriedScanIncludesOnlyParameterizedMarineTemplateCards() {
        String templateId = "equipment-template:weapon.field-rifle:milspec";
        CargoStackAPI marineCard = stack(new SpecialItemData(
                EquipmentTemplateCardItemPlugin.ITEM_ID, templateId));
        CargoStackAPI otherSpecial = stack(new SpecialItemData("other", "payload"));
        CargoStackAPI ordinaryCargo = stack(null);
        CargoAPI cargo = proxy(CargoAPI.class, (method, returnType, args) ->
                method.equals("getStacksCopy")
                        ? List.of(marineCard, otherSpecial, ordinaryCargo)
                        : defaultValue(returnType));

        assertEquals(List.of(templateId),
                EquipmentTemplateCardInventory.carriedTemplateIds(cargo).stream().toList());
    }

    private static CargoStackAPI stack(SpecialItemData special) {
        return proxy(CargoStackAPI.class, (method, returnType, args) ->
                method.equals("getSpecialDataIfSpecial")
                        ? special : defaultValue(returnType));
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, Invocation invocation) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (proxy, method, args) -> invocation.invoke(method.getName(),
                        method.getReturnType(), args != null ? args : new Object[0]));
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0f;
        if (type == double.class) return 0d;
        if (type == char.class) return '\0';
        return null;
    }

    @FunctionalInterface
    private interface Invocation {
        Object invoke(String method, Class<?> returnType, Object[] args) throws Throwable;
    }
}
