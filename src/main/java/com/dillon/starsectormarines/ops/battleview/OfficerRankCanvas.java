package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.marine.Rank;
import com.dillon.starsectormarines.ui.retained.CanvasContext;
import com.dillon.starsectormarines.ui.retained.CanvasProducer;
import com.dillon.starsectormarines.ui.retained.svg.SvgAsset;

import java.awt.Color;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/** Readable commissioned-rank bars, oak leaves and eagle beside the officer's own portrait. */
public final class OfficerRankCanvas implements CanvasProducer {

    public static final int SURFACE_SIZE = 24;

    private final Supplier<Rank> rank;
    private final Map<Rank, SvgAsset> assets = new EnumMap<>(Rank.class);

    public OfficerRankCanvas(Supplier<Rank> rank) {
        this.rank = Objects.requireNonNull(rank, "officer rank supplier");
        assets.put(Rank.LIEUTENANT, load("lieutenant"));
        assets.put(Rank.CAPTAIN, load("captain"));
        assets.put(Rank.MAJOR, load("major"));
        assets.put(Rank.LT_COLONEL, load("lt-colonel"));
        assets.put(Rank.COLONEL, load("colonel"));
    }

    @Override
    public void draw(CanvasContext context) {
        Rank value = rank.get();
        if (value == null) return;
        context.svg(assets.get(value), 0f, 0f, context.metrics().surfaceWidth(),
                context.metrics().surfaceHeight(), Color.WHITE);
    }

    private static SvgAsset load(String name) {
        return SvgAsset.load("graphics/ui/formation/rank-" + name + ".svg");
    }
}
