package com.dillon.starsectormarines.battle.world.gen;

import java.util.List;

/**
 * A named, ordered composition of {@link GenStage}s — the "what map type" knob
 * of the generation pipeline (the Game / specific-behavior analogue to
 * {@link GenContext} as Service and {@link GenStage} as System; see
 * {@code roadmap/mapgen/composable-pipeline.md}).
 *
 * <p>Where a {@link GenStage} is a single reusable pass and {@link GenContext}
 * is the shared blackboard, a recipe is the decision of <em>which</em> passes
 * run and <em>in what order</em> for one kind of map. Forking conquest vs.
 * legacy is no longer an {@code if (biomeMap != null)} inside one hard-coded
 * sequence — it's two recipes that share most stages and differ only in
 * membership (the conquest-only stages simply aren't in the legacy list).
 * Adding a new map type (station, ship interior) becomes additive: author a
 * recipe plus its domain stages; the generic stages (fill dispatch, tactical
 * link, finalize) are reused verbatim.
 *
 * <p>Immutable and stateless — the stage list is copied defensively and a
 * single recipe instance can be {@linkplain #run run} against many contexts
 * (one per battle), exactly like the {@link MapGenerator} contract it serves.
 */
public final class GenRecipe {

    private final String name;
    private final List<GenStage> stages;

    public GenRecipe(String name, List<GenStage> stages) {
        this.name = name;
        this.stages = List.copyOf(stages);
    }

    /** Debug / preview label (e.g. {@code "ConquestCity"}). Not load-bearing. */
    public String name() {
        return name;
    }

    /** The ordered, immutable stage list. */
    public List<GenStage> stages() {
        return stages;
    }

    /** Run every stage in order against {@code ctx}. */
    public void run(GenContext ctx) {
        for (GenStage stage : stages) {
            stage.run(ctx);
        }
    }
}
