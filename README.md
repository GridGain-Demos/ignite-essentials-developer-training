# Apache Ignite Essentials — Developer Training

A free instructor-led training on GridGain / Apache Ignite essential capabilities and architecture internals. Check the [complete schedule](https://www.gridgain.com/products/services/training/apache-ignite-workshop-Key-design-principles-for-building-data-intensive-applications) and join an upcoming session.

During the live training you exercise a three-node GG8 cluster running in Docker, reading and computing over a Media Store dataset with two thin-client apps — `KeyValueApp` (partition / affinity exploration) and `ComputeApp` (a distributed compute task deployed server-side).

## Table of Contents

- [Prerequisites](#prerequisites)
- [Project Layout](#project-layout)
- [1. Clone the Project](#1-clone-the-project)
- [2. Start the Cluster](#2-start-the-cluster)
- [3. Load the Media Store Schema](#3-load-the-media-store-schema)
- [4. Affinity Co-location — SQL Joins](#4-affinity-co-location--sql-joins)
- [5. Build the Training Apps](#5-build-the-training-apps)
- [6. KeyValueApp — Partition Distribution](#6-keyvalueapp--partition-distribution)
- [7. ComputeApp — Distributed Compute](#7-computeapp--distributed-compute)
- [8. Shutdown](#8-shutdown)
- [Troubleshooting](#troubleshooting)

---

## Prerequisites

- Git
- Docker Desktop
- Your favorite IDE (IntelliJ, Eclipse, VS Code, or a plain editor)

JDK 17 and Maven are optional — the `app` sidecar provides both. Install JDK 17 locally only if you use the standalone paths.

**Linux only:** the GridGain container image runs as UID 10000. If nodes fail to start on Linux, run `chown -R 10000:10000 docker/data/` and retry.

---

## Project Layout

Three GridGain nodes (`node1`, `node2`, `node3`) run on an isolated Docker bridge network. Only `node1` publishes port `10800` to the host — that is the thin-client address your apps connect to. The `app` service is a Maven 3.9 + JDK 17 sidecar: it shares the project directory via a bind mount, so you can build and run the training apps without installing Maven locally, and any edit you make in your IDE is picked up by the next `docker compose run` immediately.

```
docker/
  docker-compose.yaml     ← full topology rationale and mount details live here
  config/                 ← training-node-config.xml + ignite-log4j2.xml,
  │                          bind-mounted read-only into every server node
  data/
  │  node1/log/           ← node1 log files on the host (also via `docker compose logs node1`)
  │  node2/log/           ← node2 log files
  │  node3/log/           ← node3 log files
  libs/                   ← server-tasks.jar lands here after a build;
                             nodes load it from this directory at startup
libs/                     ← apps.jar lands here after a build;
                             run KeyValueApp and ComputeApp from this jar
src/                      ← training source — edit these for the exercises
```

The sidecar build writes to `docker/libs/` while the server nodes have that directory bind-mounted. Docker Desktop holds a lock on bind-mounted directories while containers are running, so **always bring the cluster down before a sidecar build** and back up after.

---

## 1. Clone the Project

```bash
git clone -b gg8_docker https://github.com/GridGain-Demos/ignite-essentials-developer-training.git
cd ignite-essentials-developer-training
```

---

## 2. Start the Cluster

```bash
docker compose -f docker/docker-compose.yaml up -d
```

Verify all three nodes joined:

```bash
docker compose -f docker/docker-compose.yaml logs node1 | grep "Topology snapshot" | tail -1
```

Expect `servers=3` in the output.

---

## 3. Load the Media Store Schema

```bash
docker compose -f docker/docker-compose.yaml exec -T node1 /opt/gridgain/bin/sqlline.sh -u "jdbc:ignite:thin://127.0.0.1/" --silent=true < config/media_store.sql
```

Verify row counts:

```bash
printf 'SELECT COUNT(*) FROM Artist;\nSELECT COUNT(*) FROM Customer;\n!quit\n' | docker compose -f docker/docker-compose.yaml exec -T node1 /opt/gridgain/bin/sqlline.sh -u "jdbc:ignite:thin://127.0.0.1/" --silent=true
```

Expect **275** artists and **59** customers.

---

## 4. Affinity Co-location — SQL Joins

Ignite supports distributed SQL including joins across partitioned caches. This section demonstrates why co-location matters and how to fix a non-colocated schema.

### Single-table query

Run the top-20 longest tracks against a single cache:

```bash
docker compose -f docker/docker-compose.yaml exec -T node1 /opt/gridgain/bin/sqlline.sh -u "jdbc:ignite:thin://127.0.0.1/" --silent=true < sql/top_20_longest_tracks.sql
```

### Joining non-colocated tables

`Track` and `Artist` are partitioned independently — their records land on different nodes. Run the join without any hint:

```bash
docker compose -f docker/docker-compose.yaml exec -T node1 /opt/gridgain/bin/sqlline.sh -u "jdbc:ignite:thin://127.0.0.1/" --silent=true < sql/top_20_longest_tracks_with_authors.sql
```

The `artist` column will be blank for many rows — each node can only see the Track records it holds locally, not the Artist records on other nodes.

Enable distributed joins to get complete results (Ignite shuffles data across nodes during the join phase — correct but expensive):

```bash
docker compose -f docker/docker-compose.yaml exec -T node1 /opt/gridgain/bin/sqlline.sh -u "jdbc:ignite:thin://127.0.0.1/?distributedJoins=true" --silent=true < sql/top_20_longest_tracks_with_authors.sql
```

All `artist` values are now filled in.

### Fixing with affinity co-location

The proper solution is to store each Track on the same node as its Artist. Edit `config/media_store.sql`, find the `CREATE TABLE Track` statement, and make two changes:

1. Change `PRIMARY KEY (TrackId)` → `PRIMARY KEY (TrackId, ArtistId)`
2. Add `affinityKey=ArtistId` to the `WITH` clause

`DROP TABLE IF EXISTS` removes the cache but not the binary type metadata. Remove the stale metadata for both types before reloading, otherwise the INSERT streaming will fail with a metadata conflict:

```bash
echo "y" | docker compose -f docker/docker-compose.yaml exec -T node1 /opt/gridgain/bin/control.sh --meta remove --typeName training.model.TrackKey
```

```bash
echo "y" | docker compose -f docker/docker-compose.yaml exec -T node1 /opt/gridgain/bin/control.sh --meta remove --typeName training.model.Track
```

Now reload the schema:

```bash
docker compose -f docker/docker-compose.yaml exec -T node1 /opt/gridgain/bin/sqlline.sh -u "jdbc:ignite:thin://127.0.0.1/" --silent=true < config/media_store.sql
```

Run the join again without `distributedJoins`:

```bash
docker compose -f docker/docker-compose.yaml exec -T node1 /opt/gridgain/bin/sqlline.sh -u "jdbc:ignite:thin://127.0.0.1/" --silent=true < sql/top_20_longest_tracks_with_authors.sql
```

All `artist` values are filled in — and no cross-node data shuffling was needed.

---

## 5. Build the Training Apps

Two paths — pick whichever suits your environment.

### Standalone (host Maven)

The cluster can stay up during a host Maven build.

```bash
mvn clean package -P apps
```

### Docker

Bring the cluster down before building — Docker Desktop blocks writes to bind-mounted directories while containers are running:

```bash
docker compose -f docker/docker-compose.yaml down
```

```bash
docker compose -f docker/docker-compose.yaml run --rm app mvn -B clean package -P apps
```

```bash
docker compose -f docker/docker-compose.yaml up -d
```

Verify all three nodes rejoined:

```bash
docker compose -f docker/docker-compose.yaml logs node1 | grep "Topology snapshot" | tail -1
```

### Build output

Both paths produce:

- `libs/apps.jar` — shaded fat jar; run `KeyValueApp` and `ComputeApp` against it
- `docker/libs/server-tasks.jar` — task classes only, no dependencies; loaded by each server node from its bind-mounted `/opt/gridgain/libs/user_libs/`

**If `server-tasks.jar` changes**, the cluster must be restarted so each node reloads it from disk:

```bash
docker compose -f docker/docker-compose.yaml down
```

```bash
docker compose -f docker/docker-compose.yaml up -d
```

---

## 6. KeyValueApp — Partition Distribution

`KeyValueApp` reads Artists 1–99 from the `Artist` cache via the thin client. Complete `TODO #1` in `src/main/java/training/KeyValueApp.java` to explore how Ignite distributes records across partitions and nodes.

**Standalone:**

```bash
java @src/main/resources/j17.params -cp libs/apps.jar training.KeyValueApp
```

**Docker:**

```bash
docker compose -f docker/docker-compose.yaml run --rm app java @/work/src/main/resources/j17.params -cp /work/libs/apps.jar training.KeyValueApp
```

Expect 99 artists printed, including "Jimi Hendrix", "Joe Satriani", "Legião Urbana". Windows console may garble the UTF-8 characters — they are correct upstream.

---

## 7. ComputeApp — Distributed Compute

`ComputeApp` triggers `TopPayingCustomersTask`, a server-deployed compute task that runs on every cluster node in parallel. Each node scans its local `InvoiceLine` records, aggregates per-customer totals, and returns its top-N; the thin client merges all partial results. The local scan is correct because `InvoiceLine` is co-located with `Customer` by `CustomerId` (`affinityKey=CustomerId` in `config/media_store.sql`) — the same affinity principle demonstrated with Track/Artist in section 4.

**Standalone:**

```bash
java @src/main/resources/j17.params -cp libs/apps.jar training.ComputeApp
```

**Docker:**

```bash
docker compose -f docker/docker-compose.yaml run --rm app java @/work/src/main/resources/j17.params -cp /work/libs/apps.jar training.ComputeApp
```

Expect output similar to:

```
>>> Connected to localhost:10800
>>> Top 5 Paying Listeners Across All Cluster Nodes
TopCustomer{customerId=6, fullName='Helena Holý', ...}
TopCustomer{customerId=26, fullName='Richard Cunningham', ...}
TopCustomer{customerId=57, fullName='Luis Rojas', ...}
TopCustomer{customerId=45, fullName='Hugh O'Reilly', ...}
TopCustomer{customerId=144, fullName='Ladislav Kovács', ...}
```

Docker output shows `Connected to node1:10800` instead of `localhost:10800`.

To confirm the task ran on every node (not just one), check the cluster logs:

```bash
docker compose -f docker/docker-compose.yaml logs node1 node2 node3 | grep "Task locally deployed: class training"
```

Expect three matching lines — one per node — each showing `Task locally deployed: class training.compute.TopPayingCustomersTask`.

### Modify the task

Update `src/main/java/training/ComputeApp.java`: change `int customersCount = 5` to `int customersCount = 10`. The task receives this value as its argument, so no cluster restart is needed — only `apps.jar` changes. Rebuild:

**Standalone:**

```bash
mvn clean package -P apps
```

**Docker:**

```bash
docker compose -f docker/docker-compose.yaml down
```

```bash
docker compose -f docker/docker-compose.yaml run --rm app mvn -B clean package -P apps
```

```bash
docker compose -f docker/docker-compose.yaml up -d
```

Verify all three nodes rejoined:

```bash
docker compose -f docker/docker-compose.yaml logs node1 | grep "Topology snapshot" | tail -1
```

Run `ComputeApp` again — the output now shows 10 customers.

---

## 8. Shutdown

```bash
docker compose -f docker/docker-compose.yaml down
```

The `docker/data/` directory is kept on the host (holds logs and marshaller metadata; only gains `db/` and `wal/` subdirectories when persistence is enabled).

---

## Troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| `docker compose up -d` hangs on the second attempt | Port 10800 still held by a cluster running in another directory | `docker compose -f docker/docker-compose.yaml down` in that directory first |
| Nodes start but produce no logs; `docker/data/` empty (Linux only) | Container runs as UID 10000; host `docker/data/` owned by your user | `chown -R 10000:10000 docker/data/` |
| `InaccessibleObjectException: Unable to make field long java.nio.Buffer.address accessible` | Missing `@src/main/resources/j17.params` before `-cp` | Add the `@` argfile argument |
| Sidecar: `Connection refused` to thin client | `IGNITE_ADDRESS` env var not set or compose service using `localhost` | Check `environment:` block in `docker/docker-compose.yaml` sets `IGNITE_ADDRESS=node1:10800` |
| `ComputeApp`: `Compute grid functionality is disabled for thin clients` | `ThinClientConfiguration.maxActiveComputeTasksPerConnection=0` on the server | Check `docker/config/training-node-config.xml` has the override set to 100 |
| `ComputeApp`: `Unknown task name or failed to auto-deploy task: TopPayingCustomersTask` even after cluster restart | Wrong cluster is running — another training's cluster has no `server-tasks.jar` | Run `docker inspect <node1-container-id> --format '{{range .Mounts}}{{.Source}}{{println}}{{end}}'` to confirm which `docker/libs` is mounted; bring down the wrong cluster first |
| Sidecar build: `Error assembling JAR: Problem creating output file` | Docker Desktop for Windows blocks writes to host directories currently bind-mounted in running containers | Bring the cluster down before building (`docker compose down`), then bring it back up after |
| Schema reload fails with `Binary type has different affinity key fields` for `TrackKey` | `DROP TABLE` removes the cache but not binary type metadata; stale affinity key registration conflicts with the new one | Run `echo "y" \| docker compose exec -T node1 /opt/gridgain/bin/control.sh --meta remove --typeName training.model.TrackKey` and the same for `training.model.Track`, then reload. If the cluster was restarted, also clear `docker/data/node{1,2,3}/marshaller/` first. |
