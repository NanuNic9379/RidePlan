"""Helpers for the routing data pipeline (#3, #45). Standard library only.

  python routing_data.py source CONFIG.json                     -> URL of the OpenStreetMap data
  python routing_data.py cells  CONFIG.json TILES_DIR OUTDIR BUILD OSM_DATE VALHALLA [FUEL.geojsonseq]
                                                               -> one .zip pack per grid cell, plus cells.json
                                                                  (and one fuel station list per cell, #7)
  python routing_data.py stale  CELLS.json < NAMES             -> old packs to remove from the Release

The data is built once for the whole of Italy, so downloaded cells always fit together
(same build = same numbering of roads and junctions at the edges).
The Valhalla tile directory is then split into packs:
  - one pack per 1-degree cell (the level-1 tile plus its 16 level-2 tiles);
  - one pack per 4-degree block with the main roads (level 0), shared by the cells inside it.
The app downloads only the packs around the ride and puts them all into the same directory.

Fuel stations (#7): Valhalla keeps no points of interest, so the stations (amenity=fuel, from
`osmium export ... -f geojsonseq`) go into a small text file per 1-degree cell, listed in the
cell's manifest entry under "fuel". Every cell gets one, even an empty one: on the phone a missing
file means "unknown", never "no stations". App versions without #7 ignore the extra field.
"""
import hashlib
import json
import os
import re
import sys
import zipfile

FORMAT = 1  # change only if the app can no longer read the manifest

# Tile side in degrees for each Valhalla level (world grid with origin -180, -90).
TILE_SIZE = {0: 4.0, 1: 1.0, 2: 0.25}

# A tile inside the directory: level/digits in groups of three.gph, e.g. 2/000/752/345.gph
TILE_PATH = re.compile(r"^([012])/((?:\d{3}/)*\d{3})\.gph$")

# A published pack: l<level>-<tile id>-<build>.zip
PACK_FILE = re.compile(r"^l[01]-\d+-[\w.]+\.zip$")

# A published fuel station list (#7): fuel-l1-<tile id>-<build>.tsv
FUEL_FILE = re.compile(r"^fuel-l1-\d+-[\w.]+\.tsv$")

# First line of a fuel file: format and columns. Change the number only if the app can no longer read it.
FUEL_HEADER = "#rideplan-fuel 1\tlat\tlon\tbrand\tname"

# Stations closer than this (degrees, about 30 m) are the same one mapped twice (point and area).
FUEL_SAME = 0.0003

# GitHub Release limits (#14): each file under 2 GiB, at most 1000 files per Release. During an
# update the new packs sit next to the old ones before these are removed, so twice the packs
# (plus cells.json) must fit. Checked before publishing, so a too-big area fails the build
# instead of leaving a half-uploaded Release.
MAX_ASSET_BYTES = 2 * 1024 ** 3
MAX_RELEASE_ASSETS = 1000


def release_problems(packs):
    """What would not fit in one Release; empty list if everything is fine."""
    problems = [f"{p['file']} is {p['bytes'] / 1e9:.2f} GB, over the 2 GiB per-file limit"
                for p in packs if p["bytes"] >= MAX_ASSET_BYTES]
    # Each cell can also carry a fuel station list (#7): one more file in the Release.
    files = len(packs) + sum(1 for p in packs if "fuel" in p)
    # Plus cells.json and road-notes.json (#13), replaced in place.
    if 2 * files + 2 > MAX_RELEASE_ASSETS:
        problems.append(f"{len(packs)} packs and their side files: an update needs {2 * files + 2} files, "
                        f"over the {MAX_RELEASE_ASSETS}-file limit of a Release; split the area")
    return problems


def load(path):
    with open(path, encoding="utf-8") as f:
        return json.load(f)


def source(config_path):
    print(load(config_path)["source"])


def tile_bbox(level, tile_id):
    """lon min, lat min, lon max, lat max of the tile."""
    size = TILE_SIZE[level]
    cols = int(round(360 / size))
    row, col = divmod(tile_id, cols)
    lon, lat = -180 + col * size, -90 + row * size
    return [lon, lat, lon + size, lat + size]


def tile_id_at(level, lon, lat):
    size = TILE_SIZE[level]
    cols = int(round(360 / size))
    return int((lat + 90) // size) * cols + int((lon + 180) // size)


def intersects(a, b):
    return a[0] < b[2] and b[0] < a[2] and a[1] < b[3] and b[1] < a[3]


def sha256(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def centroid(geometry):
    """A representative point of a GeoJSON geometry: the point itself, or the mean of the outer ring's vertices."""
    kind, coords = geometry.get("type"), geometry.get("coordinates")
    if kind == "Point":
        return coords
    if kind == "LineString":
        ring = coords
    elif kind == "Polygon":
        ring = coords[0] if coords else None
    elif kind == "MultiPolygon":
        ring = coords[0][0] if coords and coords[0] else None
    else:
        return None
    if not ring:
        return None
    if len(ring) > 1 and ring[0] == ring[-1]:
        ring = ring[:-1]
    return [sum(p[0] for p in ring) / len(ring), sum(p[1] for p in ring) / len(ring)]


def clean(text):
    """A tag value fit for one TSV field."""
    return " ".join(str(text or "").replace("\t", " ").split())[:80]


def sells_petrol(tags):
    """False only when the tags say there is no petrol (e.g. gas only): unknown counts as yes."""
    petrol = ("fuel:octane_95", "fuel:octane_98", "fuel:octane_100", "fuel:e5", "fuel:e10")
    if any(tags.get(k) == "yes" for k in petrol):
        return True
    return tags.get("fuel:octane_95") != "no"


def fuel_stations(path):
    """Stations from a GeoJSON sequence: [(lon, lat, brand, name)], without private, disused or duplicate ones."""
    stations, seen = [], set()
    with open(path, encoding="utf-8") as f:
        for line in f:
            line = line.strip().lstrip("\x1e")
            if not line:
                continue
            feature = json.loads(line)
            tags = feature.get("properties") or {}
            if tags.get("amenity") != "fuel" or tags.get("access") in ("private", "no"):
                continue
            if tags.get("disused") == "yes" or not sells_petrol(tags):
                continue
            point = centroid(feature.get("geometry") or {})
            if not point:
                continue
            lon, lat = round(point[0], 5), round(point[1], 5)
            key = (round(lon / FUEL_SAME), round(lat / FUEL_SAME))
            if key in seen:
                continue
            seen.add(key)
            stations.append((lon, lat, clean(tags.get("brand") or tags.get("operator")), clean(tags.get("name"))))
    return stations


def write_fuel(outdir, build, cell_ids, stations):
    """One TSV per 1-degree cell, empty ones too: {cell id: manifest entry}."""
    by_cell = {cell: [] for cell in cell_ids}
    for lon, lat, brand, name in stations:
        cell = tile_id_at(1, lon, lat)
        if cell in by_cell:
            by_cell[cell].append((lat, lon, brand, name))
    entries = {}
    for cell, rows in by_cell.items():
        file = f"fuel-l1-{cell}-{build}.tsv"
        path = os.path.join(outdir, file)
        with open(path, "w", encoding="utf-8", newline="\n") as f:
            f.write(FUEL_HEADER + "\n")
            for lat, lon, brand, name in sorted(rows):
                f.write(f"{lat}\t{lon}\t{brand}\t{name}\n")
        entries[cell] = {"file": file, "bytes": os.path.getsize(path), "sha256": sha256(path)}
    return entries


def cells(config_path, tiles_dir, outdir, build, osm_date, valhalla, fuel_path=None):
    area = load(config_path)["bbox"]
    groups = {}  # (pack level, id) -> [relative paths]
    for root, _, files in os.walk(tiles_dir):
        for name in files:
            rel = os.path.relpath(os.path.join(root, name), tiles_dir).replace(os.sep, "/")
            m = TILE_PATH.match(rel)
            if not m:
                continue  # indexes and other build files: the app does not need them
            level, tile_id = int(m.group(1)), int(m.group(2).replace("/", ""))
            if level == 0:
                key = (0, tile_id)
            else:
                # The 1-degree cell containing the tile (from its centre, to avoid the edge).
                b = tile_bbox(level, tile_id)
                key = (1, tile_id_at(1, (b[0] + b[2]) / 2, (b[1] + b[3]) / 2))
            groups.setdefault(key, []).append(rel)
    if not any(level == 1 for level, _ in groups):
        sys.exit("no cell built")

    # Numbering check: a mistake here would send the cells to the other side of the world.
    # A few packs outside the area are normal: ferry lines end in Tunisia, Albania, Greece...
    outside = [k for k in groups if not intersects(tile_bbox(*k), area)]
    for level, tile_id in outside:
        print(f"note: pack l{level}-{tile_id} outside the covered area: {tile_bbox(level, tile_id)}")
    if len(outside) * 2 > len(groups):
        sys.exit(f"{len(outside)} of {len(groups)} packs outside the covered area: wrong tile numbering?")

    os.makedirs(outdir, exist_ok=True)
    fuel = {}
    if fuel_path:
        stations = fuel_stations(fuel_path)
        fuel = write_fuel(outdir, build, [tile_id for level, tile_id in groups if level == 1], stations)
        print(f"{len(stations)} fuel stations in {len(fuel)} cells")
    packs = []
    for (level, tile_id), rels in sorted(groups.items()):
        bbox = tile_bbox(level, tile_id)
        file = f"l{level}-{tile_id}-{build}.zip"
        path = os.path.join(outdir, file)
        tile_bytes = 0
        with zipfile.ZipFile(path, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as z:
            for rel in sorted(rels):
                full = os.path.join(tiles_dir, rel)
                tile_bytes += os.path.getsize(full)
                z.write(full, rel)
        pack = {
            "id": f"l{level}-{tile_id}",
            "level": level,
            "bbox": bbox,
            "file": file,
            "bytes": os.path.getsize(path),
            "tileBytes": tile_bytes,  # space used on the phone after extraction
            "sha256": sha256(path),
        }
        if level == 1 and tile_id in fuel:
            pack["fuel"] = fuel[tile_id]
        packs.append(pack)
    problems = release_problems(packs)
    if problems:
        sys.exit("\n".join(problems))
    data = {"format": FORMAT, "build": build, "osmDate": osm_date, "valhalla": valhalla, "packs": packs}
    with open(os.path.join(outdir, "cells.json"), "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2)
    cell_sizes = sorted(p["bytes"] for p in packs if p["level"] == 1)
    print(f"{len(packs)} packs, {sum(p['bytes'] for p in packs) / 1e6:.0f} MB in total; "
          f"average cell {sum(cell_sizes) / len(cell_sizes) / 1e6:.1f} MB, largest {cell_sizes[-1] / 1e6:.1f} MB")
    for p in packs:
        if p["level"] == 0:
            print(f"main roads {p['id']}: {p['bytes'] / 1e6:.1f} MB")


def stale(manifest_path):
    """From the Release file names (one per line), those of packs no longer in the manifest."""

    packs = load(manifest_path)["packs"]
    keep = {p["file"] for p in packs} | {p["fuel"]["file"] for p in packs if "fuel" in p}
    for line in sys.stdin:
        name = line.strip()
        if (PACK_FILE.match(name) or FUEL_FILE.match(name)) and name not in keep:
            print(name)


if __name__ == "__main__":
    cmd, args = sys.argv[1], sys.argv[2:]
    {"source": source, "cells": cells, "stale": stale}[cmd](*args)
