package com.dillon.starsectormarines.battle.world.gen;

/**
 * One stateless pass of a map-generation pipeline — the System analogue in the
 * Service/System split the rest of the battle tier runs under. A stage reads
 * and mutates the shared {@link GenContext} blackboard and returns nothing; all
 * state flows through {@code ctx} (spine fields + {@link GenKey}-addressed
 * overlays), never through stage instance fields.
 *
 * <p>A generator run builds a {@link GenContext}, executes an ordered
 * {@code List<GenStage>}, and assembles the result from that context. A stage
 * that needs a domain overlay must follow the stage that binds it. Map-type
 * differences belong in {@link GenRecipe} membership; a stage uses a
 * conditional overlay only when the condition is genuinely shared behavior.
 *
 * <p>Functional interface so trivial passes can be expressed as lambdas, but
 * the load-bearing passes are concrete classes — named, reusable across
 * recipes, and the natural home for their former private helpers.
 */
@FunctionalInterface
public interface GenStage {
    void run(GenContext ctx);
}
