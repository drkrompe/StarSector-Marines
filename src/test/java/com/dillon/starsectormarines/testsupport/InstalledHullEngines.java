package com.dillon.starsectormarines.testsupport;

import com.dillon.starsectormarines.battle.air.engine.HullKinematicsResolver;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The maneuver numbers a loaded game would have supplied, read off the install's {@code ship_data.csv}.
 *
 * <p>Headless there is no {@code SettingsAPI}, so {@link HullKinematicsResolver} gives every hull the same generic
 * fallback fighter: a Dagger, a Broadsword and a Talon all fly at 21.5 cells per second. Anything that asks who catches
 * whom, which a tail chase does, gets an answer about three aircraft that do not exist. This is the sibling of
 * {@link InstalledHullSpecs} for speed and turn rather than size, and it is opt-in rather than folded into it because
 * installing it changes how every fighter in a scene flies; a scene that is about pursuit asks for it by name.
 *
 * <p>Degrades rather than fails: with no install nothing is installed and the fallback stays.
 */
public final class InstalledHullEngines {

    private InstalledHullEngines() {}

    /** Reads {@code <starsectorCore>/data/hulls/ship_data.csv} and installs it as the resolver's headless source. */
    public static void install(Path starsectorCore) {
        if (starsectorCore == null) return;
        Path csv = starsectorCore.resolve("data").resolve("hulls").resolve("ship_data.csv");
        Map<String, float[]> engines = new HashMap<>();
        try {
            List<String> lines = Files.readAllLines(csv);
            if (lines.isEmpty()) return;
            String[] header = lines.get(0).split(",", -1);
            int id = column(header, "id");
            int speed = column(header, "max speed");
            int accel = column(header, "acceleration");
            int decel = column(header, "deceleration");
            int turn = column(header, "max turn rate");
            if (id < 0 || speed < 0 || accel < 0 || decel < 0 || turn < 0) return;
            for (int i = 1; i < lines.size(); i++) {
                String[] cells = lines.get(i).split(",", -1);
                if (cells.length <= Math.max(Math.max(speed, accel), Math.max(decel, turn)) || cells[id].isBlank()) continue;
                try {
                    engines.put(cells[id].trim(), new float[]{Float.parseFloat(cells[speed]), Float.parseFloat(cells[accel]),
                            Float.parseFloat(cells[decel]), Float.parseFloat(cells[turn])});
                } catch (NumberFormatException skipped) {
                    // A row with a blank or non-numeric maneuver cell is not a hull this can answer for.
                }
            }
        } catch (Exception unreadable) {
            return;
        }
        HullKinematicsResolver.useEngineSource(engines::get);
    }

    /** As above, from the {@code starsectorDir} property the build sets on every headless task; does nothing when unset. */
    public static void install() {
        String dir = System.getProperty("starsectorDir");
        if (dir == null || dir.isBlank()) return;
        install(Paths.get(dir, "starsector-core"));
    }

    /** Removes the source, returning every hull to the fallback. */
    public static void uninstall() {
        HullKinematicsResolver.useEngineSource(null);
    }

    private static int column(String[] header, String name) {
        for (int i = 0; i < header.length; i++) {
            if (header[i].trim().equalsIgnoreCase(name)) return i;
        }
        return -1;
    }
}
