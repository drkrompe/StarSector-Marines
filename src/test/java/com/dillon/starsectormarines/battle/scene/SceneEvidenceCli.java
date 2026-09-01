package com.dillon.starsectormarines.battle.scene;

import com.dillon.starsectormarines.testsupport.InstalledHullSpecs;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Plays the behaviour-scene catalog for its verdicts and nothing else.
 *
 * <p>The same scenes already play under {@code createSnapshots}, but rendering
 * is the slow part of that and a GIF cannot fail a build. This runs them with
 * {@link FrameSink#NONE}, writes one verdict file per loop, and turns the
 * answers into an exit status.
 */
public final class SceneEvidenceCli {

    private SceneEvidenceCli() {}

    /** A usage mistake — a selector naming a scene that does not exist. */
    private static final int USAGE_EXIT = 2;

    public static void main(String[] args) throws Exception {
        Path projectRoot = args.length > 0 ? Path.of(args[0]) : Path.of(".");
        Path starsectorCore = args.length > 1
                ? Path.of(args[1])
                : Path.of(System.getProperty("starsectorDir"), "starsector-core");
        Path outputRoot = args.length > 2
                ? Path.of(args[2])
                : projectRoot.resolve("build/reports/scenes");
        String selector = args.length > 3 ? args[3] : "all";

        // Hull geometry comes from the install's own `.ship` specs, and without
        // it every aircraft in every scene is the same fallback size — a body
        // radius and a blast catch belonging to an aircraft that exists nowhere.
        InstalledHullSpecs.install(starsectorCore);
        SceneRegistries.installArmoury(projectRoot);

        List<BehaviorScene> scenes;
        try {
            scenes = SceneCatalog.discover().select(selector);
        } catch (IllegalArgumentException problem) {
            System.err.println(problem.getMessage());
            System.exit(USAGE_EXIT);
            return;
        }

        List<SceneReport> reports = new ArrayList<>();
        for (BehaviorScene scene : scenes) {
            for (SceneReport report : playScene(scene)) {
                reports.add(report);
                SceneReportJson.write(report, outputRoot
                        .resolve(report.sceneId())
                        .resolve(report.loopId() + ".verdict.json"));
            }
        }
        writeSummary(reports, outputRoot);

        System.out.print(table(reports));
        System.out.println("Wrote " + reports.size() + " loop verdict(s) from "
                + scenes.size() + " scene(s) under " + outputRoot.toAbsolutePath());
        System.exit(exitStatus(reports));
    }

    /**
     * Plays one scene, turning a thrown exception into a failing report rather
     * than letting it end the run: one broken scene must not hide what the
     * others found.
     */
    public static List<SceneReport> playScene(BehaviorScene scene) {
        try {
            return scene.play(FrameSink.NONE);
        } catch (Exception failure) {
            String detail = failure.getClass().getSimpleName()
                    + (failure.getMessage() == null ? "" : ": " + failure.getMessage());
            return List.of(new SceneReport(scene.id(), "main", 0,
                    List.of(Verdict.fail("played", detail)), Map.of()));
        }
    }

    /** 1 when any verdict failed, 0 otherwise. */
    public static int exitStatus(List<SceneReport> reports) {
        for (SceneReport report : reports) {
            if (!report.passed()) return 1;
        }
        return 0;
    }

    /** One row per loop, with a line beneath it for every verdict that failed. */
    public static String table(List<SceneReport> reports) {
        int sceneWidth = "scene".length();
        int loopWidth = "loop".length();
        for (SceneReport report : reports) {
            sceneWidth = Math.max(sceneWidth, report.sceneId().length());
            loopWidth = Math.max(loopWidth, report.loopId().length());
        }
        String rowFormat = "%-6s%-" + sceneWidth + "s  %-" + loopWidth + "s  %d/%d verdicts\n";
        StringBuilder out = new StringBuilder();
        out.append(String.format(Locale.ROOT,
                "%-6s%-" + sceneWidth + "s  %-" + loopWidth + "s  %s\n",
                "", "scene", "loop", "verdicts"));
        for (SceneReport report : reports) {
            long passed = report.verdicts().stream().filter(Verdict::pass).count();
            out.append(String.format(Locale.ROOT, rowFormat,
                    report.passed() ? "PASS" : "FAIL", report.sceneId(), report.loopId(),
                    passed, report.verdicts().size()));
            if (report.passed()) continue;
            for (Verdict verdict : report.verdicts()) {
                if (verdict.pass()) continue;
                out.append("        ").append(verdict.name())
                        .append(": ").append(verdict.detail()).append('\n');
            }
        }
        return out.toString();
    }

    static void writeSummary(List<SceneReport> reports, Path outputRoot)
            throws IOException {
        Files.createDirectories(outputRoot);

        StringBuilder markdown = new StringBuilder("# Behaviour scene verdicts\n\n");
        markdown.append("```\n").append(table(reports)).append("```\n");
        Files.writeString(outputRoot.resolve("summary.md"), markdown.toString(),
                StandardCharsets.UTF_8);

        StringBuilder json = new StringBuilder("{\n  \"passed\": ")
                .append(exitStatus(reports) == 0).append(",\n  \"loops\": [");
        for (int i = 0; i < reports.size(); i++) {
            json.append(i == 0 ? "\n" : ",\n");
            json.append(SceneReportJson.toJson(reports.get(i)).stripTrailing().indent(4)
                    .stripTrailing());
        }
        json.append(reports.isEmpty() ? "]" : "\n  ]").append("\n}\n");
        Files.writeString(outputRoot.resolve("summary.json"), json.toString(),
                StandardCharsets.UTF_8);
    }
}
