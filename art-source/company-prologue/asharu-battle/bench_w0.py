"""Bench tests for W0 (asharu-effects-plan.md): per-ship effects are independent.

The pattern for every bench_<module>.py: TESTS maps a name to its builds, frames, a one-line
note for the owner, and a setup(bench) that runs inside Blender (see effects_bench.py).
"""


def two_lashers(bench):
    """Two copies of the one Lasher build side by side: A at full throttle opening fire on
    frame 2 every 3 frames, B at quarter throttle opening a frame later every 4. Every turret
    on each ship is keyed through the kit's own swfx_guns.fire, bound to that ship's copy."""
    a = bench.ship('lasher-a', 'lasher', at=(0, 55, 0), heading=0, throttle=1.0)
    b = bench.ship('lasher-b', 'lasher', at=(0, -55, 0), heading=0, throttle=.25)
    with a.kit() as (ctx, kit):
        for t in (1, 2, 3):
            kit.guns.fire(ctx, t, 2, shots=3, interval_frames=3)
    with b.kit() as (ctx, kit):
        for t in (1, 2, 3):
            kit.guns.fire(ctx, t, 3, shots=2, interval_frames=4)
    bench.camera((250, -260, 95), (0, 0, 0), 42)


TESTS = {
    'two-lashers': dict(
        builds=['lasher'], frames=[2, 3, 4, 5],
        note='Two copies of one build: A (top) full throttle, firing frames 2 and 5; B quarter throttle, '
             'firing frame 3. Nothing should flash in step.',
        setup=two_lashers),
}
