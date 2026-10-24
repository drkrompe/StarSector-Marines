package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.testsupport.HeadlessBattles;
import com.dillon.starsectormarines.testsupport.HeadlessStrings;

import java.nio.file.Path;

/**
 * {@link RenderBudgetEvidence} without Gradle or JUnit, for a GPU profiler to launch.
 *
 * <p>Started by {@code build/nsys/render-evidence.cmd}, which {@code gradlew.bat renderEvidence} writes on every
 * run with that run's classpath and properties. Under {@code nsys profile} a Gradle launch injects the profiler
 * into every JVM Gradle forks, and one of them, the toolchain probe, hung there; this is one JVM and nothing else.
 * What JUnit's registered extensions install for a test (the disk catalogs and the mod's strings) is installed
 * here by hand.
 */
final class RenderEvidenceMain {

    private RenderEvidenceMain() { }

    public static void main(String[] args) throws Exception {
        try (HeadlessStrings strings = HeadlessBattles.install(Path.of(""))) {
            new RenderBudgetEvidence().profilesTheRealPipelineAtThreeFramings();
        }
    }
}
