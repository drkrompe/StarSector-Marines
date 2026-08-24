package com.dillon.starsectormarines.battle.turret;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DefensePostLayoutRegistryTest {

    @Test
    void builtinsCoverEveryShippedLayoutAndStructureComposition() {
        DefensePostLayoutRegistry registry = DefensePostLayoutRegistry.requireInstalled();
        assertEquals(9, registry.size());
        assertEquals(5, registry.layoutsFor(DefensePostKind.LARGE).size());
        assertLayout(registry.require(DefensePostKind.LIGHT, "default"),
                5, "structure.turret-vulcan");
        assertLayout(registry.require(DefensePostKind.MEDIUM, "default"),
                9, "structure.turret-arbalest");
        assertLayout(registry.require(DefensePostKind.LARGE, "line-h"),
                15, "structure.turret-hephaestus", "structure.turret-hephaestus");
        assertLayout(registry.require(DefensePostKind.LARGE, "line-v"),
                15, "structure.turret-hephaestus", "structure.turret-hephaestus");
        assertLayout(registry.require(DefensePostKind.LARGE, "wedge"),
                9, "structure.turret-hephaestus");
        assertLayout(registry.require(DefensePostKind.LARGE, "trapezoid"),
                13, "structure.turret-hephaestus", "structure.turret-hephaestus");
        assertLayout(registry.require(DefensePostKind.LARGE, "triangle-formation"),
                15, "structure.turret-hephaestus", "structure.turret-hephaestus",
                "structure.turret-hephaestus");
        assertLayout(registry.require(DefensePostKind.ARTILLERY, "default"),
                15, "structure.turret-locust", "structure.turret-locust");

        DefensePostLayoutDef hub = registry.require(DefensePostKind.DRONE_HUB, "default");
        assertEquals(9, hub.cells.size());
        assertEquals(0, hub.turrets.size());
        assertEquals(new DefensePostLayoutDef.Offset(0, 0), hub.droneHubOffset());
    }

    @Test
    void parserRejectsUnknownStructureDuplicateOffsetsAndTurretOffPad() throws Exception {
        TurretCatalogRegistry turrets = TurretCatalogRegistry.installed();
        assertNotNull(turrets);
        assertThrows(JSONException.class, () -> new DefensePostLayoutRegistry().ingest(
                catalog("structure.missing", false, false), turrets));
        assertThrows(JSONException.class, () -> new DefensePostLayoutRegistry().ingest(
                catalog("structure.turret-vulcan", true, false), turrets));
        assertThrows(JSONException.class, () -> new DefensePostLayoutRegistry().ingest(
                catalog("structure.turret-vulcan", false, true), turrets));
    }

    @Test
    void diskCatalogRoundTripsThroughThePublicParser() throws Exception {
        DefensePostLayoutRegistry parsed = new DefensePostLayoutRegistry();
        for (String path : DefensePostLayoutRegistry.BUILTIN_CATALOGS) {
            parsed.ingest(new JSONObject(Files.readString(Path.of("mod", path))),
                    TurretCatalogRegistry.installed());
        }
        parsed.validateCompleteness();
        assertEquals(DefensePostLayoutRegistry.requireInstalled().size(), parsed.size());
    }

    private static void assertLayout(DefensePostLayoutDef layout, int cells,
                                     String... structures) {
        assertEquals(cells, layout.cells.size(), layout.id);
        assertEquals(List.of(structures),
                layout.turrets.stream().map(DefensePostLayoutDef.TurretPlacement::structureId)
                        .toList(), layout.id);
    }

    private static JSONObject catalog(String structureId, boolean duplicateCell,
                                      boolean turretOffPad) throws JSONException {
        String extra = duplicateCell
                ? ", {\"offset\":[0,0],\"kind\":\"pad\"}" : "";
        String turretOffset = turretOffPad ? "[1,0]" : "[0,0]";
        return new JSONObject("""
                {"layouts":[{
                  "id":"layout.test", "tier":"light", "variant":"test",
                  "anchor":[0,0],
                  "bounds":{"minOffset":[-1,-1],"maxOffset":[1,1]},
                  "cells":[
                    {"offset":[0,0],"kind":"pad"},
                    {"offset":[1,0],"kind":"barrier","appearance":"vent","facing":[1,0]}
                    %s
                  ],
                  "turrets":[{"offset":%s,"structure":"%s"}]
                }]}
                """.formatted(extra, turretOffset, structureId));
    }
}
