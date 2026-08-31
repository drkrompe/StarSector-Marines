package com.dillon.starsectormarines.testsupport;

import com.dillon.starsectormarines.battle.air.engine.HullFootprintResolver;
import org.json.JSONObject;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The hull dimensions a loaded game would have supplied, read off the install
 * instead.
 *
 * <p>Every aircraft is sized by its hull's {@code .ship} {@code height}, and
 * {@link HullFootprintResolver} reaches that through {@code SettingsAPI}.
 * Headless there is no {@code SettingsAPI}, so it caches its fallback for every
 * hull alike — and a Wasp, a Broadsword and a Valkyrie all come out the same
 * length. Everything downstream inherits it: the hull a snapshot draws, the
 * body radius a shot is checked against, the sweep a cook-off catches people
 * with. Two suites had each grown a private read of the install to escape that;
 * this is that read, once, in front of the resolver rather than beside it, so
 * headless code asks the question it asks in the game and gets the same answer.
 *
 * <p>Test/tool scope on purpose, and it cannot be otherwise: the mod's script
 * sandbox forbids shipped code from touching the filesystem at all, so the
 * install-reading half has to live away from the resolver it feeds.
 *
 * <p><b>Degrades rather than fails.</b> With no install to point at, nothing is
 * installed and the resolver keeps the fallback it always had —
 * {@code HullFootprintResolver.isMeasured} is then how a caller finds out that
 * it is looking at a stand-in, which is the difference between a machine that
 * cannot answer the question and one that answers it wrongly.
 *
 * <p>Repeatable, like {@link DiskRegistries}: several callers reach it in one
 * process and the second must not throw away the first's work, so the specs
 * already parsed survive. It re-registers every time rather than returning
 * early, which is what lets anything that swapped the source out — a test of
 * the seam itself — put the real one back.
 */
public final class InstalledHullSpecs implements HullFootprintResolver.HullDimensions {

    private static InstalledHullSpecs installed;

    private final Path hullDir;
    private final Map<String, Float> forwardExtentPxByHull = new ConcurrentHashMap<>();

    private InstalledHullSpecs(Path hullDir) {
        this.hullDir = hullDir;
    }

    /**
     * Sizes hulls from {@code <starsectorCore>/data/hulls}, if nothing has.
     *
     * <p>The install is the whole answer here because the mod ships no hulls of
     * its own; a mod that did would want the same load order the game uses,
     * which is its own folder first.
     */
    public static synchronized void install(Path starsectorCore) {
        if (starsectorCore == null) return;
        Path hulls = starsectorCore.resolve("data").resolve("hulls");
        if (installed == null || !installed.hullDir.equals(hulls)) {
            installed = new InstalledHullSpecs(hulls);
        }
        HullFootprintResolver.useHullDimensions(installed);
    }

    /**
     * The same, for a caller that knows only the {@code starsectorDir} system
     * property the build sets on every headless task. Silently does nothing
     * when it is unset — see the degradation note above.
     */
    public static void install() {
        String dir = System.getProperty("starsectorDir");
        if (dir == null || dir.isBlank()) return;
        install(Paths.get(dir, "starsector-core"));
    }

    @Override
    public float forwardExtentPx(String hullId) {
        if (hullId == null || hullId.isEmpty()) return 0f;
        return forwardExtentPxByHull.computeIfAbsent(hullId, this::readForwardExtentPx);
    }

    private float readForwardExtentPx(String hullId) {
        Path spec = hullDir.resolve(hullId + ".ship");
        try {
            return (float) new JSONObject(Files.readString(spec)).optDouble("height", 0.0);
        } catch (Exception e) {
            return 0f;
        }
    }
}
