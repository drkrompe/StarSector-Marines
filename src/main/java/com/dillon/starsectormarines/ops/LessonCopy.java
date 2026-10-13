package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.i18n.Strings;

/** Whole translated sentences; arguments are values, never English sentence fragments. */
final class LessonCopy {
    private LessonCopy() { }
    static String text(String key, Object... arguments) {
        return Strings.format(key, arguments);
    }
}
