"""Bench test for progressive battle damage (asharu_damage.py, asharu-damage.md).

One build, the lead Lasher, at two points in the battle: each copy gets asharu_damage.apply as
battle_blockout will call it, with its own frame_of, so one bench frame shows both moments.
"""
F = 48           # the bench frame that is each copy's sequence time
LATER = 5.0      # the second frame, this many seconds on: embers cooling, fires burning on

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
        asharu_damage.apply('lasher-lead', s, t, t, frame_of, s.matrix,
                            log=lambda m: print(m, flush=True))
    bench.camera((-20, 230, 95), (-4, 0, 0), 40)
    bench.scene.cycles.volume_step_rate = 2.0
    bench.scene.cycles.volume_max_steps = 256


TESTS = {
    'lead-lasher': dict(
        builds=['lasher'], frames=[F, F + round(LATER * 24)],
        note='Lead Lasher at 58 s (top: S6 hits fresh) and 110 s (bottom: S6 scars cooled, S9b fires). '
             'Left frame at those times, right frame 5 s on.',
        setup=lead_lasher),
}
