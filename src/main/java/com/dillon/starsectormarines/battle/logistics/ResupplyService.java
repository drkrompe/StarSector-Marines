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

    public List<ResupplyCache> caches() {
        return Collections.unmodifiableList(caches);
    }
}
