package com.dillon.starsectormarines.battle.turret;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DefensePostLayoutRegistryTest {

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

    @Test
    void multiCellMountRequiresItsWholePadAndCannotOverlapAnotherMount() throws Exception {
        TurretCatalogRegistry turrets = TurretCatalogRegistry.installed();
        JSONObject root = new JSONObject(Files.readString(Path.of("mod",
                DefensePostLayoutRegistry.BUILTIN_CATALOGS.get(0))));
        JSONObject tower = null;
        for (int i = 0; i < root.getJSONArray("layouts").length(); i++) {
            JSONObject candidate = root.getJSONArray("layouts").getJSONObject(i);
            if (candidate.getString("variant").equals("aa-locust")) tower = candidate;
        }
        assertNotNull(tower);
        JSONObject missingPad = new JSONObject(tower.toString());
        // The shifted turret still has a legal center but its footprint reaches the ring.
        missingPad.getJSONArray("turrets").getJSONObject(0)
                .put("offset", new JSONArray("[1,0]"));
        assertThrows(JSONException.class, () -> DefensePostLayoutDef.parse(missingPad, turrets));

        JSONObject overlap = new JSONObject(tower.toString());
        overlap.getJSONArray("turrets").put(new JSONObject(
                "{'offset':[1,0], 'structure':'structure.turret-vulcan'}"));
        assertThrows(JSONException.class, () -> DefensePostLayoutDef.parse(overlap, turrets));

        JSONObject missingCorner = new JSONObject(tower.toString());
        JSONArray retained = new JSONArray();
        JSONArray cells = missingCorner.getJSONArray("cells");
        for (int i = 0; i < cells.length(); i++) {
            if (i != 6) retained.put(cells.getJSONObject(i)); // the pad's northwest corner
        }
        missingCorner.put("cells", retained);
        assertThrows(JSONException.class, () -> DefensePostLayoutDef.parse(missingCorner, turrets));
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
