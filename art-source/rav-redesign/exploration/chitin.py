"""Chitin pass (owner, 2026-10-21: "more browns creams and chitin").

The swarm does not own red. Two body-plan families are re-materialised as
lacquered chitin in browns and creams, and made readable by value structure
rather than hue: dark plates, cream sutures and rims, a lacquer sheen, and one
small amber accent.

  D1 CHITIN TIDE   B's silhouettes (streak Runner with segmented whips,
                   plated raft Brute, sac-ball Spitter), armoured
  D2 CHITIN LANCE  C's articulated limbs (switchblade lance Runner, seed-ram
                   Brute, launch-tube Spitter), in the same palette

Each builder wraps the B/C rig with the chitin palette and then marks every
plate with a cream rim and sheen.
"""
from rig import poly_xform
from creatures import (B_brute, B_matriarch, B_runner, B_spitter, C_brute, C_matriarch, C_runner, C_spitter,
                       DIRECTIONS, PAL)

CHITIN = dict(
    plate=(44, 32, 23),      # dark lacquered chitin, the body's main value
    plate2=(62, 45, 31),     # lighter plate / limb
    deep=(26, 19, 14),       # inner core, shadow
    cream=(234, 218, 182),   # sutures, rims, joints, edges
    bone=(214, 194, 150),    # calcified plaques, blades
    leg=(56, 41, 29),        # walking appendages: dark, so they add mass instead of matching the paving
    amber=(255, 168, 54),    # the one accent: small glow nodes
    ochre=(196, 142, 64),    # Spitter sac
    creep=(76, 68, 60),      # proposed creep: duller than everything, between the plates and the paving in value
    creep2=(46, 40, 34),     # creep ridges: dark, like shed plates
    creep_node=(150, 104, 44),  # rare dim amber specks in creep
)

PAL["D1"] = dict(flesh=CHITIN["plate"], core=CHITIN["deep"], float=CHITIN["amber"], sting=CHITIN["cream"],
                 fringe=CHITIN["leg"], dark=CHITIN["deep"], plaque=CHITIN["bone"],
                 sac=CHITIN["plate"], sac_glow=0.0, edge=CHITIN["cream"], sutures=CHITIN["cream"],
                 suture_w=0.03, head=CHITIN["bone"], barb=1.8,
                 creep=CHITIN["creep"], creep2=CHITIN["creep2"])
PAL["D2"] = dict(husk=CHITIN["plate"], husk2=CHITIN["plate2"], bone=CHITIN["cream"], joint=CHITIN["bone"],
                 pod=CHITIN["amber"], dark=CHITIN["deep"], whisker=CHITIN["cream"], edge=CHITIN["cream"],
                 creep=CHITIN["creep"], creep2=CHITIN["creep2"])

ARMOUR = {CHITIN["plate"], CHITIN["plate2"], CHITIN["deep"]}


def chitinize(builder, key, rim=0.016):
    P = PAL[key]

    def build(S):
        parts = builder(S, P)
        for p in parts:
            if p.kind == "solid" and tuple(p.color[:3]) in ARMOUR and p.outline > 0:
                p.edge = P["edge"]
                p.outline = max(p.outline * 0.7, rim * 0.6)
                # keep the plate dark at zoom: little midtone lift, one small specular streak
                p.sheen = 0.6
                p.hi = 0.5
            elif p.kind == "solid" and tuple(p.color[:3]) == CHITIN["amber"]:
                # the accent stays small: a node, not a lantern
                p.pts = poly_xform(p.pts, 0.5)
                p.glow = min(p.glow, 0.45)
                p.shadow = False
        return parts

    return build


DIRECTIONS["D1"] = dict(name="CHITIN TIDE", runner=chitinize(B_runner, "D1"), brute=chitinize(B_brute, "D1"),
                        spitter=chitinize(B_spitter, "D1"), matriarch=chitinize(B_matriarch, "D1"))
DIRECTIONS["D2"] = dict(name="CHITIN LANCE", runner=chitinize(C_runner, "D2"), brute=chitinize(C_brute, "D2"),
                        spitter=chitinize(C_spitter, "D2"), matriarch=chitinize(C_matriarch, "D2"))

# colours a GIF palette must keep even though they cover few pixels
PAL["D1_accents"] = [CHITIN["amber"], (255, 206, 120), CHITIN["cream"], CHITIN["bone"]]
PAL["D2_accents"] = PAL["D1_accents"]
