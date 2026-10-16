package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.world.gen.BlackSluice;
import java.util.List;

/**
 * Greyhook's authored, finite strength at Black Sluice. Every group names its outfit, size,
 * where it stands or enters, what commits it and which side of Voss's split it takes. The table
 * never reads the player's selected strength: more company lift meets the same people.
 */
public final class SmallWarForces {
    public enum Outfit { DRENT_HAULAGE, BERTH_FOUR, PRIZE_CREW }
    /** What puts a group on the field: present from the start, or a disclosed reserve's trigger. */
    public enum Commitment { AT_START, CUSTOMS_FALLS, FUEL_CONTROL_FALLS }
    /** Who obeys Voss's withdrawal order. */
    public enum SplitSide { WITHDRAWS, HOLDS }
    /**
     * @param post the cell a starting group defends, or a reserve's named ingress
     * @param targetSite for reserves, the site index it is committed against; -1 for a post
     */
    public record Group(String id, String name, Outfit outfit, int size, int x, int y, String ingress,
                        Commitment commitment, int targetSite, SplitSide split) {
        public boolean reserve() { return commitment != Commitment.AT_START; }
    }

    public static final List<Group> GROUPS = List.of(
            new Group("customs-watch", "Drent Haulage customs watch", Outfit.DRENT_HAULAGE, 4,
                    58, 69, "customs block", Commitment.AT_START, -1, SplitSide.WITHDRAWS),
            new Group("road-crew", "Drent Haulage transfer-road crew", Outfit.DRENT_HAULAGE, 4,
                    116, 60, "causeway at the support gun", Commitment.AT_START, -1, SplitSide.WITHDRAWS),
            new Group("fuel-crew", "Drent Haulage fuel crew", Outfit.DRENT_HAULAGE, 4,
                    123, 70, "fuel-control house", Commitment.AT_START, -1, SplitSide.WITHDRAWS),
            new Group("service-picket", "Drent Haulage service-road picket", Outfit.DRENT_HAULAGE, 4,
                    150, 23, "service road", Commitment.AT_START, -1, SplitSide.WITHDRAWS),
            new Group("dispatch-hold", "Berth Four dispatch hold", Outfit.BERTH_FOUR, 6,
                    166, 56, "dispatch house", Commitment.AT_START, -1, SplitSide.HOLDS),
            new Group("prize-crew-gate", "Prize crew, dispatch gate", Outfit.PRIZE_CREW, 4,
                    155, 60, "dispatch gate", Commitment.AT_START, -1, SplitSide.WITHDRAWS),
            // The pier crew stand watch at the Venture's stern gantry in the docking compound, beside the prize ship.
            new Group("prize-crew-pier", "Prize crew, pier", Outfit.PRIZE_CREW, 4,
                    205, 65, "Venture's stern gantry", Commitment.AT_START, -1, SplitSide.HOLDS),
            new Group("drent-reserve", "Drent Haulage transfer reserve", Outfit.DRENT_HAULAGE, 4,
                    BlackSluice.SERVICE_INGRESS_X, BlackSluice.SERVICE_INGRESS_Y, "service-road gate",
                    Commitment.CUSTOMS_FALLS, 1, SplitSide.WITHDRAWS),
            new Group("berth-four-counter", "Berth Four counterattack", Outfit.BERTH_FOUR, 6,
                    BlackSluice.PIER_INGRESS_X, BlackSluice.PIER_INGRESS_Y, "pier stair",
                    Commitment.FUEL_CONTROL_FALLS, 1, SplitSide.HOLDS));

    private SmallWarForces() { }

    public static int total() { return GROUPS.stream().mapToInt(Group::size).sum(); }
    public static int reserveTotal() { return GROUPS.stream().filter(Group::reserve).mapToInt(Group::size).sum(); }
}
