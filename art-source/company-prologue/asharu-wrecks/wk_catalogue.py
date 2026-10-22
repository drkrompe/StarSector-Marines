"""What the Asharu wreck kit holds: one row per variant. Plain Python.

Procedural rows give a family (wk_families), a native full size in metres (long, middle, short),
a seed and a livery. Each is built at that real size, so its plates, seams and frames are real
size too; `wreck_kit.pick` chooses the variant nearest a box and stretches it the rest of the way,
so the spread of sizes here keeps the stretch, and the detail's scale, within about a third.

Hulk rows derive a wreck from an accepted cinematic hull build (wk_hulks): stripped of turrets,
drives, lamps and effects, broken at a station along its length, its skin holed, at the hull's own
settled scale. A hulk is only chosen for a box it fills within HULK_FIT, so it is never shown at
a size its class does not have."""

# (name, family, (L, W, H) m, seed, livery)
PROCEDURAL = [
    # bows and sterns of broken frigates and destroyers (field 'hull' boxes: b/a .5-.65, c/a .4-.55)
    ('hullfrag-a', 'hullfrag', (22, 13, 11), 101, 'oxide'),
    ('hullfrag-b', 'hullfrag', (38, 23, 19), 102, 'bluegrey'),
    ('hullfrag-c', 'hullfrag', (60, 36, 29), 103, 'hegemony'),
    ('hullfrag-d', 'hullfrag', (85, 48, 38), 104, 'cream'),
    ('hullfrag-e', 'hullfrag', (120, 72, 58), 105, 'umber'),
    # open hull lengths (field 'section' boxes: b/a .65-.87, c/a .42-.66)
    ('section-a', 'section', (48, 40, 30), 201, 'grey'),
    ('section-b', 'section', (62, 43, 37), 202, 'oxide'),
    ('section-c', 'section', (80, 56, 40), 203, 'olive'),
    ('section-d', 'section', (95, 80, 46), 204, 'bone'),
    ('section-e', 'section', (115, 80, 70), 205, 'bluegrey'),
    ('section-f', 'section', (135, 100, 62), 206, 'hegemony'),
    ('section-g', 'section', (230, 165, 102), 207, 'umber'),
    # trusses and girders (field 'spar' boxes: b/a .02-.16; the stubby kinked arms .2-.45)
    ('spar-a', 'spar', (60, 4.0, 4.0), 301, 'grey'),
    ('spar-b', 'spar', (120, 5.0, 5.0), 302, 'grey'),
    ('spar-c', 'spar', (190, 5.5, 5.5), 303, 'grey'),
    ('spar-d', 'spar', (250, 7.0, 7.0), 304, 'grey'),
    ('spar-e', 'spar', (250, 13.0, 13.0), 305, 'grey'),
    ('spar-f', 'spar', (75, 10.5, 10.5), 306, 'grey'),
    ('spar-g', 'spar', (30, 4.5, 4.0), 307, 'grey'),
    ('spar-h', 'spar', (28, 8.5, 8.5), 308, 'bluegrey'),
    ('spar-i', 'spar', (60, 13.0, 13.0), 309, 'oxide'),
    ('spar-j', 'spar', (22, 9.5, 9.5), 310, 'hegemony'),
    ('spar-k', 'spar', (165, 11.0, 11.0), 311, 'grey'),
    ('spar-l', 'spar', (75, 7.5, 7.5), 312, 'grey'),
    ('spar-m', 'spar', (48, 4.6, 4.6), 313, 'grey'),
    ('spar-n', 'spar', (200, 3.4, 3.4), 314, 'grey'),
    # armour plating (field 'plate' boxes: c/a .02-.07)
    ('plate-a', 'plate', (22, 14, 1.6), 401, 'oxide'),
    ('plate-b', 'plate', (30, 24, 2.0), 402, 'cream'),
    ('plate-c', 'plate', (50, 30, 1.8), 403, 'bluegrey'),
    ('plate-d', 'plate', (55, 48, 2.2), 404, 'hegemony'),
    ('plate-e', 'plate', (80, 52, 2.4), 405, 'bone'),
    ('plate-f', 'plate', (82, 78, 2.8), 406, 'umber'),
    ('plate-g', 'plate', (85, 52, 5.5), 407, 'grey'),
    ('plate-h', 'plate', (78, 60, 1.2), 408, 'oxide'),
    ('plate-i', 'plate', (50, 27, 1.0), 409, 'bluegrey'),
    ('plate-j', 'plate', (60, 36, 3.6), 410, 'cream'),
    # modules (field 'chunk' boxes: near cubic)
    ('chunk-a', 'chunk', (45, 40, 33), 501, 'olive'),
    ('chunk-b', 'chunk', (72, 66, 54), 502, 'cream'),
    ('chunk-c', 'chunk', (104, 95, 78), 503, 'oxide'),
]

# (name, source build key, kept span along the hull's own X as fractions of its length from the stern
#  (0) to the bow (1), which ends are torn, seed). A hulk's size is its hull's, at its settled scale.
HULKS = [
    ('hulk-onslaught-fore', 'onslaught', (.38, 1.0), ('aft',), 601),
    ('hulk-onslaught-whole', 'onslaught', (.07, 1.0), ('aft',), 602),
    ('hulk-legion-fore', 'legion', (.45, 1.0), ('aft',), 611),
    ('hulk-legion-aft', 'legion', (0.0, .52), ('fore',), 612),
    ('hulk-colossus', 'colossus', (.1, 1.0), ('aft',), 621),
    ('hulk-colossus-hold', 'colossus', (.25, .8), ('aft', 'fore'), 622),
]

# The accepted builds the hulks are derived from (Drive builds/<asset>/<commit>/)
SOURCES = {
    'onslaught': dict(drive='builds/onslaught/0cb9d83', file='onslaught.blend', collection='ONSLAUGHT | clean',
                      root='ONSLAUGHT | clean | root', length_m=301.0, livery_note='Hegemony XIV'),
    'legion': dict(drive='builds/legion/0cb9d83', file='legion.blend', collection='LEGION', root='LEGION | root',
                   length_m=292.0, livery_note='XIV grey'),
    'colossus': dict(drive='builds/colossus/139e8b8b6', file='colossus.blend', collection='COLOSSUS',
                     root='COLOSSUS | root', length_m=108.5, livery_note='oxide red freighter'),
}

# A hulk is chosen only when every axis of the box is within this factor of the hulk's own size
HULK_FIT = (.86, 1.16)
