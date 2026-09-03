#!/usr/bin/env python3
"""Inspect suspiciously short edges in a Fleet Dispatch CSV road graph.

The analysis is intentionally streaming over edges. Node coordinates are loaded
once so the shortest edges can be reported with their geographic endpoints.
"""

from __future__ import annotations

import argparse
import csv
import heapq
import math
from pathlib import Path


THRESHOLDS_METERS = (0.1, 1.0, 5.0, 10.0)
DEFAULT_SHORTEST_COUNT = 20


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("nodes", type=Path, help="nodes.csv")
    parser.add_argument("edges", type=Path, help="edges.csv")
    parser.add_argument(
        "--shortest",
        type=int,
        default=DEFAULT_SHORTEST_COUNT,
        help=f"number of shortest edges to print (default: {DEFAULT_SHORTEST_COUNT})",
    )
    return parser.parse_args()


def fail(message: str) -> None:
    raise SystemExit(f"ANALYSIS FAILED: {message}")


def load_coordinates(nodes_path: Path) -> dict[int, tuple[float, float]]:
    if not nodes_path.is_file():
        fail(f"nodes file does not exist: {nodes_path}")

    coordinates: dict[int, tuple[float, float]] = {}
    with nodes_path.open("r", newline="", encoding="utf-8") as stream:
        reader = csv.DictReader(stream)
        expected = ["id", "latitude", "longitude"]
        if reader.fieldnames != expected:
            fail(f"nodes header must be {expected}, got {reader.fieldnames}")
        for row_number, row in enumerate(reader, start=2):
            try:
                node_id = int(row["id"])
                lat = float(row["latitude"])
                lon = float(row["longitude"])
            except (TypeError, ValueError) as exc:
                fail(f"malformed node row {row_number}: {exc}")
            if not (math.isfinite(lat) and math.isfinite(lon)):
                fail(f"non-finite coordinates at node row {row_number}")
            coordinates[node_id] = (lat, lon)
    return coordinates


def analyze(nodes_path: Path, edges_path: Path, shortest_count: int) -> None:
    if shortest_count <= 0:
        fail("--shortest must be greater than zero")
    if not edges_path.is_file():
        fail(f"edges file does not exist: {edges_path}")

    coordinates = load_coordinates(nodes_path)
    counts = {threshold: 0 for threshold in THRESHOLDS_METERS}
    edge_count = 0
    shortest: list[tuple[float, int, int, float]] = []

    with edges_path.open("r", newline="", encoding="utf-8") as stream:
        reader = csv.DictReader(stream)
        expected = ["from", "to", "distance_meters", "travel_time_seconds"]
        if reader.fieldnames != expected:
            fail(f"edges header must be {expected}, got {reader.fieldnames}")

        for row_number, row in enumerate(reader, start=2):
            try:
                source = int(row["from"])
                target = int(row["to"])
                distance = float(row["distance_meters"])
                travel_time = float(row["travel_time_seconds"])
            except (TypeError, ValueError) as exc:
                fail(f"malformed edge row {row_number}: {exc}")

            if not math.isfinite(distance) or distance <= 0:
                fail(f"invalid edge distance at row {row_number}: {distance}")
            if not math.isfinite(travel_time) or travel_time <= 0:
                fail(f"invalid travel time at row {row_number}: {travel_time}")
            if source not in coordinates or target not in coordinates:
                fail(f"edge row {row_number} references unknown endpoint")

            edge_count += 1
            for threshold in THRESHOLDS_METERS:
                if distance < threshold:
                    counts[threshold] += 1

            item = (-distance, source, target, travel_time)
            if len(shortest) < shortest_count:
                heapq.heappush(shortest, item)
            elif distance < -shortest[0][0]:
                heapq.heapreplace(shortest, item)

    shortest_edges = sorted(
        ((-distance, source, target, travel_time) for distance, source, target, travel_time in shortest),
        key=lambda item: item[0],
    )

    print("=== Short Edge Analysis ===")
    print()
    print(f"Edges analyzed: {edge_count:,}")
    print()
    print("Thresholds")
    for threshold in THRESHOLDS_METERS:
        count = counts[threshold]
        percentage = (count / edge_count * 100.0) if edge_count else 0.0
        print(f"  < {threshold:g} m:              {count:,} ({percentage:.6f}%)")
    print()
    print(f"Shortest {shortest_count} edges")
    for index, (distance, source, target, travel_time) in enumerate(shortest_edges, start=1):
        source_coord = coordinates[source]
        target_coord = coordinates[target]
        print(f"  {index:2d}. {distance:.6f} m | {travel_time:.6f} s | {source} -> {target}")
        print(f"      source: ({source_coord[0]:.7f}, {source_coord[1]:.7f})")
        print(f"      target: ({target_coord[0]:.7f}, {target_coord[1]:.7f})")


def main() -> None:
    args = parse_args()
    analyze(args.nodes, args.edges, args.shortest)


if __name__ == "__main__":
    main()
