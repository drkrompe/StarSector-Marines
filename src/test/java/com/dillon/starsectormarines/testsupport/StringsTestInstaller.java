package com.dillon.starsectormarines.testsupport;

import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import java.nio.file.Path;

/** Test projections read the shipped catalog, just as the game does. */
public final class StringsTestInstaller implements BeforeEachCallback, AfterEachCallback {
    private HeadlessStrings strings;
    @Override public void beforeEach(ExtensionContext context) throws Exception {
        strings = new HeadlessStrings(Path.of("mod"));
    }
    @Override public void afterEach(ExtensionContext context) {
        if (strings != null) strings.close();
    }
}
