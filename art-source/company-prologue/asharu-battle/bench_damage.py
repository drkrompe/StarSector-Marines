"""Bench tests for progressive battle damage (asharu_damage.py, asharu-damage.md).

The lead Lasher at two points in the battle, each copy given asharu_damage.apply as battle_blockout
calls it, with its own frame_of, so one bench frame shows both moments.
"""
F = 48           # the bench frame that is each copy's sequence time
LATER = 5.0      # lead-lasher's second frame, this many seconds on: embers cooling, fires burning on

POINTS = (('lead-58', 58.0, 40.0), ('lead-110', 110.0, -40.0))     # (copy, sequence time, height)


def lead_lasher(bench):
    """The lead Lasher just after S6 (58 s: holed and burning forward, venting aft) above, and at the
    end of S9b (110 s: the S6 hits cooled to scars, her aft deck and stern burning) below. Port side
    and deck, from above her port beam."""
    import asharu_damage
    import importlib
    importlib.reload(asharu_damage)
    for key, t, z in POINTS:
        s = bench.ship(key, 'lasher', at=(0, 0, z), heading=0, throttle=.3)
        bench.bpy.context.view_layer.update()
        frame_of = (lambda t_: lambda u: F + round((u - t_) * 24))(t)
        asharu_damage.apply('lasher-lead', s, t, t, frame_of, s.matrix, log=lambda m: print(m, flush=True), look='v1')
    bench.camera((-20, 230, 95), (-4, 0, 0), 40)
    bench.scene.cycles.volume_step_rate = 2.0
    bench.scene.cycles.volume_max_steps = 256


# before-after: v2 (left column) against v1 (right column); the camera looks from the port side, so +X is
# screen-left, 58 s (top row) and 110 s (bottom row).
# Frame F frames the four from as far as the shots see a Lasher (one hull about a tenth of the
# frame's width); frame F + 1 is the close view.
GRID = [(f'{look}-{int(t)}', look, t, x, z) for look, x in (('v1', -60.0), ('v2', 60.0))
        for t, z in ((58.0, 32.0), (110.0, -32.0))]


def before_after(bench):
    import asharu_damage
    import importlib
    from mathutils import Vector
    importlib.reload(asharu_damage)
    for key, look, t, x, z in GRID:
        s = bench.ship(key, 'lasher', at=(x, 0, z), heading=0, throttle=.3)
        bench.bpy.context.view_layer.update()
        frame_of = (lambda t_: lambda u: F + round((u - t_) * 24))(t)
        asharu_damage.apply('lasher-lead', s, t, t, frame_of, s.matrix, log=lambda m: print(m, flush=True),
                            look=look)
    cam = bench.cam
    aim = Vector((0, 0, 0))
    for f, loc, lens in ((F, (-260, 820, 330), 35), (F + 1, (-60, 250, 105), 40)):
        bench.camera(loc, tuple(aim), lens)
        cam.keyframe_insert('location', frame=f)
        cam.keyframe_insert('rotation_euler', frame=f)
        cam.data.keyframe_insert('lens', frame=f)
    bench.scene.cycles.volume_step_rate = 2.0
    bench.scene.cycles.volume_max_steps = 256


TESTS = {
    'lead-lasher': dict(
        builds=['lasher'], frames=[F, F + round(LATER * 24)],
        note='Lead Lasher at 58 s (top: S6 hits fresh) and 110 s (bottom: S6 scars cooled, S9b fires), v1 look. '
             'Left frame at those times, right frame 5 s on.',
        setup=lead_lasher),
    'before-after': dict(
        builds=['lasher'], frames=[F, F + 1],
        note='Lead Lasher, v2 look (left column) against v1 (right), at 58 s (top row) and 110 s (bottom row). '
             'Left frame from shot distance, right frame close.',
        setup=before_after),
}
