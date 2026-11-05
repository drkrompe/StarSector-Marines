package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.campaign.CampaignStateScript;
import com.dillon.starsectormarines.campaign.WellhollowBloomProgress;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.io.xml.DomDriver;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/**
 * {@code playBattle}'s saves: the stand-in campaign as XML, through XStream as the game's own saves go, so the mod's
 * {@code readResolve}s and transients behave as they do on a real load. An element the classes no longer know is skipped
 * and a field the file does not have stays unset, so a save outlives the code being tweaked. They live under the user's
 * home, shared by every worktree and outliving {@code build}.
 *
 * <p>Two profiles keep a scripted run from overwriting where the owner stopped. A run writes only to its own: the owner's
 * is the directory itself, and a run with a script takes the script profile unless {@code -PplayProfile} says otherwise.
 * The script profile also reads the owner's saves, so a script can start from any of them and leave it as it was.
 */
final class PlaySaves {

    /** The checkpoint taken on every arrival at the mission board; "pick up where I stopped". */
    static final String AUTO = "auto";

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final Path OWNER = Path.of(System.getProperty("user.home"), ".starsector-marines", "play-saves");
    private static final Path SCRIPT = OWNER.resolve("script");
    private static final Path DIRECTORY = directory(System.getProperty("play.profile", ""),
            !System.getProperty("play.script", "").isBlank());

    /** A campaign as it is written out. A plain class: XStream cannot set a record's final fields on this JDK. */
    static final class State {
        MarineRoster roster;
        CampaignStateScript campaign;
        Map<String, Object> saved;
        String docked;
        float credits;
        /** What the hold has, by commodity; null, as in any older save, is stores that do not run out. */
        Map<String, Float> stores;
        List<Ship> awarded;
    }

    /** A ship the company gained in play; the stand-in's own hull is not written. */
    static final class Ship {
        String id;
        String variantId;
        String name;
    }

    private PlaySaves() {
    }

    /** The profile this run writes to. */
    static String profile() {
        return DIRECTORY.equals(SCRIPT) ? "script" : "owner";
    }

    private static Path directory(String profile, boolean scripted) {
        return switch (profile.strip()) {
            case "" -> scripted ? SCRIPT : OWNER;
            case "owner" -> OWNER;
            case "script" -> SCRIPT;
            default -> throw new IllegalArgumentException("no play profile called " + profile + "; there are: owner script");
        };
    }

    static void write(String name, State state) {
        try {
            Files.createDirectories(DIRECTORY);
            Files.writeString(DIRECTORY.resolve(name + ".xml"), xstream().toXML(state));
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    static State read(String name) {
        Path file = file(name);
        if (!Files.exists(file)) {
            throw new IllegalArgumentException("no play save called " + name + "; there are: " + String.join(" ", names()));
        }
        try {
            return (State) xstream().fromXML(Files.readString(file));
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    /** Every save, the newest first. */
    static List<String> names() {
        List<Path> saves = new ArrayList<>();
        for (Path directory : readable()) {
            if (!Files.isDirectory(directory)) continue;
            try (Stream<Path> files = Files.list(directory)) {
                files.filter(file -> file.toString().endsWith(".xml")).forEach(saves::add);
            } catch (IOException failure) {
                throw new UncheckedIOException(failure);
            }
        }
        return saves.stream()
                .sorted(Comparator.comparing(PlaySaves::written).reversed())
                .map(file -> file.getFileName().toString().replaceFirst("\\.xml$", ""))
                .distinct()
                .toList();
    }

    /** When the save was written, as the menu shows it. */
    static String when(String name) {
        return WHEN.format(written(file(name)).toInstant().atZone(ZoneId.systemDefault()));
    }

    /**
     * The board's checkpoint: {@link #AUTO}, and for each story mission on offer a save named for it and docked where it
     * is offered, so {@code -PplayLoad=<mission>} returns to just before launching it. Returns the names written.
     */
    static List<String> checkpoint(State state) {
        List<String> written = new ArrayList<>();
        write(AUTO, state);
        written.add(AUTO);
        for (Map.Entry<String, PlanetAPI> offer : storyOffers().entrySet()) {
            state.docked = offer.getValue().getId();
            write(offer.getKey(), state);
            written.add(offer.getKey());
        }
        return written;
    }

    /** Each client's current story mission on every world, by save name. */
    private static Map<String, PlanetAPI> storyOffers() {
        Map<String, PlanetAPI> offers = new LinkedHashMap<>();
        for (PlanetAPI world : PlayCompany.worlds()) {
            MarineOpsContext board = new MarineOpsContext(world);
            for (Client client : board.clients) {
                if (CompletedReplays.isClient(client)) continue;
                board.getMissionsFor(client).stream().filter(mission -> mission.source == MissionSource.STORY
                                && !WellhollowBloomProgress.replay(mission.id))
                        .findFirst().ifPresent(mission -> offers.put(slug(mission.name), world));
            }
        }
        return offers;
    }

    private static String slug(String name) {
        return name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
    }

    /** Where a save of this name is read from: this run's own copy, else the owner's. */
    private static Path file(String name) {
        for (Path directory : readable()) {
            Path file = directory.resolve(name + ".xml");
            if (Files.exists(file)) return file;
        }
        return DIRECTORY.resolve(name + ".xml");
    }

    private static List<Path> readable() {
        return DIRECTORY.equals(OWNER) ? List.of(OWNER) : List.of(DIRECTORY, OWNER);
    }

    private static FileTime written(Path file) {
        try {
            return Files.getLastModifiedTime(file);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    private static XStream xstream() {
        XStream xstream = new XStream(new DomDriver());
        // The user's own files, written by this class.
        XStream.setupDefaultSecurity(xstream);
        xstream.allowTypesByRegExp(new String[]{".*"});
        xstream.ignoreUnknownElements();
        return xstream;
    }
}
