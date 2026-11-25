"""Road notes for the app (#13): seasonal closures and fixed speed cameras. Standard library only.

  python road_notes.py IN.geojsonseq OUT.json OSM_DATE [BORDERS]

IN is `osmium export -f geojsonseq --add-unique-id=type_id` of the ways and nodes kept by the
"Road notes" step of routing-data.yml. Out of those only two things are written:
  - closures: roads (highway=*) with a seasonal or time-conditional ban for motor vehicles,
    e.g. a pass closed in winter (`motor_vehicle:conditional=no @ (Nov-May)`, `seasonal=yes`,
    `opening_hours=May-Oct`). The original tags are kept: the app shows and interprets them.
  - cameras: highway=speed_camera nodes (fixed cameras), with maxspeed and direction if tagged.
    Only cameras in Italy are written (#62, owner decision): no file on the phone holds camera
    positions for countries where carrying them is illegal (CH, FR, DE...). Border rule, on the
    same coarse outlines the app uses (CountryBorders.kt, about 500 m): a camera is kept when it
    is inside Italy's outline, or within COAST_KM of it and inside no other country's outline
    (shore roads that the coarse outline cuts off). Closures are kept for every country.
    See docs/pass-closures-and-speed-cameras.md.

The optional BORDERS argument is the CountryBorders.kt to read (default: the app's own file).
"""
import json
import math
import re
import sys
from pathlib import Path

FORMAT = 1  # change only if the app can no longer read the file

# Tags that can close a road for part of the year. The values are kept as they are.
CLOSURE_KEYS = (
    "motor_vehicle:conditional",
    "motorcycle:conditional",
    "vehicle:conditional",
    "access:conditional",
    "seasonal",
    "opening_hours",
)

# Roads a motorcycle can use: closures on footways or cycleways do not matter to the app.
MOTOR_ROADS = {
    "motorway", "motorway_link", "trunk", "trunk_link", "primary", "primary_link",
    "secondary", "secondary_link", "tertiary", "tertiary_link", "unclassified",
    "residential", "living_street", "service", "track", "road",
}

# Name tags copied to the file, to say which road is closed.
NAME_KEYS = ("name", "ref", "name:it", "name:en", "name:de", "name:fr", "name:es")

# Coordinates with 6 decimals: about 10 cm, more than enough.
DIGITS = 6

# Speed cameras (#62): the only country whose cameras are published.
CAMERA_COUNTRY = "IT"

# Outside Italy's coarse outline but this close to it, and in no other country: still Italy.
COAST_KM = 1.0

BORDERS = Path(__file__).resolve().parent.parent / \
    "shared/src/commonMain/kotlin/io/github/nanunic9379/rideplan/route/CountryBorders.kt"


def decode(text, precision):
    """Google encoded polyline (latitude first) to [(lon, lat)]."""
    factor = 10 ** precision
    points, values, i = [], [], 0
    while i < len(text):
        result = shift = 0
        while True:
            b = ord(text[i]) - 63
            i += 1
            result |= (b & 0x1F) << shift
            shift += 5
            if b < 0x20:
                break
        values.append(~(result >> 1) if result & 1 else result >> 1)
    lat = lon = 0
    for dlat, dlon in zip(values[::2], values[1::2]):
        lat, lon = lat + dlat, lon + dlon
        points.append((lon / factor, lat / factor))
    return points


def load_borders(path=BORDERS):
    """{ISO code: [ring of (lon, lat)]} from the generated CountryBorders.kt."""
    text = Path(path).read_text(encoding="utf-8")
    precision = int(re.search(r"const val PRECISION = (\d+)", text).group(1))
    borders = {}
    for code, body in re.findall(r'"([A-Z]{2})" to listOf\((.*?)\n\s*\),', text, re.S):
        borders[code] = [decode(r, precision) for r in re.findall(r'"""(.*?)"""', body)]
    if CAMERA_COUNTRY not in borders:
        sys.exit(f"no {CAMERA_COUNTRY} outline in {path}")
    return borders


class Ring(list):
    """A ring of (lon, lat) points with its box (lon min, lat min, lon max, lat max)."""

    def __init__(self, points):
        super().__init__(points)
        self.box = (min(p[0] for p in self), min(p[1] for p in self), max(p[0] for p in self), max(p[1] for p in self))


def inside(rings, lon, lat):
    """Even-odd rule over every ring (San Marino and the Vatican are holes in Italy), like the app."""
    hit = False
    for ring in rings:
        # Outside the ring's latitudes no edge is crossed; past its east end none either; west of
        # it the crossings come in pairs. So a ring whose box misses the point changes nothing.
        if not ring.box[1] <= lat <= ring.box[3] or not ring.box[0] <= lon <= ring.box[2]:
            continue
        j = len(ring) - 1
        for i in range(len(ring)):
            (xi, yi), (xj, yj) = ring[i], ring[j]
            if (yi > lat) != (yj > lat) and lon < xi + (lat - yi) / (yj - yi) * (xj - xi):
                hit = not hit
            j = i
    return hit


def km_to(rings, lon, lat):
    """Shortest distance in km from the point to the rings' edges (flat Earth, fine for a few km)."""
    kx, ky = 111.32 * math.cos(math.radians(lat)), 110.57
    best = math.inf
    for ring in rings:
        for i in range(len(ring)):
            (ax, ay), (bx, by) = ring[i - 1], ring[i]
            ax, ay, bx, by = (ax - lon) * kx, (ay - lat) * ky, (bx - lon) * kx, (by - lat) * ky
            dx, dy = bx - ax, by - ay
            t = 0.0 if dx == dy == 0 else max(0.0, min(1.0, -(ax * dx + ay * dy) / (dx * dx + dy * dy)))
            best = min(best, math.hypot(ax + t * dx, ay + t * dy))
    return best


def camera_country_check(borders):
    """A function (lon, lat) -> True for a camera that may be published (see the module doc)."""
    home = [Ring(r) for r in borders[CAMERA_COUNTRY]]
    others = [[Ring(r) for r in rings] for code, rings in borders.items() if code != CAMERA_COUNTRY]

    def keep(lon, lat):
        if inside(home, lon, lat):
            return True
        if any(inside(rings, lon, lat) for rings in others):
            return False
        return km_to(home, lon, lat) <= COAST_KM
    return keep


def closes(tags):
    """True if the tags of a road describe a closure for part of the year."""
    if tags.get("highway") not in MOTOR_ROADS:
        return False
    for key in ("motor_vehicle:conditional", "motorcycle:conditional", "vehicle:conditional", "access:conditional"):
        value = tags.get(key, "")
        # "no @ (Nov-May)", "no @ (Dec-Apr); destination @ (...)": a ban at some time.
        if any(part.strip().startswith(("no", "private")) for part in value.split(";")):
            return True
    if tags.get("seasonal", "no") not in ("no", ""):
        return True
    # opening_hours on a road (not on a shop): the road itself opens and closes.
    return "opening_hours" in tags


def camera(tags):
    return tags.get("highway") == "speed_camera"


def rounded(coords):
    return [[round(c[0], DIGITS), round(c[1], DIGITS)] for c in coords]


def notes(lines, keep_camera=lambda lon, lat: True):
    """Closures and cameras of the GeoJSON lines; cameras only where keep_camera(lon, lat)."""
    closures, cameras = [], []
    for line in lines:
        line = line.strip().lstrip("\x1e")  # geojsonseq may start each record with RS
        if not line:
            continue
        feature = json.loads(line)
        tags = feature.get("properties") or {}
        geometry = feature.get("geometry") or {}
        osm_id = feature.get("id")
        if geometry.get("type") == "Point" and camera(tags):
            lon, lat = geometry["coordinates"]
            if not keep_camera(lon, lat):
                continue
            item = {"id": osm_id, "lat": round(lat, DIGITS), "lon": round(lon, DIGITS)}
            for key in ("maxspeed", "direction"):
                if key in tags:
                    item[key] = tags[key]
            cameras.append(item)
        elif geometry.get("type") == "LineString" and closes(tags):
            item = {"id": osm_id, "highway": tags["highway"], "line": rounded(geometry["coordinates"])}
            item["tags"] = {k: tags[k] for k in CLOSURE_KEYS if k in tags}
            for key in NAME_KEYS:
                if key in tags:
                    item.setdefault("names", {})[key] = tags[key]
            closures.append(item)
    closures.sort(key=lambda c: str(c["id"]))
    cameras.sort(key=lambda c: str(c["id"]))
    return closures, cameras


def main(argv):
    if len(argv) not in (4, 5):
        sys.exit(__doc__)
    src, out, osm_date = argv[1:4]
    keep = camera_country_check(load_borders(*argv[4:]))
    with open(src, encoding="utf-8") as f:
        closures, cameras = notes(f, keep)
    with open(out, "w", encoding="utf-8") as f:
        json.dump({"format": FORMAT, "osmDate": osm_date, "closures": closures, "cameras": cameras},
                  f, ensure_ascii=False, separators=(",", ":"))
    print(f"{len(closures)} seasonal closures, {len(cameras)} speed cameras")


if __name__ == "__main__":
    main(sys.argv)
