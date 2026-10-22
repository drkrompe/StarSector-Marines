package com.dillon.starsectormarines.campaign.bloom;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * A sector held in memory: markets whose size the bloom can take down, and a record of every lever
 * the bloom pulled. Shared by the unit tests and the bloom evidence harness.
 */
public class TestBloomWorld implements BloomWorld {

    private final long seed;
    private final Map<String, BloomMarket> markets = new TreeMap<>();
    public final List<String> log = new ArrayList<>();
    public final Map<String, Integer> reputation = new TreeMap<>();
    public final List<String> decivilized = new ArrayList<>();
    public boolean hegemonyAlive = true;

    public TestBloomWorld(long seed) {
        this.seed = seed;
    }

    public TestBloomWorld add(BloomMarket market) {
        markets.put(market.id, market);
        return this;
    }

    /** A plain frontier colony: old, workings, not core, at (x, 0) in its own system. */
    public TestBloomWorld colony(String id, String systemId, int size, float x) {
        return add(new BloomMarket(id, id, systemId, "independent", size, 3650, false, false,
                false, false, true, false, x, 0f, 20f, 1f, 0f));
    }

    public BloomMarket get(String id) {
        return markets.get(id);
    }

    @Override public long seed() { return seed; }

    @Override public List<BloomMarket> markets() { return new ArrayList<>(markets.values()); }

    @Override public BloomMarket market(String marketId) { return markets.get(marketId); }

    @Override public boolean hegemonyAlive() { return hegemonyAlive; }

    @Override public void erupted(RavBloom bloom) { log.add("erupted " + bloom.marketId); }

    @Override
    public void wentDark(RavBloom bloom, boolean disrupt) {
        log.add("dark " + bloom.marketId + (disrupt ? " disrupted" : ""));
    }

    @Override public void starved(RavBloom bloom) { log.add("starved " + bloom.marketId); }

    @Override
    public boolean reduceSize(String marketId) {
        BloomMarket m = markets.get(marketId);
        if (m == null || m.size <= RavBloomRules.MIN_SIZE) return false;
        markets.put(marketId, withSize(m, m.size - 1));
        log.add("shrunk " + marketId);
        return true;
    }

    @Override
    public void decivilize(String marketId) {
        decivilized.add(marketId);
        markets.remove(marketId);
    }

    @Override
    public void ended(RavBloom bloom, int unrest) {
        log.add("ended " + bloom.marketId + " " + bloom.end() + " unrest " + unrest);
    }

    @Override
    public void reputation(String factionId, int delta, RavBloom bloom) {
        reputation.merge(factionId, delta, Integer::sum);
    }

    public void grow(String marketId) {
        BloomMarket m = markets.get(marketId);
        if (m != null) markets.put(marketId, withSize(m, m.size + 1));
    }

    public void age(int days) {
        for (Map.Entry<String, BloomMarket> e : markets.entrySet()) {
            BloomMarket m = e.getValue();
            e.setValue(new BloomMarket(m.id, m.name, m.systemId, m.factionId, m.size,
                    m.daysInExistence + days, m.hidden, m.playerOwned, m.ruins, m.habitable,
                    m.workings, m.core, m.x, m.y, m.lyFromCore, m.traffic, m.inflow));
        }
    }

    static BloomMarket withSize(BloomMarket m, int size) {
        return new BloomMarket(m.id, m.name, m.systemId, m.factionId, size, m.daysInExistence,
                m.hidden, m.playerOwned, m.ruins, m.habitable, m.workings, m.core, m.x, m.y,
                m.lyFromCore, m.traffic, m.inflow);
    }
}
