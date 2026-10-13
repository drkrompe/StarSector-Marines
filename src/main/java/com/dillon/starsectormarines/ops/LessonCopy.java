package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.i18n.Strings;
import java.text.MessageFormat;

/** Whole translated sentences; arguments are values, never English sentence fragments. */
final class LessonCopy {
    private LessonCopy() { }
    static String text(String key, Object... arguments) {
        String pattern = Strings.get(key);
        return arguments.length == 0 ? pattern : MessageFormat.format(pattern, arguments);
    }
}
