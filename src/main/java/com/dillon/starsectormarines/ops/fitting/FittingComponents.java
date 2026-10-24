package com.dillon.starsectormarines.ops.fitting;

import java.util.ArrayList;
import java.util.List;

/** The fitting kit's markup components, which a refit room loads ahead of its own. */
public final class FittingComponents {

    /** Under the mod's root, as the game's own loader reads them. */
    public static final List<String> PATHS = List.of(
            "data/ui/components/fitting/fitting-card.mlx",
            "data/ui/components/fitting/fitting-filter-pill.mlx",
            "data/ui/components/fitting/fitting-meter.mlx",
            "data/ui/components/fitting/fitting-slot-card.mlx");

    private FittingComponents() { }

    /** The kit's components, then {@code roomPaths}: a room's markup may use the kit's tags. */
    public static List<String> with(List<String> roomPaths) {
        List<String> paths = new ArrayList<>(PATHS);
        paths.addAll(roomPaths);
        return List.copyOf(paths);
    }
}
