"""Writes the direct-control HUD's item icons: hand grenade, smoke grenade, integral shield, flamer fuel.

They sit beside the hand-drawn round icons (round-shell, round-missile, ...) in
mod/graphics/ui/direct-control/ and follow their conventions: flat silhouettes at
strip height, one file a state. A thrown item and the shield are live / loading /
spent like a round; a fuel charge is charged / charging / empty like an energy cell,
because the flamer's tank is drawn through the same gauge.

    python art-source/direct-control-icons/item_icons.py [--style colour|steel|glyph] [--out DIR]

`colour` is the shipped style. The others were the alternatives put to the owner
(2026-10-24); they stay here so the comparison can be rendered again.
"""
import argparse
import pathlib

DEFAULT_OUT = pathlib.Path(__file__).resolve().parents[2] / "mod" / "graphics" / "ui" / "direct-control"

# Per style, per item: body fill, body stroke, accent (band / spoon / chevron), highlight.
STYLES = {
    "colour": {
        "grenade": ("#6f8a3c", "#34441a", "#b8c0c4", "#a8c06a"),
        "smoke": ("#8f9aa1", "#3e464c", "#eef2f4", "#c3cbd0"),
        "shield": ("#2f8fd8", "#164a70", "#c8f4ff", "#7cc8ff"),
        "fuel": ("#ff8a2a", "#ffd2a0", "#ffe9c4", "#20262a"),
    },
    "steel": {
        "grenade": ("#e4eaee", "#20262a", "#d8573a", "#ffffff"),
        "smoke": ("#e4eaee", "#20262a", "#8f9aa1", "#ffffff"),
        "shield": ("#e4eaee", "#20262a", "#3fb0ff", "#ffffff"),
        "fuel": ("#e9a24a", "#e4eaee", "#ffe9c4", "#20262a"),
    },
    "glyph": {
        "grenade": ("#c8e2f2", "#c8e2f2", "#24323b", "#c8e2f2"),
        "smoke": ("#c8e2f2", "#c8e2f2", "#24323b", "#c8e2f2"),
        "shield": ("#c8e2f2", "#c8e2f2", "#24323b", "#c8e2f2"),
        "fuel": ("#f2e2b0", "#f2e2b0", "#3b3524", "#20262a"),
    },
}

SPENT_STROKE = "#969ea2"
LOADING_OPACITY = 0.6

# Outline shapes, shared by every style and by the spent outline.
GRENADE_BODY = "M6 5.2 C9.6 5.2 11.2 8.4 11.2 12.4 C11.2 16.6 9.2 19.2 6 19.2 C2.8 19.2 0.8 16.6 0.8 12.4 C0.8 8.4 2.4 5.2 6 5.2 Z"
SMOKE_BODY = "M1 4.6 L9 4.6 L9 18.2 Q9 19.2 8 19.2 L2 19.2 Q1 19.2 1 18.2 Z"
SHIELD_BODY = "M8 0.8 L15.2 3.4 L15.2 9.6 C15.2 14.4 12 17.4 8 19.2 C4 17.4 0.8 14.4 0.8 9.6 L0.8 3.4 Z"
FUEL_BODY = "M0.6 3.2 Q0.6 2 1.8 2 L6.2 2 Q7.4 2 7.4 3.2 L7.4 16.2 Q7.4 17.4 6.2 17.4 L1.8 17.4 Q0.6 17.4 0.6 16.2 Z"

VIEWBOX = {"grenade": "0 0 12 20", "smoke": "0 0 10 20", "shield": "0 0 16 20", "fuel": "0 0 8 18"}


def attrs(fill, stroke, width, opacity):
    o = "" if opacity >= 1 else f' fill-opacity="{opacity}" stroke-opacity="{opacity}"'
    s = f' stroke="{stroke}" stroke-width="{width}"' if stroke else ""
    return f'fill="{fill}"{s}{o}'


def grenade(c, opacity, spent):
    if spent:
        return (f'<path d="{GRENADE_BODY}" fill="none" stroke="{SPENT_STROKE}" stroke-width="1.1"/>'
                f'<rect x="4" y="1.6" width="4" height="3.6" fill="none" stroke="{SPENT_STROKE}" stroke-width="0.9"/>')
    body, edge, accent, light = c
    glyph = body == edge
    seams = (f'<path d="M1.4 10.6 L10.6 10.6 M1.2 14.6 L10.8 14.6 M6 5.6 L6 18.8" stroke="{accent if glyph else edge}"'
             f' stroke-width="0.8" stroke-opacity="{0.9 * opacity:.2f}"/>')
    return (f'<path d="{GRENADE_BODY}" {attrs(body, None if glyph else edge, 0.9, opacity)}/>'
            + ("" if glyph else f'<path d="M3 8.4 Q3.4 7 4.6 6.6 L4.6 16 Q3 15 2.8 12.4 Z" fill="{light}" opacity="{0.55 * opacity:.2f}"/>')
            + seams
            + f'<rect x="4" y="1.6" width="4" height="3.6" {attrs(accent if not glyph else body, None if glyph else edge, 0.7, opacity)}/>'
            + f'<path d="M8 2.2 L10.4 3 L10.4 11" fill="none" stroke="{accent if not glyph else body}" stroke-width="1.2" stroke-opacity="{opacity}"/>'
            + f'<circle cx="2.6" cy="2.8" r="1.5" fill="none" stroke="{accent if not glyph else body}" stroke-width="0.8" stroke-opacity="{opacity}"/>')


def smoke(c, opacity, spent):
    if spent:
        return (f'<path d="{SMOKE_BODY}" fill="none" stroke="{SPENT_STROKE}" stroke-width="1.1"/>'
                f'<rect x="2.6" y="1" width="4.8" height="3.4" fill="none" stroke="{SPENT_STROKE}" stroke-width="0.9"/>')
    body, edge, accent, light = c
    glyph = body == edge
    return (f'<rect x="2.6" y="1" width="4.8" height="3.6" {attrs(body if glyph else light, None if glyph else edge, 0.7, opacity)}/>'
            f'<path d="{SMOKE_BODY}" {attrs(body, None if glyph else edge, 0.9, opacity)}/>'
            f'<rect x="1.4" y="8" width="7.2" height="2.6" fill="{accent}" fill-opacity="{opacity}"/>'
            f'<rect x="1.4" y="13.6" width="7.2" height="1.2" fill="{accent}" fill-opacity="{0.7 * opacity:.2f}"/>'
            + ("" if glyph else f'<rect x="2.2" y="11.4" width="1.4" height="6.6" fill="{light}" opacity="{0.5 * opacity:.2f}"/>'))


def shield(c, opacity, spent):
    if spent:
        return f'<path d="{SHIELD_BODY}" fill="none" stroke="{SPENT_STROKE}" stroke-width="1.2"/>'
    body, edge, accent, light = c
    glyph = body == edge
    return (f'<path d="{SHIELD_BODY}" {attrs(body, None if glyph else edge, 1.0, opacity)}/>'
            + ("" if glyph else f'<path d="M8 2.8 L13.2 4.7 L13.2 9.4 C13.2 11.4 12.4 13 11.2 14.2 Z" fill="{light}" opacity="{0.45 * opacity:.2f}"/>')
            + f'<path d="M3.6 7.6 L8 5.4 L12.4 7.6 M3.6 11.4 L8 9.2 L12.4 11.4" fill="none" stroke="{accent}"'
              f' stroke-width="1.3" stroke-linecap="round" stroke-linejoin="round" stroke-opacity="{opacity}"/>')


def fuel(c, state):
    fill, edge, light, back = c
    shell = (f'<rect x="2.6" y="0.4" width="2.8" height="1.8" fill="{back}" fill-opacity="0.55" stroke="{edge if state != "empty" else "#5c6c76"}" stroke-width="0.7"/>'
             f'<path d="{FUEL_BODY}" fill="{back}" fill-opacity="0.55" stroke="{edge if state != "empty" else "#5c6c76"}" stroke-width="1"/>')
    if state == "charged":
        return (shell + f'<rect x="1.7" y="3.2" width="4.6" height="13" rx="1.1" fill="{fill}"/>'
                f'<path d="M4 6 Q5.6 8.2 5 10.2 Q4.6 11.8 4 12 Q3.4 11.8 3 10.2 Q2.4 8.2 4 6 Z" fill="{light}" fill-opacity="0.85"/>')
    if state == "charging":
        return shell + f'<rect x="1.7" y="9.6" width="4.6" height="6.6" rx="1.1" fill="{fill}" fill-opacity="0.75"/>'
    return shell


def write(style, out):
    out.mkdir(parents=True, exist_ok=True)
    palette = STYLES[style]
    files = {}
    for name, draw in (("grenade", grenade), ("smoke", smoke), ("shield", shield)):
        for state in ("live", "loading", "spent"):
            body = draw(palette[name], LOADING_OPACITY if state == "loading" else 1.0, state == "spent")
            files[f"round-{name}-{state}.svg"] = (name, body)
    for state in ("charged", "charging", "empty"):
        files[f"round-fuel-{state}.svg"] = ("fuel", fuel(palette["fuel"], state))
    for filename, (name, body) in files.items():
        svg = f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="{VIEWBOX[name]}">{body}</svg>\n'
        (out / filename).write_text(svg, encoding="utf-8", newline="\n")
    return sorted(files)


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--style", choices=sorted(STYLES), default="colour")
    parser.add_argument("--out", type=pathlib.Path, default=DEFAULT_OUT)
    args = parser.parse_args()
    for name in write(args.style, args.out):
        print(args.out / name)


if __name__ == "__main__":
    main()
