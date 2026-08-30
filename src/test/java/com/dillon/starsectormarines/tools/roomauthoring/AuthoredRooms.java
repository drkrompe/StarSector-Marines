package com.dillon.starsectormarines.tools.roomauthoring;

import com.dillon.starsectormarines.battle.world.gen.fit.layout.RoomLayout;
import com.dillon.starsectormarines.battle.world.gen.fit.layout.RoomLayoutCatalog;
import com.dillon.starsectormarines.battle.world.gen.fit.layout.RoomLayoutJson;
import com.dillon.starsectormarines.battle.world.gen.fit.layout.RoomLayouts;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * The authored rooms on disk, as the tool sees them.
 *
 * <p>The game reads these through {@code SettingsAPI} and cannot list a folder,
 * so the set is named by an index. That index is <b>the tool's to maintain</b>:
 * a room saved without being added to it is a file the game will never open,
 * which is the most disheartening possible outcome for an afternoon's authoring.
 *
 * <p>So writing a room is two files, and the index is rebuilt from what is
 * actually on disk rather than appended to — an index that drifted from the
 * folder would be a room that silently stopped loading.
 */
public final class AuthoredRooms {

    private AuthoredRooms() {}

    private static final String SUFFIX = ".room.json";

    /** Every authored room in the project, ready to install. */
    public static RoomLayouts read(Path projectRoot) throws Exception {
        Path index = projectRoot.resolve("mod").resolve(RoomLayoutCatalog.INDEX);
        if (!Files.exists(index)) return new RoomLayouts(List.of());

        return RoomLayoutCatalog.read(
                new JSONObject(Files.readString(index, StandardCharsets.UTF_8)),
                path -> new JSONObject(Files.readString(
                        projectRoot.resolve("mod").resolve(path), StandardCharsets.UTF_8)));
    }

    /**
     * Write one room, and bring the index back into step with the folder.
     *
     * @return the file name written
     */
    public static String write(Path projectRoot, RoomLayout layout) throws Exception {
        Path folder = projectRoot.resolve("mod").resolve(RoomLayoutCatalog.FOLDER);
        Files.createDirectories(folder);

        String name = layout.purpose().name().toLowerCase() + "."
                + layout.fit().name().toLowerCase() + SUFFIX;
        replace(folder.resolve(name), RoomLayoutJson.write(layout).toString(2));
        replace(projectRoot.resolve("mod").resolve(RoomLayoutCatalog.INDEX),
                RoomLayoutCatalog.index(namesIn(folder)).toString(2));
        return name;
    }

    /** Every room document in the folder, sorted so the index does not churn. */
    private static List<String> namesIn(Path folder) throws Exception {
        List<String> names = new ArrayList<>();
        try (Stream<Path> files = Files.list(folder)) {
            files.map(path -> path.getFileName().toString())
                    .filter(name -> name.endsWith(SUFFIX))
                    .sorted()
                    .forEach(names::add);
        }
        return names;
    }

    /**
     * Replace a file in one step.
     *
     * <p>Written beside and moved over, as the other pages do: a half-written
     * document is a room the game refuses at startup rather than a bad edit
     * somebody can undo.
     */
    private static void replace(Path target, String content) throws Exception {
        Path staged = target.resolveSibling(target.getFileName() + ".tmp");
        Files.writeString(staged, content, StandardCharsets.UTF_8);
        Files.move(staged, target, StandardCopyOption.REPLACE_EXISTING);
    }
}
