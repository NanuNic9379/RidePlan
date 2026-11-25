"""Areas of road data beyond Italy (#14). Standard library only.

  python routing_areas.py matrix  AREAS.json "alps,fr"         -> JSON list of the areas to build
  python routing_areas.py config  AREAS.json ID OUT.json       -> config for routing_data.py (source, bbox)
  python routing_areas.py release AREAS.json ID                -> Release tag of the area
  python routing_areas.py index   AREAS.json POLY_DIR IDS OUT.json
                                                               -> the index the app reads (areas.json)

Every area is an independent build with its own Release and its own cells.json (cells of
different builds never mix). The build itself is the same as Italy's: routing_data.py splits
it into packs. Italy (id "it") is still built by routing-data.yml into the "routing-data"
Release, which older app versions read directly: it is never built from here.

The index lists, for every area whose Release already has a cells.json (IDS, comma-separated),
its Release tag, bounding box and outline (the .poly of the extract as encoded polylines,
same encoding as CountryBorders.kt). Inside the outline the data is complete: the app prefers
the area whose outline holds every stop of the ride, because the 1-degree cells at the edge of
an area also exist where only part of their roads was in the extract.
"""
import json
import re
import sys

FORMAT = 1  # change only if the app can no longer read the index

# Encoded polylines with 3 decimals (about 110 m), like the country outlines.
PRECISION = 3

# Same limits as the app: no id or tag can build a path or a URL outside its place.
AREA_ID = re.compile(r"^[a-z0-9][a-z0-9-]{0,31}$")
RELEASE = re.compile(r"^routing-[a-z0-9-]{1,40}$")

# The area built by routing-data.yml (today's Italy). Never rebuilt here.
LEGACY = "it"


def load(path):
    with open(path, encoding="utf-8") as f:
        return json.load(f)


def areas(path):
    """The areas of the file, checked: unique ids, valid tags, a bbox of four numbers."""
    out = load(path)["areas"]
    seen = set()
    for a in out:
        if not AREA_ID.match(a["id"]) or a["id"] in seen:
            sys.exit(f"invalid or repeated area id: {a['id']!r}")
        seen.add(a["id"])
        if not RELEASE.match(a["release"]):
            sys.exit(f"invalid Release tag for {a['id']}: {a['release']!r}")
        box = a["bbox"]
        if len(box) != 4 or not (box[0] < box[2] and box[1] < box[3]):
            sys.exit(f"invalid bbox for {a['id']}: {box}")
    return out


def find(path, area_id):
    for a in areas(path):
        if a["id"] == area_id:
            return a
    sys.exit(f"unknown area: {area_id}")


def buildable(path, wanted):
    """The ids of `wanted` (comma-separated), checked: known, with a source, not Italy."""
    ids = [w.strip() for w in wanted.split(",") if w.strip()]
    if not ids:
        sys.exit("no area given")
    out = []
    for area_id in ids:
        a = find(path, area_id)
        if area_id == LEGACY:
            sys.exit("Italy is built by routing-data.yml, not here")
        if not a.get("source"):
            sys.exit(f"area {area_id} has no source")
        if area_id not in out:
            out.append(area_id)
    return out


def matrix(path, wanted):
    print(json.dumps(buildable(path, wanted)))


def config(path, area_id, out_path):
    """The config routing_data.py expects (same keys as routing-data.json; poly: outline of the extract, #60)."""
    a = find(path, area_id)
    with open(out_path, "w", encoding="utf-8") as f:
        json.dump({"source": a["source"], "bbox": a["bbox"], "poly": a.get("poly", "")}, f, indent=2)


def release(path, area_id):
    print(find(path, area_id)["release"])


def parse_poly(text):
    """Osmosis .poly: [(is_hole, [(lon, lat), ...]), ...]. Sections starting with '!' are holes."""
    lines = [line.strip() for line in text.splitlines()]
    rings, i = [], 1  # line 0 is the polygon name
    while i < len(lines):
        head = lines[i]
        i += 1
        if not head or head == "END":
            continue
        ring = []
        while i < len(lines) and lines[i] != "END":
            parts = lines[i].split()
            if len(parts) >= 2:
                ring.append((float(parts[0]), float(parts[1])))
            i += 1
        i += 1  # the section's END
        if len(ring) > 1 and ring[0] == ring[-1]:
            ring = ring[:-1]
        if len(ring) >= 3:
            rings.append((head.startswith("!"), ring))
    if not any(not hole for hole, _ in rings):
        raise ValueError("no outer ring in the polygon")
    return rings


def encode(ring):
    """Google encoded polyline of (lon, lat) points, latitude first, PRECISION decimals."""
    factor = 10 ** PRECISION
    out, last = [], (0, 0)
    for lon, lat in ring:
        cur = (round(lat * factor), round(lon * factor))
        for v in (cur[0] - last[0], cur[1] - last[1]):
            v = ~(v << 1) if v < 0 else v << 1
            while v >= 0x20:
                out.append(chr((0x20 | (v & 0x1F)) + 63))
                v >>= 5
            out.append(chr(v + 63))
        last = cur
    return "".join(out)


def outline_bbox(rings):
    pts = [p for hole, ring in rings if not hole for p in ring]
    return [min(p[0] for p in pts), min(p[1] for p in pts), max(p[0] for p in pts), max(p[1] for p in pts)]


def index_data(areas_list, polys, published):
    """The index: only areas with a published build, in the order of the areas file."""
    entries = []
    for a in areas_list:
        if a["id"] not in published:
            continue
        entry = {"id": a["id"], "name": a["name"], "release": a["release"], "bbox": a["bbox"]}
        rings = polys.get(a["id"])
        if rings:
            # Holes are plain rings too: the app uses the even-odd rule.
            entry["bbox"] = [round(v, 3) for v in outline_bbox(rings)]
            entry["outline"] = {"precision": PRECISION, "rings": [encode(r) for _, r in rings]}
        entries.append(entry)
    return {"format": FORMAT, "areas": entries}


def index(path, poly_dir, published_ids, out_path):
    """POLY_DIR/<id>.poly for every area that has one (a missing outline only means bbox only)."""
    published = {p.strip() for p in published_ids.split(",") if p.strip()}
    polys = {}
    for a in areas(path):
        try:
            with open(f"{poly_dir}/{a['id']}.poly", encoding="utf-8") as f:
                polys[a["id"]] = parse_poly(f.read())
        except (OSError, ValueError) as e:
            print(f"note: no outline for {a['id']}: {e}")
    data = index_data(areas(path), polys, published)
    if not data["areas"]:
        sys.exit("no published area: nothing to index")
    with open(out_path, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=1)
    print(", ".join(f"{e['id']} ({len(e.get('outline', {}).get('rings', []))} rings)" for e in data["areas"]))


if __name__ == "__main__":
    cmd, args = sys.argv[1], sys.argv[2:]
    {"matrix": matrix, "config": config, "release": release, "index": index}[cmd](*args)
