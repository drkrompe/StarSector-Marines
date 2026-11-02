package com.dillon.starsectormarines.campaign.backend;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A family moved behind {@link CampaignBackend} stays moved: shipped code asks the seam, not the sector, or a host with
 * no game finds a screen that cannot run. A file listed here still holds a sector of its own; the list only shrinks.
 */
class CampaignBackendBoundaryTest {

    private static final Path SOURCES = Path.of("src/main/java");
    private static final String SAVED_DATA = "getPersistentData()";
    private static final Set<String> SAVED_DATA_ALLOWED = Set.of(
            "com/dillon/starsectormarines/campaign/backend/CampaignBackend.java",
            "com/dillon/starsectormarines/campaign/start/CerberusCompanyStart.java",
            "com/dillon/starsectormarines/campaign/start/CompanyProloguePresenter.java",
            "com/dillon/starsectormarines/campaign/start/SecondWindKeeper.java",
            "com/dillon/starsectormarines/ops/CompanyPrologueScreen.java");

    @Test
    void savedDataIsAskedOfTheBackend() throws IOException {
        Set<String> found;
        try (Stream<Path> files = Files.walk(SOURCES)) {
            found = files.parallel().filter(file -> file.toString().endsWith(".java"))
                    .filter(CampaignBackendBoundaryTest::asksSavedData)
                    .map(file -> SOURCES.relativize(file).toString().replace('\\', '/'))
                    .collect(Collectors.toCollection(TreeSet::new));
        }
        assertEquals(new TreeSet<>(SAVED_DATA_ALLOWED), found);
    }

    private static boolean asksSavedData(Path file) {
        try {
            return Files.readString(file).contains(SAVED_DATA);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }
}
