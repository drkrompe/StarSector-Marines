# Yard storybook page art

Status: DRAFT — awaiting the owner's review (2026-10-20)
Written: 2026-10-20

One picture per page of the four-page yard storybook that follows the Jangala arrival
cinematic. Each carries its page's meaning rather than only its place.

| Page | Image id | Layers | Meaning |
|---|---|---|---|
| 1 What it costs | `yard-chits` | `p1-counter-bg-v1.png` (Nessa's office counter, the yard and Second Wind through the window, no people) + `p1-castle-hand-v1.png` (Castle's hand on transparency) | Three chits down in a row, the fourth (threads from the jacket lining still on it) held over the gap. The open med kit and the slit lining say where they came from. |
| 2 Twenty-two days | `yard-tally` | `p2-tally-v1.png`, an edit of `../berths-fitted-v2.png` | The converted hold full of families and Sabine's section, and twenty-two chalk strokes on the bunk pillar: the Claim's chalked count of days begun again. |
| 3 The road up | `yard-road` | `p3-road-v2.png` (an edit of `-v1` emptying the racks) | Nessa's half-empty parts racks, the last Wayline hauler down shot through, its driver head-down, Mara with her hand on the cab, and the empty road up into the highlands with smoke on it. |
| 4 Ore dust | `yard-ore-dust` | `p4-ore-dust-v1.png`, painted over `../../story-stills/guides/p5-port-bay.png` | One sack of ore dust strapped down on a whole flatbed, a dozen of Sabine's escorts riding with it, Sabine counting them aboard. |

`python compose_storybook.py` writes full-size composites to `build/yard-storybook/`;
`--pack` also writes the 1200x800 JPEGs under the image ids above. Page 1's hand
came back larger than briefed and is scaled and placed by the script; pages 2 and 4
are cropped toward their subject.

People follow the layering rule (owner, 2026-10-19): no foreground character is
painted into a background. Page 1's hand is its own cut-out; the people on pages
2-4 are mid-distance figures, like Y1's yard hands.
