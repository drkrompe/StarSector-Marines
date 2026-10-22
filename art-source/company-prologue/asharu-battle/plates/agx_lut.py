"""Inside Blender: tabulate the stills' grade (AgX, 'AgX - Base Contrast', sRGB display) on a
lattice of scene-linear colours, so finish_plates.py can invert it (see README.md).

    blender -b --factory-startup --python agx_lut.py -- OUT.npz

Nothing is rendered: a float image holding the lattice is written with save_render, which applies
the scene's view transform, and read back. The lattice is N^3 colours spaced evenly in log2
from 2^LO to 2^HI, plus black.
"""
import sys

import bpy
import numpy as np

N, LO, HI = 33, -12.0, 4.0


def main():
    out = sys.argv[sys.argv.index('--') + 1]
    scene = bpy.context.scene
    scene.view_settings.view_transform = 'AgX'
    scene.view_settings.look = 'AgX - Base Contrast'
    scene.view_settings.exposure = 0.0
    scene.view_settings.gamma = 1.0
    scene.display_settings.display_device = 'sRGB'
    scene.render.image_settings.file_format = 'PNG'
    scene.render.image_settings.color_depth = '16'
    scene.render.image_settings.color_mode = 'RGB'
    axis = np.concatenate([[0.0], 2.0 ** np.linspace(LO, HI, N - 1)])
    r, g, b = np.meshgrid(axis, axis, axis, indexing='ij')
    lin = np.stack([r, g, b], -1).reshape(-1, 3)            # N^3 x 3, R slowest
    w = N * N
    h = N
    img = bpy.data.images.new('lattice', w, h, float_buffer=True, alpha=True)
    img.colorspace_settings.name = 'Linear Rec.709'
    rgba = np.ones((h, w, 4), dtype=np.float32)
    rgba[..., :3] = lin.reshape(h, w, 3)
    img.pixels.foreach_set(rgba.ravel())
    png = out + '.png'
    img.save_render(png, scene=scene)
    back = bpy.data.images.load(png)
    back.colorspace_settings.name = 'Non-Color'       # the display code values, not linearised
    px = np.empty(w * h * 4, dtype=np.float32)
    back.pixels.foreach_get(px)
    disp = px.reshape(h, w, 4)[..., :3].reshape(-1, 3)
    np.savez(out, axis=axis, display=disp.reshape(N, N, N, 3), n=N)
    print('AGX LUT', out, disp.min(0), disp.max(0))


main()
