package com.dillon.starsectormarines.campaign.ruins;

import com.dillon.starsectormarines.campaign.CampaignState;

import java.util.Collection;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * What a ruins site remembers ({@code ruins-expedition.md}, Law 6): the ids of its finds already brought home, written
 * once when a won or pulled-out expedition commits. A later expedition meets the same ground (its battle seed is the
 * site's) with those finds left out, and everything else as seeded.
 *
 * <p>The set lives in the site market's memory under {@link #MEMORY_KEY}, as the ids joined by commas.
 */
public final class RuinsSiteFinds {

    /** The site market's memory key for its taken finds. */
    public static final String MEMORY_KEY = "$starsector_marines_ruinsTakenFinds";

    private static final long SEED_SALT = 0x52554E5353495445L;

    private RuinsSiteFinds() {}

    /** The site's battle seed: the same ground, and so the same finds under the same ids, on every expedition. */
    public static long battleSeed(String marketId) {
        long z = SEED_SALT ^ (marketId == null ? 0 : marketId.hashCode()) * 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    /** The ids in a stored value; empty for none. */
    public static Set<String> parse(String stored) {
        Set<String> ids = new TreeSet<>();
        if (stored == null || stored.isBlank()) return ids;
        for (String id : stored.split(",")) if (!id.isBlank()) ids.add(id.trim());
        return ids;
    }

    /** The stored value once {@code recovered} is added. */
    public static String merged(String stored, Collection<String> recovered) {
        Set<String> ids = parse(stored);
        if (recovered != null) for (String id : recovered) if (id != null && !id.isBlank()) ids.add(id.trim());
        return String.join(",", ids);
    }

    /**
     * Commits the expedition recorded for {@code serial}, once: takes it from the ledger and, when it was won or
     * pulled out with finds brought home, writes them into the site's set.
     *
     * @param read  the site's stored value
     * @param write stores the site's new value
     * @return what was taken from the ledger; {@link RuinsExpeditionLedger.Pending#NONE} commits and writes nothing
     */
    public static RuinsExpeditionLedger.Pending commit(CampaignState state, int serial,
                                                       Supplier<String> read, Consumer<String> write) {
        Set<String> recovered = RuinsExpeditionLedger.recordedFinds(state, serial);
        RuinsExpeditionLedger.Pending pending = RuinsExpeditionLedger.take(state, serial);
        boolean broughtHome = pending == RuinsExpeditionLedger.Pending.WON
                || pending == RuinsExpeditionLedger.Pending.PULLED_OUT;
        if (broughtHome && !recovered.isEmpty()) write.accept(merged(read.get(), recovered));
        return pending;
    }
}
