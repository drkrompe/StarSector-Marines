package com.dillon.starsectormarines.campaign;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Durable, namespaced campaign facts a finished side objective leaves behind ({@code side-objective-toolkit.md}),
 * such as {@code corvus.harrow.coupling_delivered}, for later missions, dialogue and offers to read. Settlement writes
 * them from the mission outcome, never from a live battle. A mark is permanent until a story clears it.
 */
public final class CampaignMarks implements Serializable {
    /** Lower-case dotted words with a namespace first. */
    private static final Pattern MARK = Pattern.compile("[a-z0-9_]+(\\.[a-z0-9_]+)+");

    private final LinkedHashSet<String> marks = new LinkedHashSet<>();

    /** Whether {@code mark} is a well-formed mark: lower-case dotted words, namespace first. */
    public static boolean valid(String mark) { return mark != null && MARK.matcher(mark).matches(); }

    /** Writes a mark; writing one already kept changes nothing. @return whether it was new */
    public boolean write(String mark) {
        if (!valid(mark))
            throw new IllegalArgumentException("a campaign mark is namespaced lower-case words: " + mark);
        return marks.add(mark);
    }

    public boolean has(String mark) { return marks.contains(mark); }

    /** Clears a mark, for the story that retires it. @return whether it was kept */
    public boolean clear(String mark) { return marks.remove(mark); }

    /** Every mark under {@code namespace} ({@code corvus} or {@code corvus.harrow}), in the order written. */
    public List<String> under(String namespace) {
        List<String> out = new ArrayList<>();
        for (String mark : marks) if (mark.startsWith(namespace + ".")) out.add(mark);
        return out;
    }

    /** Every mark, in the order written. */
    public Set<String> all() { return Collections.unmodifiableSet(marks); }
}
