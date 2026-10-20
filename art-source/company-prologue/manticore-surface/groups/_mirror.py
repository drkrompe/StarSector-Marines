"""Port-authored parts and their starboard mirrors (the hull is symmetric about Y = 0)."""
BIG = 200.0


def box(x=(-BIG, BIG), y=(-BIG, BIG), z=(-BIG, BIG)):
    return ((x[0], y[0], z[0]), (x[1], y[1], z[1]))


def flip(v):
    return (v[0], -v[1], v[2])


def flip_box(b):
    (x0, y0, z0), (x1, y1, z1) = b
    return ((x0, -y1, z0), (x1, -y0, z1))


SEAMS = None  # parts.py seam_walls (.05 claims the plating's steep seam walls); off for the shipped build: its tiles were fitted to guides without them


def pair(parts, name, members, dir, up, cone, what, **kw):
    """members: [(port object name with '{s}' for the side, box)]; adds name-port and name-stbd.
    Every pair claims its plates' seam walls (parts.py seam_walls)."""
    kw.setdefault('seam_walls', SEAMS)
    parts[name+'-port'] = dict(members=[(o.format(s='port'), {'box': b}) for o, b in members],
                               dir=dir, up=up, cone=cone, what='port '+what, **kw)
    parts[name+'-stbd'] = dict(members=[(o.format(s='starboard'), {'box': flip_box(b)}) for o, b in members],
                               dir=flip(dir), up=flip(up), cone=cone, what='starboard '+what+' (mirror of port)',
                               skin_from=name+'-port', mirror_u=True, **kw)
