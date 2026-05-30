package com.dillon.starsectormarines.battle.world.gen;

/**
 * One stateless pass of a map-generation pipeline — the System analogue in the
 * Service/System split the rest of the battle tier runs under. A stage reads
 * and mutates the shared {@link GenContext} blackboard and returns nothing; all
 * state flows through {@code ctx} (spine fields + {@link GenKey}-addressed
 * overlays), never through stage instance fields.
 *
 * <p>A generator's {@code generate()} becomes "build a {@link GenContext}, run
 * an ordered {@code List<GenStage>}, assemble the result from the context." A
 * stage that needs a domain overlay is responsible for ordering — its position
 * in the list must follow whatever {@code put}s the overlay it reads. Once
 * {@code GenRecipe} lands (Slice 3) the conditional {@code if (ctx.has(KEY))}
 * gates inside stages collapse into "the stage simply isn't in this recipe."
 *
 * <p>Functional interface so trivial passes can be expressed as lambdas, but
 * the load-bearing passes are concrete classes — named, reusable across
 * recipes, and the natural home for their former private helpers.
 */
@FunctionalInterface
public interface GenStage {
    void run(GenContext ctx);
}
