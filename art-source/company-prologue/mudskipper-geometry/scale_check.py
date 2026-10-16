"""Pass 4: derive the Mudskipper's one metres_per_unit from what she carries.

Run: blender --background mudskipper-geometry.blend --python scale_check.py
Prints JSON. The required volume comes from the mod's deck program, printed by
.claude/skills/ship-model-pipeline/DeckProgramProbe.java:
  FRIGATE "Civilian Transport" (LINER) crew 5-100 cargo 70 -> 3,329 m2 of rooms,
  x1.40 circulation (DeckSizing.CIRCULATION) x 3.2 m deck height = 14,914 m3.
The usable share of a pressure hull is taken as 55% (the Second Wind's figure),
with 45% (roomy) and 65% (cramped) bounding the range.
"""
import json
import bmesh
import bpy

ROOMS_M2 = 3329
CIRCULATION = 1.40
DECK_HEIGHT_M = 3.2
REQUIRED_M3 = ROOMS_M2 * CIRCULATION * DECK_HEIGHT_M
USABLE = {'cramped': .65, 'nominal': .55, 'roomy': .45}
# The pressure hull proper, plus the separate solids a crew would also live in:
# the sponsons (ported), the dorsal cabin hood and the bow cockpit.
MAIN = 'Main hull - level datum Z0'
EXTRA = ['Port forebody sponson', 'Starboard forebody sponson', 'Dorsal cabin hood', 'Bow cockpit block']


def volume(name):
    ob = bpy.data.objects[name]
    me = ob.evaluated_get(bpy.context.evaluated_depsgraph_get()).to_mesh()
    bm = bmesh.new()
    bm.from_mesh(me)
    bm.transform(ob.matrix_world)
    v = abs(bm.calc_volume(signed=True))
    bm.free()
    return v


main = volume(MAIN)
extra = sum(volume(n) for n in EXTRA)
length = max((ob.matrix_world @ v.co).x for ob in bpy.data.objects if ob.type == 'MESH' for v in ob.data.vertices) \
    - min((ob.matrix_world @ v.co).x for ob in bpy.data.objects if ob.type == 'MESH' for v in ob.data.vertices)
scale = lambda vol, use: (REQUIRED_M3/(vol*use))**(1/3)
out = {
    'required_m3': round(REQUIRED_M3),
    'main_hull_units3': round(main),
    'with_sponsons_hood_cockpit_units3': round(main+extra),
    'metres_per_unit_main_hull': {k: round(scale(main, u), 3) for k, u in USABLE.items()},
    'metres_per_unit_all_pressure': {k: round(scale(main+extra, u), 3) for k, u in USABLE.items()},
    'length_units': round(length, 2),
}
print('SCALE ' + json.dumps(out))
