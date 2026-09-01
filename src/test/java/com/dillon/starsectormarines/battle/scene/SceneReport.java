package com.dillon.starsectormarines.battle.scene;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * What one loop of one scene found.
 *
 * <p>{@code metrics} are the raw readings the verdicts were judged from —
 * plan-less ticks, cells travelled, casualties — kept beside the verdicts so a
 * later reader can re-judge them against a different bar, and so a control run
 * and its subject can be laid side by side without re-playing either.
 *
 * @param sceneId  the {@link BehaviorScene#id()} this belongs to
 * @param loopId   which loop of that scene; a scene with one loop uses {@code "main"}
 * @param ticks    how many ticks were played
 * @param verdicts every answer the loop gave, in the order it gave them
 * @param metrics  named readings, insertion-ordered
 */
public record SceneReport(String sceneId, String loopId, int ticks,
                          List<Verdict> verdicts, Map<String, Number> metrics) {

    public SceneReport {
        Objects.requireNonNull(sceneId, "sceneId");
        Objects.requireNonNull(loopId, "loopId");
        verdicts = List.copyOf(verdicts);
        metrics = new LinkedHashMap<>(metrics);
    }

    /** True when every verdict passed. A loop with no verdicts passes vacuously and should say so in its label. */
    public boolean passed() {
        for (Verdict v : verdicts) if (!v.pass()) return false;
        return true;
    }
}
