package com.dillon.starsectormarines.ui.retained.markup;

/** Reads one authored component from the host resource system. */
@FunctionalInterface
public interface MarkupSource {
    String read(String path) throws Exception;
}
