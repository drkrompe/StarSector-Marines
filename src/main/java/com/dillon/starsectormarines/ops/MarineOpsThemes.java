package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.ui.Fonts;
import com.dillon.starsectormarines.ui.retained.style.StyleSheet;
import com.dillon.starsectormarines.ui.retained.style.UiTheme;

import java.awt.Color;
import java.util.Map;

/** Reusable final theme layers for Marine Ops retained documents. */
final class MarineOpsThemes {

    static final String SHEET_NAME = "marine-ops-theme";

    private MarineOpsThemes() {
    }

    static UiTheme standard() {
        return theme("""
                :root {
                    font-family: body;
                    color: #e4eefa;
                    background-color: #080d15;
                }
                .panel { background-color: #15202e; border-color: #6282a8; }
                .workbench-root, .fleet-armory-root { border-color: #6ed7ff; }
                .surface-dark { background-color: #0e1621; }
                .tone-edge { color: #6ed7ff; }
                .tone-muted { color: #8b9aaf; }
                .tone-accent { color: #ffd464; }
                .tone-good { color: #78d494; }
                .tone-danger { color: #e98b83; }
                button {
                    color: #e4eefa;
                    background-color: #1e3045;
                    border-color: #6282a8;
                    transition: background-color 140ms ease-out, border-color 140ms ease-out,
                                opacity 100ms linear;
                }
                button.selected { background-color: #315070; border-color: #6ed7ff; }
                button:hover { background-color: #2a4968; }
                button:active { background-color: #466f92; }
                button:focus-visible { border-color: #ffd464; }
                button:disabled { opacity: 0.38; }
                .good-surface { background-color: #132b22; border-color: #78d494; }
                .danger-surface { background-color: #3d2a26; border-color: #e98b83; }
                .edge-surface { background-color: #183b50; border-color: #6ed7ff; }
                """);
    }

    static UiTheme highContrast() {
        return theme("""
                :root {
                    font-family: body;
                    color: #ffffff;
                    background-color: #000000;
                }
                .panel { background-color: #071019; border-color: #d2f3ff; }
                .workbench-root, .fleet-armory-root { border-color: #63e8ff; }
                .surface-dark { background-color: #000000; }
                .tone-edge { color: #63e8ff; }
                .tone-muted { color: #c4d1df; }
                .tone-accent { color: #ffe45c; }
                .tone-good { color: #78ff9d; }
                .tone-danger { color: #ff9d91; }
                button {
                    color: #ffffff;
                    background-color: #102b42;
                    border-color: #d2f3ff;
                    transition: background-color 140ms ease-out, border-color 140ms ease-out,
                                opacity 100ms linear;
                }
                button.selected { background-color: #225b75; border-color: #63e8ff; }
                button:hover { background-color: #15577b; }
                button:active { background-color: #2185b4; }
                button:focus-visible { border-color: #ffe45c; }
                button:disabled { opacity: 0.32; }
                .good-surface { background-color: #06361b; border-color: #78ff9d; }
                .danger-surface { background-color: #501c16; border-color: #ff9d91; }
                .edge-surface { background-color: #06374b; border-color: #63e8ff; }
                """);
    }

    static CanvasPalette canvasPalette(boolean highContrast) {
        if (highContrast) {
            return new CanvasPalette(new Color(0x10, 0x2B, 0x42),
                    new Color(0x22, 0x5B, 0x75), new Color(0x58, 0x1E, 0x18),
                    new Color(0x06, 0x36, 0x1B), new Color(0x63, 0xE8, 0xFF),
                    new Color(0xD2, 0xF3, 0xFF), new Color(0x78, 0xFF, 0x9D),
                    new Color(0xFF, 0xE4, 0x5C), Color.WHITE,
                    new Color(0xC4, 0xD1, 0xDF));
        }
        return new CanvasPalette(new Color(0x1E, 0x30, 0x45),
                new Color(0x31, 0x50, 0x70), new Color(0x3D, 0x2A, 0x26),
                new Color(0x13, 0x2B, 0x22), new Color(0x6E, 0xD7, 0xFF),
                new Color(0x62, 0x82, 0xA8), new Color(0x78, 0xD4, 0x94),
                new Color(0xFF, 0xD4, 0x64), new Color(0xE4, 0xEE, 0xFA),
                new Color(0x8B, 0x9A, 0xAF));
    }

    private static UiTheme theme(String css) {
        return new UiTheme(StyleSheet.parse(SHEET_NAME, css),
                Map.of("body", Fonts.INSIGNIA_LARGE,
                        "heading", Fonts.ORBITRON_20_BOLD));
    }

    record CanvasPalette(Color button, Color selected, Color danger, Color valid,
                         Color edge, Color border, Color good, Color accent,
                         Color text, Color muted) {
    }
}
