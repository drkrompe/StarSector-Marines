package com.dillon.starsectormarines.tools.mapauthoring;

import com.dillon.starsectormarines.battle.world.gen.LowQuay;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.SettlementLink;
import com.dillon.starsectormarines.battle.world.gen.ShepherdBasin;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.tools.mapauthoring.MapSketch.Place;
import com.dillon.starsectormarines.tools.mapauthoring.MapSketch.Role;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;

/**
 * The maps the page opens: an existing mission map, generated as it plays, with its named places. Never an empty
 * grid. Each is laid out on one palette ({@link #builtAs}) and re-skinned for every other world, so the layout under
 * every skin is the same map. The owned-map documents ({@code owned-maps}) join this list as they are written.
 */
public final class MapBases {

    public record Base(String id, String label, long seed, SurfacePalette builtAs,
                       Function<TargetProfile, MapResult> generator, List<Place> places) {
        public MapResult generate() {
            return generator.apply(new TargetProfile(0, 0, 0, 0, "", Set.of(), builtAs, SettlementLink.ROAD));
        }
        public MapSketch sketch() {
            return new MapSketch(id, List.of(), List.of(), List.of(), places, List.of());
        }
    }

    private MapBases() { }

    public static List<Base> all() {
        List<Base> out = new ArrayList<>();
        for (ShepherdBasin.Visit visit : ShepherdBasin.Visit.values()) {
            String name = visit.name().toLowerCase(Locale.ROOT).replace('_', '-');
            out.add(new Base("shepherd-basin/" + name, "Shepherd Basin · " + name.replace('-', ' '), 14101,
                    SurfacePalette.ARID, p -> ShepherdBasin.generate(14101, p, visit), shepherdPlaces()));
        }
        out.add(new Base("low-quay", "Low Quay", 7, SurfacePalette.VERDANT, p -> LowQuay.generate(7, p), List.of(
                new Place("landing", "Landing", LowQuay.LANDING_X, LowQuay.LANDING_Y, Role.LANDING),
                new Place("records", "Records office", LowQuay.RECORDS_X, LowQuay.RECORDS_Y, Role.SITE),
                new Place("kit", "Kit store", LowQuay.KIT_X, LowQuay.KIT_Y, Role.SITE),
                new Place("load", "Loading berth", LowQuay.LOAD_X, LowQuay.LOAD_Y, Role.SITE),
                new Place("crossing", "Crossing", LowQuay.CROSS_X, LowQuay.CROSS_Y, Role.LANDMARK))));
        return out;
    }

    public static Base find(String id) {
        for (Base b : all()) if (b.id().equals(id)) return b;
        throw new IllegalArgumentException("no map called " + id + "; known: "
                + all().stream().map(Base::id).toList());
    }

    private static List<Place> shepherdPlaces() {
        List<Place> out = new ArrayList<>();
        for (ShepherdBasin.Place p : ShepherdBasin.PLACES) {
            out.add(new Place(p.id(), p.label(), p.x(), p.y(), p == ShepherdBasin.LANDING ? Role.LANDING : Role.SITE));
        }
        return out;
    }
}
