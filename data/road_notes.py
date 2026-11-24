"""Road notes for the app (#13): seasonal closures and fixed speed cameras. Standard library only.

  python road_notes.py IN.geojsonseq OUT.json OSM_DATE

IN is `osmium export -f geojsonseq --add-unique-id=type_id` of the ways and nodes kept by the
"Road notes" step of routing-data.yml. Out of those only two things are written:
  - closures: roads (highway=*) with a seasonal or time-conditional ban for motor vehicles,
    e.g. a pass closed in winter (`motor_vehicle:conditional=no @ (Nov-May)`, `seasonal=yes`,
    `opening_hours=May-Oct`). The original tags are kept: the app shows and interprets them.
  - cameras: highway=speed_camera nodes (fixed cameras), with maxspeed and direction if tagged.
    Whether the app shows them depends on the country where the rider is: see
    docs/pass-closures-and-speed-cameras.md.
"""
import json
import sys

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


def notes(lines):
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
    if len(argv) != 4:
        sys.exit(__doc__)
    src, out, osm_date = argv[1:]
    with open(src, encoding="utf-8") as f:
        closures, cameras = notes(f)
    with open(out, "w", encoding="utf-8") as f:
        json.dump({"format": FORMAT, "osmDate": osm_date, "closures": closures, "cameras": cameras},
                  f, ensure_ascii=False, separators=(",", ":"))
    print(f"{len(closures)} seasonal closures, {len(cameras)} speed cameras")


if __name__ == "__main__":
    main(sys.argv)
