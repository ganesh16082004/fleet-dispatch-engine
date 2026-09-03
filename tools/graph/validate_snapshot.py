#!/usr/bin/env python3
"""Validate a Fleet Dispatch CSV road-graph snapshot.

The validator streams the CSV files so validation does not require constructing
another in-memory Java RoadGraph. It checks structural integrity and reports
basic degree, weight, coordinate, and connectivity statistics.
"""

from __future__ import annotations

import argparse
import csv
import math
from collections import Counter, deque
from pathlib import Path


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("nodes", type=Path, help="nodes.csv")
    parser.add_argument("edges", type=Path, help="edges.csv")
    return parser.parse_args()


def fail(message: str) -> None:
    raise SystemExit(f"VALIDATION FAILED: {message}")


def validate(nodes_path: Path, edges_path: Path) -> None:
    if not nodes_path.is_file():
        fail(f"nodes file does not exist: {nodes_path}")
    if not edges_path.is_file():
        fail(f"edges file does not exist: {edges_path}")

    node_ids: set[int] = set()
    coordinates: dict[int, tuple[float, float]] = {}
    invalid_coordinates = 0
    duplicate_nodes = 0

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

            if node_id in node_ids:
                duplicate_nodes += 1
            node_ids.add(node_id)
            coordinates[node_id] = (lat, lon)
            if not (-90.0 <= lat <= 90.0 and -180.0 <= lon <= 180.0):
                invalid_coordinates += 1
            if not (math.isfinite(lat) and math.isfinite(lon)):
                invalid_coordinates += 1

    if duplicate_nodes:
        fail(f"duplicate node IDs: {duplicate_nodes}")
    if invalid_coordinates:
        fail(f"invalid node coordinates: {invalid_coordinates}")

    out_degree: Counter[int] = Counter()
    in_degree: Counter[int] = Counter()
    adjacency: dict[int, list[int]] = {node_id: [] for node_id in node_ids}
    distance_sum = 0.0
    distance_min = math.inf
    distance_max = 0.0
    time_sum = 0.0
    time_min = math.inf
    time_max = 0.0
    edge_count = 0
    missing_endpoints = 0
    invalid_distances = 0
    invalid_times = 0

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

            edge_count += 1
            if source not in node_ids or target not in node_ids:
                missing_endpoints += 1
            else:
                adjacency[source].append(target)
                out_degree[source] += 1
                in_degree[target] += 1

            if not math.isfinite(distance) or distance <= 0:
                invalid_distances += 1
            else:
                distance_sum += distance
                distance_min = min(distance_min, distance)
                distance_max = max(distance_max, distance)

            if not math.isfinite(travel_time) or travel_time <= 0:
                invalid_times += 1
            else:
                time_sum += travel_time
                time_min = min(time_min, travel_time)
                time_max = max(time_max, travel_time)

    if missing_endpoints:
        fail(f"edges reference missing node IDs: {missing_endpoints}")
    if invalid_distances:
        fail(f"invalid edge distances: {invalid_distances}")
    if invalid_times:
        fail(f"invalid edge travel times: {invalid_times}")

    isolated = sum(1 for node_id in node_ids if out_degree[node_id] == 0 and in_degree[node_id] == 0)
    zero_out = sum(1 for node_id in node_ids if out_degree[node_id] == 0)
    zero_in = sum(1 for node_id in node_ids if in_degree[node_id] == 0)

    # Weak connectivity over the directed graph. Since adjacency is already in
    # memory as Python integer lists, this avoids duplicating the full edge set.
    reverse_adjacency: dict[int, list[int]] = {node_id: [] for node_id in node_ids}
    with edges_path.open("r", newline="", encoding="utf-8") as stream:
        reader = csv.DictReader(stream)
        for row in reader:
            source = int(row["from"])
            target = int(row["to"])
            if source in node_ids and target in node_ids:
                reverse_adjacency[target].append(source)

    visited: set[int] = set()
    component_sizes: list[int] = []
    for start in node_ids:
        if start in visited:
            continue
        queue: deque[int] = deque([start])
        visited.add(start)
        size = 0
        while queue:
            current = queue.popleft()
            size += 1
            for neighbor in adjacency[current]:
                if neighbor not in visited:
                    visited.add(neighbor)
                    queue.append(neighbor)
            for neighbor in reverse_adjacency[current]:
                if neighbor not in visited:
                    visited.add(neighbor)
                    queue.append(neighbor)
        component_sizes.append(size)

    component_sizes.sort(reverse=True)
    largest_component = component_sizes[0] if component_sizes else 0
    coverage = (largest_component / len(node_ids) * 100.0) if node_ids else 0.0

    avg_out = edge_count / len(node_ids) if node_ids else 0.0
    avg_distance = distance_sum / edge_count if edge_count else 0.0
    avg_time = time_sum / edge_count if edge_count else 0.0

    print("=== Bengaluru Graph Validation ===")
    print()
    print("Nodes")
    print(f"  Count:                 {len(node_ids):,}")
    print(f"  Duplicate IDs:         {duplicate_nodes:,}")
    print(f"  Invalid coordinates:   {invalid_coordinates:,}")
    print()
    print("Edges")
    print(f"  Count:               {edge_count:,}")
    print(f"  Missing endpoints:     {missing_endpoints:,}")
    print(f"  Invalid distances:     {invalid_distances:,}")
    print(f"  Invalid travel times:  {invalid_times:,}")
    print()
    print("Degree")
    print(f"  Min out-degree:         {min(out_degree.values(), default=0)}")
    print(f"  Max out-degree:         {max(out_degree.values(), default=0)}")
    print(f"  Avg out-degree:         {avg_out:.3f}")
    print(f"  Isolated nodes:         {isolated:,}")
    print(f"  Zero out-degree:        {zero_out:,}")
    print(f"  Zero in-degree:         {zero_in:,}")
    print()
    print("Distance / Travel Time")
    print(f"  Distance min (m):       {distance_min if edge_count else 0:.3f}")
    print(f"  Distance avg (m):       {avg_distance:.3f}")
    print(f"  Distance max (m):       {distance_max:.3f}")
    print(f"  Time min (s):            {time_min if edge_count else 0:.3f}")
    print(f"  Time avg (s):            {avg_time:.3f}")
    print(f"  Time max (s):            {time_max:.3f}")
    print()
    print("Connectivity (weak)")
    print(f"  Components:              {len(component_sizes):,}")
    print(f"  Largest component:       {largest_component:,}")
    print(f"  Largest coverage:        {coverage:.3f}%")
    print(f"  Nodes outside largest:   {len(node_ids) - largest_component:,}")
    print()
    print("Status: PASS")


def main() -> None:
    args = parse_args()
    validate(args.nodes, args.edges)


if __name__ == "__main__":
    main()
