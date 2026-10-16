package com.dillon.starsectormarines.battle.air.engine;

import com.dillon.starsectormarines.testsupport.InstalledHullSpecs;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class EngineSlotResolverSourceTest {
    private static final String SPEC="{\"width\":20,\"height\":40,\"center\":[10,10],\"engineSlots\":[{\"location\":[-10,4],\"length\":8,\"width\":4}]}";
    @AfterEach void restoreInstall() {
        EngineSlotResolver.useHullSpecs(null);
        InstalledHullSpecs.install();
    }
    @Test void sourceUsesSameNativeSlotTransformAndCacheIsResetOnSwap() throws Exception {
        JSONObject spec=new JSONObject(SPEC);
        EngineSlotResolver.useHullSpecs(id -> spec);
        var slots=EngineSlotResolver.resolve("test-engine-source",4);
        assertEquals(1,slots.length);
        assertEquals(-.4f,slots[0].localX,.00001f);
        assertEquals(-1f,slots[0].localY,.00001f);
        assertEquals(.8f,slots[0].lengthCells,.00001f);
        EngineSlotResolver.useHullSpecs(id -> null);
        assertEquals(0,EngineSlotResolver.resolve("test-engine-source",4).length);
        EngineSlotResolver.useHullSpecs(id -> spec);
        assertEquals(1,EngineSlotResolver.resolve("test-engine-source",4).length);
        EngineSlotResolver.useHullSpecs(null);
        assertEquals(0,EngineSlotResolver.resolve("test-engine-source",4).length);
    }
    @Test void missingAndMalformedSourcesKeepEmptyFallback() throws Exception {
        EngineSlotResolver.useHullSpecs(id -> {throw new IllegalStateException("unavailable");});
        assertEquals(0,EngineSlotResolver.resolve("test-engine-throwing",4).length);
        JSONObject malformed=new JSONObject("{\"engineSlots\":[{}]}");
        EngineSlotResolver.useHullSpecs(id -> malformed);
        assertEquals(0,EngineSlotResolver.resolve("test-engine-malformed",4).length);
        assertEquals(0,EngineSlotResolver.resolve((String)null,4).length);
    }
    @Test void installedHullSpecsFeedNativeEngineSlots(@TempDir Path core) throws Exception {
        Path hulls=Files.createDirectories(core.resolve("data/hulls"));
        Files.writeString(hulls.resolve("test-disk-engine.ship"),SPEC);
        InstalledHullSpecs.install(core);
        assertEquals(1,EngineSlotResolver.resolve("test-disk-engine",4).length);
    }
}
