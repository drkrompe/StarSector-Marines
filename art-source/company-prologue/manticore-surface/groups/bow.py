"""Bow: the two beak horns (red armour strakes). The bow head deck between them
is on the pods sheet (wings.py).

Each horn is port-authored with a starboard mirror: its outboard wall, its
inboard wall (facing in toward the bow gun and a little aft) and its top.
"""
from math import sqrt
from parts_common import UP_Z, DOWN_X, PORT, STARBOARD
from groups._mirror import box, pair

INBOARD = (-1/sqrt(5), -2/sqrt(5), 0.0)   # the port horn's inner wall: starboard and a little aft
SHOULDER = (0.0, 1/sqrt(5), 2/sqrt(5))    # the horn's raked top

PARTS = {}
pair(PARTS, 'horn-out', [('Hull bow {s} horn', box())], PORT, UP_Z, .7, 'beak horn outboard wall')
pair(PARTS, 'horn-in', [('Hull bow {s} horn', box())], INBOARD, UP_Z, .7,
     'beak horn inboard wall, facing the bow gun')
pair(PARTS, 'horn-top', [('Hull bow {s} horn', box())], SHOULDER, DOWN_X, .7, 'beak horn raked top')

SHEETS = {
    'bow': ['horn-out-port', 'horn-in-port', 'horn-top-port'],
}
