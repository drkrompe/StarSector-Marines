"""Remove the fighter build's review furniture from a surfacing build copy.

blender --background <build>/<id>-geometry.blend --python strip_review_props.py

The geometry driver saves the 1.8 m scale figure, the pilot proxy and (for
the Wasp) the parked deck plane in its .blend; they serve its cockpit-fit,
scale and parked reviews. A surfacing run must
not see them: the figure would be painted into the finish reference, and
both would carry an unclassed material. Only the build copy under the
surfacing folder's build/ is changed; the geometry package is never edited.
"""
import bpy

gone = [o for o in bpy.data.objects
        if o.name.startswith(('Scale figure', 'Pilot proxy', 'Parked deck plane'))]
for o in gone:
    bpy.data.objects.remove(o, do_unlink=True)
bpy.ops.wm.save_mainfile()
print('SURF stripped', len(gone), 'review props')
