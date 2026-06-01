package com.dillon.starsectormarines;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a member that exists <strong>only for development / debugging</strong> and
 * should be stripped (or gated off) for a production build — debug overlays, state
 * dumpers, inspection viz, and the like.
 *
 * <p>The point is discoverability: a future "prod build" cleanup pass (or a quick
 * {@code grep @DebugOnly}) can find every debug-only member in one sweep, rather than
 * relying on a naming convention that only methods can follow. Complements
 * {@link DevConfig}, which holds the dev-build <em>toggles</em>; {@code @DebugOnly}
 * marks the <em>code</em> those toggles gate, plus debug code that isn't flag-gated.
 *
 * <p>Apply incrementally, as debug-only members are spotted (same cadence as the
 * gut-check that keeps debug scaffolding out of production-path refactors) — not as a
 * big upfront audit. {@code SOURCE} retention: this is a build-time signal, it carries
 * no runtime cost and leaves no trace in the bytecode.
 */
@Documented
@Retention(RetentionPolicy.SOURCE)
@Target({ElementType.METHOD, ElementType.FIELD, ElementType.TYPE, ElementType.CONSTRUCTOR})
public @interface DebugOnly {
}
