package com.dillon.starsectormarines.battle.scene;

import java.util.Objects;

/**
 * One yes-or-no a scene answers about its own recording.
 *
 * <p>{@code detail} carries the measurement the answer was read off — "handback
 * at tick 412, arrival at 411" — so a FAIL line says what was seen rather than
 * only that it was wrong.
 */
public record Verdict(String name, boolean pass, String detail) {

    public Verdict {
        Objects.requireNonNull(name, "name");
        detail = detail == null ? "" : detail;
    }

    public static Verdict pass(String name, String detail) {
        return new Verdict(name, true, detail);
    }

    public static Verdict fail(String name, String detail) {
        return new Verdict(name, false, detail);
    }

    public static Verdict of(String name, boolean pass, String detail) {
        return new Verdict(name, pass, detail);
    }
}
