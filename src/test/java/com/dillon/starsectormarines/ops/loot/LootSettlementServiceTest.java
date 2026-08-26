package com.dillon.starsectormarines.ops.loot;

import com.dillon.starsectormarines.marine.EquipmentTemplateCardItemPlugin;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.SpecialItemData;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LootSettlementServiceTest {

    @Test
    void selectedTemplateSettlesAsParameterizedSpecialCargo() {
        String templateId = "equipment-template:weapon.field-rifle:milspec";
        LootCandidate candidate = new LootCandidate(LootKind.SPECIAL, templateId,
                "Rook Milspec", null, 5_000, 1f, 1f, 1, 1);
        LootStack stack = new LootStack(candidate, 1);
        LootSettlementLine line = new LootSettlementLine(0, stack,
                LootCapacityBucket.CARGO, 1, 0, 0);
        List<SpecialItemData> added = new ArrayList<>();
        CargoAPI cargo = proxy(CargoAPI.class, (method, returnType, args) -> {
            if (method.equals("addSpecial")) {
                added.add((SpecialItemData) args[0]);
                assertEquals(1f, (float) args[1]);
            }
            return defaultValue(returnType);
        });

        LootSettlementService.addKept(cargo, line);

        assertEquals(1, added.size());
        assertEquals(EquipmentTemplateCardItemPlugin.ITEM_ID, added.get(0).getId());
        assertEquals(templateId, added.get(0).getData());
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
