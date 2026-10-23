#!/usr/bin/env python3
"""Lossless PNG recompression for the release package.

usage: optimize_pngs.py <src-dir> <dest-dir>

Writes every PNG under src-dir to the same relative path under dest-dir, recompressed with
Pillow's optimize=True when that is smaller and decodes to identical RGBA, otherwise copied
verbatim. A destination newer than its source is kept, so a warm run touches nothing. The
tracked sources are never rewritten (see CLAUDE.md, packageRelease).
"""
import io
import os
import shutil
import sys
from concurrent.futures import ProcessPoolExecutor

import numpy as np
from PIL import Image


def convert(job):
    src, dst = job
    if os.path.exists(dst) and os.path.getmtime(dst) >= os.path.getmtime(src):
        return 0
    os.makedirs(os.path.dirname(dst), exist_ok=True)
    with Image.open(src) as im:
        im.load()
        kw = {"optimize": True}
        if im.mode == "P" and "transparency" in im.info:
            kw["transparency"] = im.info["transparency"]
        buf = io.BytesIO()
        im.save(buf, "PNG", **kw)
        data = buf.getvalue()
        same = False
        if len(data) < os.path.getsize(src):
            a = np.asarray(im.convert("RGBA"))
            with Image.open(io.BytesIO(data)) as out:
                b = np.asarray(out.convert("RGBA"))
            same = a.shape == b.shape and bool((a == b).all())
    if same:
        with open(dst, "wb") as f:
            f.write(data)
    else:
        shutil.copyfile(src, dst)
    return 1


def main():
    src_root, dst_root = sys.argv[1], sys.argv[2]
    jobs = []
    for d, _, files in os.walk(src_root):
        for f in files:
            if f.lower().endswith(".png"):
                p = os.path.join(d, f)
                jobs.append((p, os.path.join(dst_root, os.path.relpath(p, src_root))))
    with ProcessPoolExecutor() as ex:
        done = sum(ex.map(convert, jobs, chunksize=4))
    print("optimize_pngs: %d of %d PNGs recompressed" % (done, len(jobs)))


if __name__ == "__main__":
    main()
