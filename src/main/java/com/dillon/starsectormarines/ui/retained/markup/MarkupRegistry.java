package com.dillon.starsectormarines.ui.retained.markup;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Named parsed templates currently available to the component builder. */
public final class MarkupRegistry {
    private Map<String, MarkupTemplate> templates = Map.of();

    public MarkupTemplate template(String name) {
        return templates.get(name);
    }

    public Set<String> names() {
        return templates.keySet();
    }

    public void replaceAll(Collection<MarkupTemplate> replacements) {
        Map<String, MarkupTemplate> next = new LinkedHashMap<>();
        for (MarkupTemplate template : replacements) {
            MarkupTemplate previous = next.put(template.name(), template);
            if (previous != null) {
                throw new UiMarkupException("Two component files define <" + template.name() + ">.");
            }
        }
        templates = Map.copyOf(next);
    }
}
