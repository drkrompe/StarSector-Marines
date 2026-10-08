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
                    color: #e4e7df;
                    background-color: #111516;
                }
                .heading { font-family: heading; }
                .title { font-family: title; }
                .panel {
                    background-color: #292f30;
                    border-color: #727b79;
                    border-style: outset;
                    background-image: linear-gradient(to bottom, #ffffff0c 0%, #ffffff00 18%, #00000024 100%);
                }
                .workbench-root, .fleet-armory-root, .fleet-armory-overview-root, .company-hq-root,
                .mission-select-root, .mission-briefing-root, .squad-deployment-root,
                .stationing-root, .mission-results-root, .mission-loot-root,
                .mech-lab-root, .boat-deck-root {
                    border-color: #727b79;
                    border-style: outset;
                }
                .surface-dark {
                    background-color: #101919;
                    background-image: none;
                    border-style: inset;
                }
                .tone-edge { color: #a7ccc4; }
                .tone-muted { color: #a0ada8; }
                .tone-accent { color: #edc273; }
                .tone-good { color: #78d494; }
                .tone-danger { color: #e98b83; }
                button {
                    color: #e4e7df;
                    background-color: #3c4444;
                    border-color: #8d9690;
                    border-style: outset;
                    background-image: linear-gradient(to bottom, #ffffff1b 0%, #ffffff06 46%, #00000010 48%, #00000030 100%);
                    transition: background-color 140ms ease-out, border-color 140ms ease-out,
                                opacity 100ms linear;
                }
                button.selected { background-color: #494431; border-color: #edc273; border-style: inset; }
                button.power-armed { background-color: #4a3c20; border-color: #edc273; color: #edc273; border-style: inset; }
                button:hover { background-color: #505b58; }
                button:active { background-color: #222c2a; border-style: inset; }
                button:focus-visible { border-color: #ffe1a0; border-style: solid; }
                button:disabled { opacity: 0.38; }
                .good-surface { background-color: #132b22; border-color: #78d494; }
                .warning-surface { background-color: #332b1c; border-color: #ffd464; color: #ffd464; }
                .danger-surface { background-color: #3d2a26; border-color: #e98b83; }
                .edge-surface { background-color: #253b38; border-color: #a7ccc4; }
                .company-card-ready { border-color: #78d494; }
                .company-card-operational { border-color: #ffd464; }
                .company-card-unready { border-color: #e98b83; }
                /* Dossiers are steel enclosures around a dark readout, not giant keys. */
                .squad-card, .organization-target, .company-card {
                    background-color: #2e3736;
                    border-style: outset;
                    background-image: linear-gradient(to bottom, #ffffff12 0%, #ffffff00 12%, #00000026 100%);
                }
                .squad-card.organizing { border-color: #727b79; }
                .organization-target:hover, .company-card:hover { background-color: #46524d; }
                .organization-target.selected, .company-card.selected {
                    background-color: #494431;
                    border-style: inset;
                }
                .organization-target:active, .company-card:active {
                    background-color: #222c2a;
                    border-style: inset;
                }
                .squad-card.selected {
                    background-color: #3d3b2e;
                    border-color: #edc273;
                    border-style: inset;
                }
                .squad-open { background-color: transparent; background-image: none; }
                .squad-open:hover { background-color: #ffffff08; }
                .squad-command-block, .squad-card-status {
                    background-color: #121e1c;
                    border-color: #657771;
                    border-style: inset;
                }
                #marine-ops-page-nav {
                    background-color: #444c4b;
                    border-color: #929a94;
                    border-style: outset;
                    background-image: linear-gradient(to bottom, #ffffff24 0%, #ffffff08 45%, #00000020 48%, #00000044 100%);
                }
                #marine-ops-page-context {
                    background-color: #111b18;
                    color: #b8c9ad;
                    border-color: #727d73;
                    border-style: inset;
                }
                #marine-ops-page-routes .page-nav-current {
                    background-color: #393323;
                    border-color: #edc273;
                    color: #ffda8b;
                    border-style: inset;
                    background-image: linear-gradient(to bottom, #00000050 0%, #00000000 100%);
                }
                #marine-ops-page-routes .page-nav-current:hover { background-color: #50452e; }
                """);
    }

    static UiTheme highContrast() {
        return theme("""
                :root {
                    font-family: body;
                    color: #ffffff;
                    background-color: #000000;
                }
                .heading { font-family: heading; }
                .title { font-family: title; }
                .panel { background-color: #071019; border-color: #d2f3ff; }
                .workbench-root, .fleet-armory-root, .fleet-armory-overview-root, .company-hq-root,
                .mission-select-root, .mission-briefing-root, .squad-deployment-root,
                .stationing-root, .mission-results-root, .mission-loot-root { border-color: #63e8ff; }
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
                .squad-card.organizing { background-color: #071019; border-color: #d2f3ff; }
                .squad-card.selected { background-color: #225b75; border-color: #ffe45c; }
                button.power-armed { background-color: #3a3018; border-color: #ffe45c; color: #ffe45c; }
                button:hover { background-color: #15577b; }
                button:active { background-color: #2185b4; }
                button:focus-visible { border-color: #ffe45c; }
                button:disabled { opacity: 0.32; }
                .good-surface { background-color: #06361b; border-color: #78ff9d; }
                .warning-surface { background-color: #3a3018; border-color: #ffe45c; color: #ffe45c; }
                .danger-surface { background-color: #501c16; border-color: #ff9d91; }
                .edge-surface { background-color: #06374b; border-color: #63e8ff; }
                .company-card-ready { border-color: #78ff9d; }
                .company-card-operational { border-color: #ffe45c; }
                .company-card-unready { border-color: #ff9d91; }
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
        return new CanvasPalette(new Color(0x3C, 0x44, 0x44),
                new Color(0x49, 0x44, 0x31), new Color(0x3D, 0x2A, 0x26),
                new Color(0x13, 0x2B, 0x22), new Color(0xA7, 0xCC, 0xC4),
                new Color(0x72, 0x7B, 0x79), new Color(0x78, 0xD4, 0x94),
                new Color(0xED, 0xC2, 0x73), new Color(0xE4, 0xE7, 0xDF),
                new Color(0xA0, 0xAD, 0xA8));
    }

    private static UiTheme theme(String css) {
        return new UiTheme(StyleSheet.parse(SHEET_NAME, css),
                Map.of("body", Fonts.INSIGNIA_15_AA,
                        "heading", Fonts.ORBITRON_12_BOLD,
                        "compact", Fonts.ORBITRON_10,
                        "title", Fonts.ORBITRON_16));
    }

    record CanvasPalette(Color button, Color selected, Color danger, Color valid,
                         Color edge, Color border, Color good, Color accent,
                         Color text, Color muted) {
    }
}
