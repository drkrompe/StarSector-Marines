package com.dillon.starsectormarines.engine.ecs;

import java.util.ArrayList;
import java.util.List;

/**
 * A cached match over archetype tables: every table whose mask includes all
 * {@code required} bits and none of the {@code excluded} bits. The matched list is
 * rebuilt only when the world creates a <em>new</em> archetype table (rare — a
 * row's count changing does not invalidate it, since the list holds the table by
 * reference), so steady-state use is a list walk. Hold one across ticks to keep
 * the cache warm; obtain via {@link EntityWorld#query}, iterate via
 * {@link EntityWorld#matched(Query)}.
 */
public final class Query {
    final long required;
    final long excluded;
    final List<ArchetypeTable> matched = new ArrayList<>();
    int seenVersion = -1;

    Query(long required, long excluded) {
        this.required = required;
        this.excluded = excluded;
    }
}
