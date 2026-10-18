package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.ops.HqActionItem.Filter;
import com.dillon.starsectormarines.ops.HqActionItem.Kind;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The HQ action board: every action item, ordered by urgency and bounded, with what each
 * filter holds. Pure and headless; the HQ screen gathers the items from the campaign.
 *
 * <p>Order: obligations falling due within {@link #SOON_DAYS} first, then story leads,
 * letters and side jobs, then obligations with longer to run. Within a band, soonest deadline
 * first, then the order the items were gathered in. Side jobs are the only open-ended source,
 * so they are capped at {@link #SIDE_JOB_LIMIT}; the board keeps the count it left out.
 */
record HqActionBoard(List<HqActionItem> items, int hiddenSideJobs) {

    static final int SOON_DAYS = 7;
    static final int SIDE_JOB_LIMIT = 4;

    static HqActionBoard of(List<HqActionItem> gathered) {
        List<HqActionItem> kept = new ArrayList<>();
        int sideJobs = 0;
        int hidden = 0;
        List<HqActionItem> bySoonest = new ArrayList<>(gathered);
        bySoonest.sort(Comparator.comparingInt(HqActionItem::daysLeft));
        for (HqActionItem item : bySoonest) {
            if (item.kind() == Kind.SIDE_JOB && sideJobs++ >= SIDE_JOB_LIMIT) {
                hidden++;
                continue;
            }
            kept.add(item);
        }
        kept.sort(Comparator.comparingInt(HqActionBoard::band)
                .thenComparingInt(HqActionItem::daysLeft)
                .thenComparingInt(gathered::indexOf));
        return new HqActionBoard(List.copyOf(kept), hidden);
    }

    /** The items a filter shows, in board order. */
    List<HqActionItem> shown(Filter filter) {
        if (filter == Filter.ALL) return items;
        return items.stream().filter(item -> item.kind().filter == filter).toList();
    }

    /** How many items each filter holds, ALL included; hidden side jobs count toward ALL and OTHER. */
    Map<Filter, Integer> counts() {
        Map<Filter, Integer> counts = new EnumMap<>(Filter.class);
        for (Filter filter : Filter.values()) counts.put(filter, shown(filter).size());
        counts.merge(Filter.ALL, hiddenSideJobs, Integer::sum);
        counts.merge(Filter.OTHER, hiddenSideJobs, Integer::sum);
        return counts;
    }

    /** The item a selection names, or the first shown when it names none of them. */
    HqActionItem selected(Filter filter, String id) {
        List<HqActionItem> shown = shown(filter);
        for (HqActionItem item : shown) if (item.id().equals(id)) return item;
        return shown.isEmpty() ? null : shown.get(0);
    }

    private static int band(HqActionItem item) {
        return switch (item.kind()) {
            case OBLIGATION -> item.daysLeft() <= SOON_DAYS ? 0 : 4;
            case STORY -> 1;
            case LETTER -> 2;
            case SIDE_JOB -> 3;
        };
    }
}
