"""Shared direction constants for the group part tables (groups/<group>.py)."""
from math import sqrt

HULL = 'Main hull - level datum Z0'
PORT = (0.0, 1.0, 0.0)
STARBOARD = (0.0, -1.0, 0.0)
UP_Z = (0.0, 0.0, 1.0)
DOWN_Z = (0.0, 0.0, -1.0)
BOW = (1.0, 0.0, 0.0)
STERN = (-1.0, 0.0, 0.0)
DOWN_X = (1.0, 0.0, 0.0)   # screen-up for top views: bow at the top
BOW_QUARTER = (sqrt(.5), sqrt(.5), 0.0)
BOW_QUARTER_STBD = (sqrt(.5), -sqrt(.5), 0.0)
CHEEK = (0.50, 0.36, 0.79)  # sw_hull 'Port bow cheek': (.81, .58) at 38 deg below horizontal
CHEEK_STBD = (0.50, -0.36, 0.79)
