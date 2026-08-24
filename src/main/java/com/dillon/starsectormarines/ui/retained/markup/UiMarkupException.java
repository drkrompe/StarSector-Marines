package com.dillon.starsectormarines.ui.retained.markup;

/** A strict markup refusal, optionally positioned in its source file. */
public final class UiMarkupException extends RuntimeException {

    private final boolean positioned;

    public UiMarkupException(String message) {
        this(message, false);
    }

    private UiMarkupException(String message, boolean positioned) {
        super(message);
        this.positioned = positioned;
    }

    public static UiMarkupException at(String fileName, int line, int column, String message) {
        return new UiMarkupException(fileName + ":" + line + ":" + column + " — " + message, true);
    }

    public boolean isPositioned() {
        return positioned;
    }
}
