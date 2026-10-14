package com.dillon.starsectormarines.battle.scene;

import com.dillon.starsectormarines.testsupport.InstalledHullSpecs;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Plays the selected behaviour scenes twice and fails on the first reading that differs.
 *
 * <p>A scene is evidence only if the same world plays the same way twice: a reading that drifts from run to run
 * cannot be compared against its control. {@code simDeterminism} owns the same question for the Conquest matrix; this
 * owns it for scenes, which are where a changed number is most often taken for a finding. Opt-in and excluded from
 * {@code test}, because it plays every selected loop twice.
 */
public final class SceneDeterminismCli {

    private SceneDeterminismCli() {}

    public static void main(String[] args) throws Exception {
        Path projectRoot = args.length > 0 ? Path.of(args[0]) : Path.of(".");
        Path starsectorCore = args.length > 1
                ? Path.of(args[1])
                : Path.of(System.getProperty("starsectorDir"), "starsector-core");
        String selector = args.length > 2 ? args[2] : "all";
        InstalledHullSpecs.install(starsectorCore);
        SceneRegistries.installArmoury(projectRoot);
        List<String> differences = new ArrayList<>();
        int compared = 0;
        for (BehaviorScene scene : SceneCatalog.discover().select(selector)) {
            List<SceneReport> first = SceneEvidenceCli.playScene(scene);
            List<SceneReport> second = SceneEvidenceCli.playScene(scene);
            if (first.size() != second.size()) {
                differences.add(scene.id() + ": " + first.size() + " loops then " + second.size());
                continue;
            }
            for (int i = 0; i < first.size(); i++) {
                compared++;
                String drift = difference(first.get(i), second.get(i));
                System.out.println((drift == null ? "SAME  " : "DIFFER ") + scene.id() + "/" + first.get(i).loopId()
                        + (drift == null ? "" : "  " + drift));
                if (drift != null) differences.add(scene.id() + "/" + first.get(i).loopId() + ": " + drift);
            }
        }
        System.out.println(compared + " loop(s) compared, " + differences.size() + " differ");
        System.exit(differences.isEmpty() ? 0 : 1);
    }

    /** The first reading that differs between two plays of one loop, or null when they agree. */
    static String difference(SceneReport a, SceneReport b) {
        for (Map.Entry<String, Number> entry : a.metrics().entrySet()) {
            Number other = b.metrics().get(entry.getKey());
            if (other == null || !other.toString().equals(entry.getValue().toString())) {
                return "metric " + entry.getKey() + " " + entry.getValue() + " then " + other;
            }
        }
        for (int i = 0; i < Math.min(a.verdicts().size(), b.verdicts().size()); i++) {
            Verdict x = a.verdicts().get(i);
            Verdict y = b.verdicts().get(i);
            if (x.pass() != y.pass() || !x.detail().equals(y.detail())) {
                return "verdict " + x.name() + " '" + x.detail() + "' then '" + y.detail() + "'";
            }
        }
        if (a.verdicts().size() != b.verdicts().size()) return "verdict count";
        return null;
    }
}
