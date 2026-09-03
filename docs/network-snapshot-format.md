# Road Network Snapshot Format

The dispatch engine consumes a deterministic two-file CSV snapshot. Keeping this format separate from OSM parsing lets the routing core remain independent of external map-data formats.

## `nodes.csv`

Header:

```text
id,latitude,longitude
```

Each row contains a non-negative node identifier and its geographic coordinates in decimal degrees.

## `edges.csv`

Header:

```text
from,to,distance_meters,travel_time_seconds
```

Each row represents one **directed** road segment. A two-way road is represented by two rows, one for each direction. `distance_meters` and `travel_time_seconds` must both be positive and finite.

## Example

```text
# nodes.csv
id,latitude,longitude
1,12.9716,77.5946
2,12.9720,77.5950
```

```text
# edges.csv
from,to,distance_meters,travel_time_seconds
1,2,60.0,6.0
2,1,60.0,6.0
```

The future OSM preprocessing pipeline will produce this same canonical snapshot format. The runtime engine will then load it through `CsvRoadNetworkLoader` and construct the immutable `RoadGraph` used by Dijkstra and A*.
