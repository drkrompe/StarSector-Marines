package com.dillon.starsectormarines.battle.logistics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Battle-scoped owner of persistent resupply caches. */
public final class ResupplyService {

    private final List<ResupplyCache> caches = new ArrayList<>();

    public void add(ResupplyCache cache) {
        if (cache != null) caches.add(cache);
    }

    /** The cache riding {@code carrierId}, or {@code null} if that vehicle carries none. */
    public ResupplyCache carriedBy(long carrierId) {
        for (ResupplyCache cache : caches) {
            if (cache.carrierId == carrierId) return cache;
        }
        return null;
    }

    public List<ResupplyCache> caches() {
        return Collections.unmodifiableList(caches);
    }
}
