# OSM preprocessing

This directory contains the reproducible conversion step from an OpenStreetMap XML extract to the Fleet Dispatch Engine's canonical road-network snapshot.

## Input

The converter accepts either:

- `.osm` XML
- `.osm.gz` compressed XML

A city-sized extract can be obtained from BBBike or another OSM extract provider. BBBike publishes city and custom extracts in multiple formats, including OSM XML and PBF. The raw data is not committed to this repository. See the project `.gitignore` for the local `/data/` convention.

## Pipeline

```text
OSM XML/XML.GZ
     |
     v
parse nodes + highway ways
     |
     v
filter drivable road classes/access restrictions
     |
     v
apply one-way semantics
     |
     v
split ways into directed segments
     |
     v
Haversine distance + baseline speed -> travel time
     |
     v
nodes.csv + edges.csv
     |
     v
CsvRoadNetworkLoader
     |
     v
RoadGraph
```

The preprocessing tool intentionally uses only Python's standard library. This keeps the data transformation easy to reproduce without introducing an OSM parser dependency into the Java routing core.

## Run

From the repository root on Windows PowerShell:

```powershell
python tools/osm/osm_to_snapshot.py data/raw/bengaluru.osm.gz data/processed/bengaluru
```

Expected output files:

```text
data/processed/bengaluru/nodes.csv
data/processed/bengaluru/edges.csv
```

## Road semantics

The first version retains common drivable urban classes from `motorway` through `service`. Roads explicitly marked `access=no/private`, `vehicle=no/private`, or `motor_vehicle=no/private` are excluded.

`oneway=yes|1|true` creates forward edges only. `oneway=-1` reverses the way. Other values are treated as bidirectional for this first version.

Travel time uses the OSM `maxspeed` tag when it can be parsed, otherwise a conservative road-class default speed. This is a **baseline free-flow model**, not live traffic data; dynamic traffic belongs in the later simulation layer.

## Data provenance

OSM data is © OpenStreetMap contributors and is licensed under the Open Data Commons Open Database License (ODbL). Preserve the source/provenance information for any redistributed snapshots or derived database extracts.
