"""Self-tests of the data scripts (#60, #61, #62). Standard library only.

  python3 -m unittest discover -s data
"""
import hashlib
import json
import os
import sys
import tempfile
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import road_notes  # noqa: E402
import routing_data  # noqa: E402

# Packs reported outside the Alps by the failed run 45444361349 of routing-areas.yml (#60):
# ferry lines to the Balearics, Spain, Algeria, Tunisia, Sardinia, Corsica and Sicily.
FAILED_RUN_OUTSIDE = [
    "l1-46264", "l1-46260", "l1-46261", "l1-46268", "l1-46267", "l1-46265", "l1-46266", "l1-46263",
    "l1-47703", "l1-45536", "l1-45539", "l1-45537", "l1-45179", "l1-45901", "l1-45905", "l1-45909",
    "l1-45900", "l1-45907", "l1-45899", "l1-45906", "l1-45903", "l1-46623", "l1-46625", "l1-46624",
    "l1-46620", "l1-46621", "l1-46627", "l1-46626", "l1-46622", "l1-46628", "l1-46985", "l1-46988",
    "l1-46986", "l1-46983", "l1-46989", "l1-46982", "l1-46984", "l1-45535", "l1-45534", "l1-46262",
    "l1-46269", "l1-45540", "l1-45545", "l1-45538", "l1-45898", "l1-45910", "l1-46987", "l1-46259",
    "l1-47344", "l1-47342", "l1-47346", "l1-47349", "l1-47348", "l1-47343", "l1-47345", "l1-45546",
    "l1-45543", "l1-45550", "l1-45547", "l1-47347", "l1-46981", "l1-45174", "l0-2835", "l0-2834",
    "l0-2837", "l0-2833", "l0-2927", "l0-2925", "l0-2836",
]

# A coarse hand-drawn outline of the Geofabrik Alps extract (lon, lat).
ALPS = [[(4.9, 43.1), (6.5, 42.9), (7.6, 43.7), (9.5, 44.1), (12.5, 44.2), (13.6, 45.5),
         (16.7, 45.9), (16.7, 48.4), (12.0, 48.4), (7.5, 48.0), (5.5, 47.5), (4.9, 46.0)]]


def key(name):
    level, tile_id = name[1:].split("-")
    return int(level), int(tile_id)


def alps_cells():
    """The 1-degree cells and 4-degree blocks inside the Alps outline, as Valhalla would build them."""
    cells = {(1, routing_data.tile_id_at(1, lon + 0.5, lat + 0.5)) for lon in range(6, 16) for lat in range(45, 48)}
    blocks = {(0, routing_data.tile_id_at(0, lon, lat)) for lon in (6, 10, 14) for lat in (45, 47)}
    return cells | blocks


class AreaTest(unittest.TestCase):
    def test_failed_run_ferry_ends_are_dropped(self):
        sizes = {k: 5_000_000 for k in alps_cells()}
        sizes.update({key(n): 20_000 for n in FAILED_RUN_OUTSIDE})
        self.assertGreater(len(FAILED_RUN_OUTSIDE) * 2, len(sizes))  # the old check failed here
        kept, dropped = routing_data.split_area(sizes, [4.5, 42.5, 17.0, 48.5], ALPS)
        self.assertEqual(sorted(key(n) for n in FAILED_RUN_OUTSIDE), dropped)
        self.assertEqual(alps_cells(), kept)

    def test_bbox_when_there_is_no_outline(self):
        sizes = {k: 5_000_000 for k in alps_cells()}
        sizes[key("l1-45550")] = 20_000  # Tunisia
        kept, dropped = routing_data.split_area(sizes, [4.5, 42.5, 17.0, 48.5])
        self.assertEqual([key("l1-45550")], dropped)
        self.assertEqual(alps_cells(), kept)

    def test_wrong_numbering_still_fails(self):
        sizes = {k: 1_000 for k in alps_cells()}
        sizes.update({key(n): 5_000_000 for n in FAILED_RUN_OUTSIDE})
        with self.assertRaises(SystemExit):
            routing_data.split_area(sizes, [4.5, 42.5, 17.0, 48.5], ALPS)

    def test_edge_cell_meets_the_outline(self):
        # The square touches the outline only within the margin.
        self.assertTrue(routing_data.meets([16.75, 46.0, 17.75, 47.0], ALPS))
        self.assertFalse(routing_data.meets([17.0, 46.0, 18.0, 47.0], ALPS))


class CellsTest(unittest.TestCase):
    def test_manifest_lists_road_notes_and_drops_ferry_ends(self):
        with tempfile.TemporaryDirectory() as tmp:
            tiles = os.path.join(tmp, "tiles")
            inside = routing_data.tile_id_at(1, 10.5, 46.5)
            ferry = routing_data.tile_id_at(1, 10.5, 36.5)
            for rel, size in ((f"1/{inside // 1000:03d}/{inside % 1000:03d}.gph", 4000),
                              (f"1/{ferry // 1000:03d}/{ferry % 1000:03d}.gph", 100)):
                os.makedirs(os.path.dirname(os.path.join(tiles, rel)), exist_ok=True)
                with open(os.path.join(tiles, rel), "wb") as f:
                    f.write(b"x" * size)
            config = os.path.join(tmp, "area.json")
            with open(config, "w") as f:
                json.dump({"source": "", "bbox": [4.5, 42.5, 17.0, 48.5]}, f)
            poly = os.path.join(tmp, "area.poly")
            with open(poly, "w") as f:
                f.write("alps\n1\n" + "".join(f"  {x} {y}\n" for x, y in ALPS[0]) + "END\nEND\n")
            notes = os.path.join(tmp, "road-notes.json")
            with open(notes, "w") as f:
                f.write('{"format":1,"osmDate":"x","closures":[],"cameras":[]}')
            out = os.path.join(tmp, "out")
            routing_data.cells(config, tiles, out, "42", "2026-11-24", "3.9.2", None, outline=poly, notes=notes)
            with open(os.path.join(out, "cells.json")) as f:
                data = json.load(f)
            self.assertEqual([f"l1-{inside}"], [p["id"] for p in data["packs"]])
            with open(notes, "rb") as f:
                body = f.read()
            self.assertEqual({"file": "road-notes.json", "bytes": len(body),
                              "sha256": hashlib.sha256(body).hexdigest()}, data["roadNotes"])
            self.assertFalse(os.path.exists(os.path.join(out, f"l1-{ferry}-42.zip")))


def camera(osm_id, lon, lat):
    return json.dumps({"type": "Feature", "id": osm_id, "properties": {"highway": "speed_camera"},
                       "geometry": {"type": "Point", "coordinates": [lon, lat]}})


class CamerasTest(unittest.TestCase):
    def test_only_italian_cameras_and_every_closure(self):
        keep = road_notes.camera_country_check(road_notes.load_borders())
        lines = [
            camera("n1", 9.19, 45.46),   # Milan
            camera("n2", 8.54, 47.37),   # Zurich
            camera("n3", 7.26, 43.70),   # Nice
            camera("n4", 11.58, 48.14),  # Munich
            camera("n5", 12.45, 43.94),  # San Marino (a hole in Italy)
            camera("n6", 12.0, 41.0),    # open sea
            json.dumps({"type": "Feature", "id": "w1",
                        "properties": {"highway": "secondary", "motor_vehicle:conditional": "no @ (Nov-May)"},
                        "geometry": {"type": "LineString", "coordinates": [[7.75, 46.23], [7.76, 46.24]]}}),  # CH
        ]
        closures, cameras = road_notes.notes(lines, keep)
        self.assertEqual(["n1"], [c["id"] for c in cameras])
        self.assertEqual(["w1"], [c["id"] for c in closures])

    def test_border_margin(self):
        borders = {"IT": [[(10.0, 45.0), (11.0, 45.0), (11.0, 46.0), (10.0, 46.0)]],
                   "CH": [[(11.0, 45.0), (12.0, 45.0), (12.0, 46.0), (11.0, 46.0)]]}
        keep = road_notes.camera_country_check(borders)
        km = 1 / (111.32 * 0.7)  # about 1 km of longitude at 45.5 N, in degrees
        self.assertTrue(keep(10.5, 45.5))
        self.assertTrue(keep(10.0 - 0.5 * km, 45.5))   # just off the coarse outline, no other country
        self.assertFalse(keep(10.0 - 3 * km, 45.5))    # too far
        self.assertFalse(keep(11.0 + 0.5 * km, 45.5))  # close, but inside the neighbour

    def test_decode(self):
        # The example of Google's polyline documentation.
        points = road_notes.decode("_p~iF~ps|U_ulLnnqC_mqNvxq`@", 5)
        self.assertEqual([(-120.2, 38.5), (-120.95, 40.7), (-126.453, 43.252)],
                         [(round(x, 3), round(y, 3)) for x, y in points])


if __name__ == "__main__":
    unittest.main()
