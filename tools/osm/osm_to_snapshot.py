#!/usr/bin/env python3
"""Convert a large OSM XML/XML.GZ extract into the Fleet Dispatch CSV snapshot.

The preprocessing pass is intentionally streaming. OSM extracts can be much larger
than the final road graph, so we avoid retaining every way in Python memory.

Input:
  .osm or .osm.gz containing OSM nodes and ways.

Output:
  nodes.csv: id,latitude,longitude
  edges.csv: from,to,distance_meters,travel_time_seconds

Road semantics are deliberately conservative: only common drivable highway
classes are retained; explicit motor-vehicle/vehicle/access restrictions are
excluded; oneway=-1 reverses the directed segments.
"""

from __future__ import annotations

import argparse
import csv
import gzip
import math
import sqlite3
import tempfile
import xml.etree.ElementTree as ET
from collections import OrderedDict
from dataclasses import dataclass
from pathlib import Path
from typing import TextIO


ALLOWED_HIGHWAYS = {
    "motorway", "motorway_link", "trunk", "trunk_link",
    "primary", "primary_link", "secondary", "secondary_link",
    "tertiary", "tertiary_link", "unclassified", "residential",
    "living_street", "service",
}

DEFAULT_SPEED_KPH = {
    "motorway": 80.0, "motorway_link": 55.0, "trunk": 65.0, "trunk_link": 50.0,
    "primary": 50.0, "primary_link": 45.0, "secondary": 40.0, "secondary_link": 35.0,
    "tertiary": 35.0, "tertiary_link": 30.0, "unclassified": 30.0,
    "residential": 25.0, "living_street": 15.0, "service": 15.0,
}

EARTH_RADIUS_M = 6_371_008.8
CACHE_SIZE = 100_000


@dataclass(frozen=True)
class OsmNode:
    latitude: float
    longitude: float


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("input", type=Path, help="OSM .osm or .osm.gz file")
    parser.add_argument("output_dir", type=Path, help="Directory for nodes.csv and edges.csv")
    return parser.parse_args()


def open_input(path: Path) -> TextIO:
    if path.suffix.lower() == ".gz":
        return gzip.open(path, "rt", encoding="utf-8")
    return path.open("r", encoding="utf-8")


def haversine_meters(a: OsmNode, b: OsmNode) -> float:
    lat1 = math.radians(a.latitude)
    lat2 = math.radians(b.latitude)
    dlat = lat2 - lat1
    dlon = math.radians(b.longitude - a.longitude)
    h = math.sin(dlat / 2) ** 2 + math.cos(lat1) * math.cos(lat2) * math.sin(dlon / 2) ** 2
    return 2.0 * EARTH_RADIUS_M * math.asin(math.sqrt(h))


def parse_speed_kph(value: str | None, highway: str) -> float:
    if not value:
        return DEFAULT_SPEED_KPH[highway]
    normalized = value.strip().lower().replace(" ", "")
    if normalized in {"none", "signals", "walk"}:
        return DEFAULT_SPEED_KPH[highway]

    numeric = ""
    unit = "kph"
    for char in normalized:
        if char.isdigit() or char == ".":
            numeric += char
        else:
            if "mph" in normalized:
                unit = "mph"
            break
    try:
        speed = float(numeric)
    except ValueError:
        return DEFAULT_SPEED_KPH[highway]
    if speed <= 0 or not math.isfinite(speed):
        return DEFAULT_SPEED_KPH[highway]
    return speed * 1.609344 if unit == "mph" else speed


def is_drivable(tags: dict[str, str], highway: str) -> bool:
    if highway not in ALLOWED_HIGHWAYS:
        return False
    for key in ("access", "vehicle", "motor_vehicle"):
        value = tags.get(key, "").strip().lower()
        if value in {"no", "private"}:
            return False
    return True


def build_node_store(path: Path, database: sqlite3.Connection) -> int:
    """First pass: stream every OSM node into a disk-backed SQLite table."""
    database.execute("CREATE TABLE nodes (id INTEGER PRIMARY KEY, lat REAL NOT NULL, lon REAL NOT NULL)")
    database.execute("PRAGMA synchronous=OFF")
    database.execute("PRAGMA journal_mode=MEMORY")
    count = 0
    batch: list[tuple[int, float, float]] = []
    with open_input(path) as stream:
        for event, element in ET.iterparse(stream, events=("end",)):
            if element.tag != "node":
                continue
            node_id = int(element.attrib["id"])
            batch.append((node_id, float(element.attrib["lat"]), float(element.attrib["lon"])))
            count += 1
            if len(batch) >= 20_000:
                database.executemany("INSERT INTO nodes(id, lat, lon) VALUES (?, ?, ?)", batch)
                database.commit()
                batch.clear()
            element.clear()
    if batch:
        database.executemany("INSERT INTO nodes(id, lat, lon) VALUES (?, ?, ?)", batch)
        database.commit()
    return count


def lookup_node(database: sqlite3.Connection, node_id: int, cache: OrderedDict[int, OsmNode]) -> OsmNode | None:
    cached = cache.get(node_id)
    if cached is not None:
        cache.move_to_end(node_id)
        return cached
    row = database.execute("SELECT lat, lon FROM nodes WHERE id = ?", (node_id,)).fetchone()
    if row is None:
        return None
    node = OsmNode(row[0], row[1])
    cache[node_id] = node
    cache.move_to_end(node_id)
    if len(cache) > CACHE_SIZE:
        cache.popitem(last=False)
    return node


def write_snapshot(path: Path, database: sqlite3.Connection, output_dir: Path) -> tuple[int, int]:
    output_dir.mkdir(parents=True, exist_ok=True)
    edges_path = output_dir / "edges.csv"
    if edges_path.exists():
        edges_path.unlink()

    used_nodes: set[int] = set()
    edge_rows: list[tuple[int, int, float, float]] = []
    cache: OrderedDict[int, OsmNode] = OrderedDict()

    with open_input(path) as stream:
        for event, element in ET.iterparse(stream, events=("end",)):
            if element.tag != "way":
                continue
            refs: list[int] = []
            tags: dict[str, str] = {}
            for child in element:
                if child.tag == "nd":
                    refs.append(int(child.attrib["ref"]))
                elif child.tag == "tag":
                    tags[child.attrib["k"]] = child.attrib["v"]

            highway = tags.get("highway")
            if highway is None or not is_drivable(tags, highway) or len(refs) < 2:
                element.clear()
                continue

            speed_mps = parse_speed_kph(tags.get("maxspeed"), highway) / 3.6
            if speed_mps <= 0:
                element.clear()
                continue

            direction = tags.get("oneway", "").strip().lower()
            reverse = direction == "-1"
            bidirectional = direction not in {"yes", "1", "true", "-1"}

            for left, right in zip(refs, refs[1:]):
                left_node = lookup_node(database, left, cache)
                right_node = lookup_node(database, right, cache)
                if left_node is None or right_node is None:
                    continue
                distance = haversine_meters(left_node, right_node)
                if distance <= 0 or not math.isfinite(distance):
                    continue
                travel_time = distance / speed_mps
                if reverse:
                    edge_rows.append((right, left, distance, travel_time))
                else:
                    edge_rows.append((left, right, distance, travel_time))
                    if bidirectional:
                        edge_rows.append((right, left, distance, travel_time))
                used_nodes.add(left)
                used_nodes.add(right)
                if len(edge_rows) >= 20_000:
                    append_edges(edges_path, edge_rows)
                    edge_rows.clear()
            element.clear()

    if edge_rows:
        append_edges(edges_path, edge_rows)

    with (output_dir / "nodes.csv").open("w", newline="", encoding="utf-8") as node_file:
        writer = csv.writer(node_file)
        writer.writerow(("id", "latitude", "longitude"))
        for node_id in sorted(used_nodes):
            node = lookup_node(database, node_id, cache)
            if node is not None:
                writer.writerow((node_id, f"{node.latitude:.7f}", f"{node.longitude:.7f}"))
    return len(used_nodes), count_csv_rows(edges_path)


def append_edges(path: Path, rows: list[tuple[int, int, float, float]]) -> None:
    write_header = not path.exists()
    with path.open("a", newline="", encoding="utf-8") as edge_file:
        writer = csv.writer(edge_file)
        if write_header:
            writer.writerow(("from", "to", "distance_meters", "travel_time_seconds"))
        for row in rows:
            writer.writerow((row[0], row[1], f"{row[2]:.3f}", f"{row[3]:.3f}"))


def count_csv_rows(path: Path) -> int:
    with path.open("r", encoding="utf-8") as stream:
        return max(0, sum(1 for _ in stream) - 1)


def main() -> None:
    args = parse_args()
    if not args.input.is_file():
        raise SystemExit(f"Input file does not exist: {args.input}")

    with tempfile.TemporaryDirectory(prefix="fleet-osm-") as temp_dir:
        database_path = Path(temp_dir) / "nodes.sqlite"
        database = sqlite3.connect(database_path)
        try:
            node_count = build_node_store(args.input, database)
            snapshot_nodes, edge_count = write_snapshot(args.input, database, args.output_dir)
        finally:
            database.close()

    print(f"OSM nodes parsed: {node_count:,}")
    print(f"Snapshot nodes:   {snapshot_nodes:,}")
    print(f"Snapshot edges:   {edge_count:,}")
    print(f"Wrote: {args.output_dir / 'nodes.csv'}")
    print(f"Wrote: {args.output_dir / 'edges.csv'}")


if __name__ == "__main__":
    main()
