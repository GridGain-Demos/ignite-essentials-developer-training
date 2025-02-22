# [Foundation Course] Apache Ignite Essentials: Key Design Principles for Building Data-Intensive Applications

This project is designed for a free instructor-led training on the Ignite essential capabilities and architecture internals.
Check [the complete schedule](https://www.gridgain.com/products/services/training/apache-ignite-workshop-Key-design-principles-for-building-data-intensive-applications) and join one of our upcoming training sessions.

## Setting Up Environment

* Java Developer Kit, version 11, 17 or 21
* Apache Maven 3.0 or later
* Docker
* Your favorite IDE, such as IntelliJ IDEA, or Eclipse, or a simple text editor.

## Clone The Project

1. Clone the training project with Git or download it as an archive:
    ```bash
    git clone https://github.com/GridGain-Demos/ignite-essentials-developer-training.git
    ```

2. (optionally), open the project in your favourite IDE such as IntelliJ or Eclipse, or just use a simple text editor
and command-line instructions prepared for all the samples.    

## Starting Ignite Cluster

Start a two-node Ignite cluster:

1. Open a terminal window and navigate to the root directory of this project.

2. Start your nodes using Docker Compose:
    ```bash
   docker compose -f docker-compose.yml up
   ```

3. Initialise the cluster:
    ```bash
   docker run -e LANG=C.UTF-8 -e LC_ALL=C.UTF-8 -v ./config/media_store.sql:/opt/ignite/downloads/media_store.sql --rm --network ignite3_default -it apacheignite/ignite:3.0.0 cli
   connect http://node1:10300
   cluster init --name=docker --metastorage-group=node1,node2 
    ```
 
## Creating Media Store Schema and Loading Data

Now you need to create a Media Store schema and load the cluster with sample data. Use SQLLine tool to achieve that:

1. Open a terminal window and navigate to the root directory of this project.
2. Load the media store database:
    ```bash
   docker run -e LANG=C.UTF-8 -e LC_ALL=C.UTF-8 -v ./config/media_store.sql:/opt/ignite/downloads/media_store.sql --rm --network ignite3_default -it apacheignite/ignite:3.0.0 cli
   connect http://node1:10300
   sql --file=/opt/ignite/downloads/media_store.sql
    ```

Keep the connection open as you'll use it for following exercises.

## Data Partitioning - Checking Data Distribution

With the Media Store database loaded, you can check how Ignite distributed the records within the cluster:

1. Open a SQL prompt:
    ```bash
   docker run -e LANG=C.UTF-8 -e LC_ALL=C.UTF-8 --rm --network ignite3_default -it apacheignite/ignite:3.0.0 cli
   connect http://node1:10300
   sql
   select * from system.local_partition_states where table_name = 'INVOICE';
   select "__part", count(1) from invoice group by "__part";
    ```

2. While on that screen, follow the instructor to learn some insights.

Optional, scale out the cluster by the third node. You'll see that some partitions were rebalanced to the new node.

## Affinity Co-location - Optimizing Complex SQL Queries With JOINs

Ignite supports SQL for data processing including distributed joins, grouping and sorting. In this section, you're 
going to run basic SQL operations as well as more advanced ones.

### Querying Single Table

1. Use the CLI to open a SQL prompt:
     ```bash
   docker run -e LANG=C.UTF-8 -e LC_ALL=C.UTF-8 --rm --network ignite3_default -it apacheignite/ignite:3.0.0 cli
   connect http://node1:10300
   sql
    ```

2. Run the following query to find top-20 longest tracks:

    ```sql
    SELECT trackid, name, MAX(milliseconds / (1000 * 60)) as duration FROM track
    WHERE genreId < 17
    GROUP BY trackid, name ORDER BY duration DESC LIMIT 20;
    ```

### Joining Two Colocated Tables

1. Modify the previous query by adding information about an author. You do this by doing a LEFT
JOIN with the `Artist` table:

    ```sql
   SELECT track.trackId, track.name as track_name, genre.name as genre, artist.name as artist,
   MAX(milliseconds / (1000 * 60)) as duration FROM track
   LEFT JOIN artist ON track.artistId = artist.artistId
   JOIN genre ON track.genreId = genre.genreId
   WHERE track.genreId < 17
   GROUP BY track.trackId, track.name, genre.name, artist.name ORDER BY duration DESC LIMIT 20;
   ```

2. Try adding the phrase "EXPLAIN PLAN FOR" at the beginning of the above query to see how Ignite will execute it.
3. Examine the output. Your instructor will give hints for what to look for. It will look something like this:
```bash
Limit(fetch=[20]): rowcount = 20.0, cumulative cost = IgniteCost [rowCount=15318.06, cpu=77499.96615043783, memory=33461.76, io=178134.0, network=101068.0], id = 35293  
```

## Running Co-located Compute Tasks

Run `training.ComputeApp` that uses Apache Ignite compute capabilities for a calculation of top-5 paying customers.
The compute task executes on every cluster node, iterates through local records and responds to the application that 
merges partial results.

1. Build an executable JAR with the applications' classes (or just start the app with IntelliJ IDEA or Eclipse):
    ```bash
    mvn clean package 
    ```
2. Load the code into your cluster:
    ```bash
   docker run -e LANG=C.UTF-8 -e LC_ALL=C.UTF-8 -v ./target/ignite-essentials-developer-training-1.0-SNAPSHOT.jar:/opt/ignite/downloads/ignite-essentials-developer-training-1.0-SNAPSHOT.jar --rm --network ignite3_default -it apacheignite/ignite:3.0.0 cli
   connect http://node1:10300
   cluster unit deploy --version 1.0.0 --path=/opt/ignite/downloads/ignite-essentials-developer-training-1.0-SNAPSHOT.jar essentialsCompute
    ```
3. Execute the `ComputeApp` program from your IDE or the deployable jar:
    ```bash
    java -jar target/ignite-essentials-developer-training-1.0-SNAPSHOT.jar 
    ```
3. Execute the `ComputeApp` program from your IDE. 
