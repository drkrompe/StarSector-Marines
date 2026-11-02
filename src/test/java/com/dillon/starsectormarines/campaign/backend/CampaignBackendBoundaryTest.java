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
 * no game finds a screen that cannot run. A file listed here still holds a sector of its own; the lists only shrink.
 */
class CampaignBackendBoundaryTest {

    private static final Path SOURCES = Path.of("src/main/java");
    private static final String BACKEND = "com/dillon/starsectormarines/campaign/backend/CampaignBackend.java";
    private static final String CERBERUS = "com/dillon/starsectormarines/campaign/start/CerberusCompanyStart.java";
    private static final String PROLOGUE = "com/dillon/starsectormarines/campaign/start/CompanyProloguePresenter.java";
    private static final String KEEPER = "com/dillon/starsectormarines/campaign/start/SecondWindKeeper.java";

    @Test
    void savedDataIsAskedOfTheBackend() throws IOException {
        assertEquals(new TreeSet<>(Set.of(BACKEND, CERBERUS, PROLOGUE, KEEPER,
                        "com/dillon/starsectormarines/ops/CompanyPrologueScreen.java")),
                filesCalling("getPersistentData()"));
    }

    @Test
    void theCompanyFleetIsAskedOfTheBackend() throws IOException {
        assertEquals(new TreeSet<>(Set.of(BACKEND, CERBERUS, PROLOGUE, KEEPER,
                        "com/dillon/starsectormarines/ops/CompanyViewAbility.java",
                        "com/dillon/starsectormarines/ops/event/PlayerEventPresenter.java",
                        "com/fs/starfarer/api/impl/campaign/rulecmd/CompanyYardCMD.java")),
                filesCalling("getPlayerFleet()"));
    }

    @Test
    void theSectorsScriptsAreAskedOfTheBackend() throws IOException {
        assertEquals(new TreeSet<>(Set.of(BACKEND)), filesCalling("getScripts()"));
    }

    @Test
    void factionsAndMarketsAreAskedOfTheBackend() throws IOException {
        Set<String> onlyTheBackend = new TreeSet<>(Set.of(BACKEND));
        assertEquals(onlyTheBackend, filesCalling("Global.getSector().getFaction("));
        assertEquals(onlyTheBackend, filesCalling("Global.getSector().getPlayerFaction()"));
        assertEquals(onlyTheBackend, filesCalling("Global.getSector().getEconomy().getMarket"));
        assertEquals(onlyTheBackend, filesCalling("Global.getSector().getStarSystem("));
    }

    @Test
    void onlyTheStartAndAVariantReadBuildShipsOfTheGame() throws IOException {
        assertEquals(new TreeSet<>(Set.of(BACKEND, 
                "com/dillon/starsectormarines/campaign/start/CerberusCompanyStart.java",
                "com/dillon/starsectormarines/ops/CompanyShipResolver.java")),
                filesCalling("Global.getFactory().createFleetMember("));
    }

    private static Set<String> filesCalling(String call) throws IOException {
        try (Stream<Path> files = Files.walk(SOURCES)) {
            return files.parallel().filter(file -> file.toString().endsWith(".java"))
                    .filter(file -> contains(file, call))
                    .map(file -> SOURCES.relativize(file).toString().replace('\\', '/'))
                    .collect(Collectors.toCollection(TreeSet::new));
        }
    }

    private static boolean contains(Path file, String call) {
        try {
            return Files.readString(file).contains(call);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }
}
