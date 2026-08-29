/**
 * Framework core — process-lifetime diagnostics.
 *
 * <p>Category: framework core (diagnostics; no single feature owner).
 * <br>Charter:  explain how the game process ended. {@code ProcessExitWatchdog}
 *           names the thread that asked for shutdown, logs exceptions that
 *           no other handler printed, and leaves a dated liveness line in the
 *           game log so a death with no Java-visible cause is still bounded
 *           in time and memory.
 * <br>Boundary: diagnostics only, and process-wide rather than per battle —
 *           the battle-tick profiler and stalled-tick capture live in
 *           {@code battle.profile}. Nothing here is load-bearing for gameplay
 *           correctness; don't hang gameplay state off it.
 */
package com.dillon.starsectormarines.diagnostics;
