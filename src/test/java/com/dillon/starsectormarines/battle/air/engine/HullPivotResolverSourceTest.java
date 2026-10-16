package com.dillon.starsectormarines.battle.air.engine;

import com.dillon.starsectormarines.battle.air.AirScale;
import com.dillon.starsectormarines.testsupport.InstalledHullSpecs;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class HullPivotResolverSourceTest {
    @AfterEach void restoreInstall() {
        HullPivotResolver.useHullGeometry(null);
        InstalledHullSpecs.install();
    }

    @Test void authoredPixelCenterIsConvertedToCells() {
        HullPivotResolver.useHullGeometry(id -> new HullPivotResolver.HullGeometry(24, 30, 13, 10));
        float k=AirScale.METERS_PER_PX/AirScale.METERS_PER_CELL;
        assertArrayEquals(new float[]{-k,5*k},HullPivotResolver.pivotOffset("test-pivot"),0.00001f);
    }

    @Test void missingInvalidOrThrowingSourcesKeepZeroFallback() {
        HullPivotResolver.useHullGeometry(id -> null);
        assertArrayEquals(new float[]{0,0},HullPivotResolver.pivotOffset("test-missing"));
        assertArrayEquals(new float[]{0,0},HullPivotResolver.pivotOffset(null));
        HullPivotResolver.useHullGeometry(id -> new HullPivotResolver.HullGeometry(0,30,13,10));
        assertArrayEquals(new float[]{0,0},HullPivotResolver.pivotOffset("test-invalid"));
        HullPivotResolver.useHullGeometry(id -> {throw new IllegalStateException("unavailable");});
        assertArrayEquals(new float[]{0,0},HullPivotResolver.pivotOffset("test-throwing"));
    }

    @Test void swappingOrRemovingSourceInvalidatesCachedAnswer() {
        HullPivotResolver.useHullGeometry(id -> null);
        assertArrayEquals(new float[]{0,0},HullPivotResolver.pivotOffset("test-swap"));
        HullPivotResolver.useHullGeometry(id -> new HullPivotResolver.HullGeometry(20,40,10,10));
        float k=AirScale.METERS_PER_PX/AirScale.METERS_PER_CELL;
        assertArrayEquals(new float[]{0,10*k},HullPivotResolver.pivotOffset("test-swap"),0.00001f);
        HullPivotResolver.useHullGeometry(null);
        assertArrayEquals(new float[]{0,0},HullPivotResolver.pivotOffset("test-swap"));
    }

    @Test void installedSpecsFeedNativeWidthHeightAndBottomOriginCenter(@TempDir Path core) throws Exception {
        Path hulls=Files.createDirectories(core.resolve("data/hulls"));
        Files.writeString(hulls.resolve("test-disk-pivot.ship"),
                "{\"width\":24,\"height\":30,\"center\":[13,10]}");
        InstalledHullSpecs.install(core);
        float k=AirScale.METERS_PER_PX/AirScale.METERS_PER_CELL;
        assertArrayEquals(new float[]{-k,5*k},HullPivotResolver.pivotOffset("test-disk-pivot"),0.00001f);
    }
}
