package com.dillon.starsectormarines.testsupport;

import com.dillon.starsectormarines.ui.retained.svg.SvgAsset;

import java.nio.file.Files;
import java.nio.file.Path;

/** Disk resource adapter shared by tests and UI snapshots; never shipped. */
public final class HeadlessSvgAssets {
    private HeadlessSvgAssets() {}

    public static void install(Path modRoot) {
        Path root = modRoot.toAbsolutePath().normalize();
        SvgAsset.setSource(path -> Files.readString(root.resolve(path)));
    }
}
