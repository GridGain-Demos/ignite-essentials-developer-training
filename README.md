# [Developer Training] Apache Ignite Essentials - Key Design Principles for Building Data-Intensive Applications

This project is designed for a free instructor-led training on the Ignite essential capabilities and architecture internals.
Check the schedule (TBD) and join one of our upcoming training sessions.

## Setting Up Environment

* Java Developer Kit, version 8 or later
* Apache Maven 3.0 or later
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

2. Use Maven to create a core executable JAR with all the dependencies (Note, build the JAR even if you plan to
start the sample code with IntelliJ IDEA or Eclipse. The JAR is used by other tools throughout the class):
    ```bash
    mvn clean package -P core
    ```
3. Start the first cluster node (or just start the app with IntelliJ IDEA or Eclipse):
    ```bash
    java -cp libs/core.jar training.ServerStartup
    ```

4. Open another terminal window and start the second node:
    ```bash
    java -cp libs/core.jar training.ServerStartup
    ```

Both nodes auto-discover each other and you'll have a two-nodes cluster ready for exercises.
 
## Connecting to GridGain Control Center

You use [GridGain Control Center](https://control.gridgain.com) throughout the course to see how Ignite distributes 
records, to execute and optimize SQL queries, and to monitor the state of the cluster.

1. Go to [https://control.gridgain.com](https://control.gridgain.com).

2. Create an account to sign in into Control Center.

3. Just in case, generate a new token for the cluster (the default token expires in 5 minutes after the cluster startup time):

    * Open a terminal window and navigate to the root directory of this project.
    
    * Generate the token (the `ManagementCommandHandler` is the tool used by the 
    [management.sh|bat script](https://www.gridgain.com/docs/control-center/latest/clusters#generating-a-token) of the 
    Ignite Agent distribution package, you just call it directly with this training to skip extra downloads): 
        ```bash
        java -cp libs/core.jar org.gridgain.control.agent.commandline.ManagementCommandHandler --token
        ```              

4. [Register the cluster](https://www.gridgain.com/docs/control-center/latest/clusters#adding-clusters) with Control Center 
using the token.

## Creating Media Store Schema and Loading Data

Now you need to create a Media Store schema and load the cluster with sample data. Use SQLLine tool to achieve that:

1. Open a terminal window and navigate to the root directory of this project.
   
2. Assuming that you've already assembled the core executable JAR with all the dependencies, launch a SQLLine process:
    ```bash
    java -cp libs/core.jar sqlline.SqlLine
    ```
   
3. Connect to the cluster:
    ```bash
    !connect jdbc:ignite:thin://127.0.0.1/ ignite ignite
    ```

4. Load the Media Store database:
    ```bash
    !run config/media_store.sql
    ```

Keep the connection open as you'll use it for following exercises.

## Data Partitioning - Checking Data Distribution

With the Media Store database loaded, you can check how Ignite distributed the records within the cluster:

1. Open the [Caches Screen](https://www.gridgain.com/docs/control-center/latest/caches#partition-distribution) of 
Control Center.

2. While on that screen, follow the instructor to learn some insights.

Optional, scale out the cluster by the third node. You'll see that some partitions were rebalanced to the new node.

## Affinity Co-location - Optimizing Complex SQL Queries With JOINs

Ignite supports SQL for data processing including distributed joins, grouping and sorting. In this section, you're 
going to run basic SQL operations as well as more advanced ones.

### Querying Single Table

1. Go to the [SQL Notebooks Screen](https://www.gridgain.com/docs/control-center/latest/querying) of Control Center.
 
2. Run the following query to find top-20 longest tracks:

    ```
    SELECT trackid, name, MAX(milliseconds / (1000 * 60)) as duration FROM track
    WHERE genreId < 17
    GROUP BY trackid, name ORDER BY duration DESC LIMIT 20;
    ```

### Joining Two Non-Colocated Tables

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

    Once you run the query, you'll see that the `artist` column is blank for some records. That's because `Track` and 
    `Artist` tables are not co-located and the nodes don't have all data available locally during the join phase.
    
2. Allow the non-colocated joins by enabling the `Allow non-colocated joins` checkbox on the Control Center screen.

3. Run the query again to see a complete and correct result.

### Joining Two Co-located Tables

The non-colocated joins used above come with a performance penalty, i.e., if the nodes are shuffling large data sets
during the join phase, your performance will be impacted. However, it's possible to co-locate `Track` and `Artist` tables, and
avoid the usage of the non-colocated joins:

1. Search for the `CREATE TABLE Track` command in the `media_store.sql` file.

2. Replace `PRIMARY KEY (TrackId)` with `PRIMARY KEY (TrackId, ArtistId)`.

3. Co-locate Tracks with Artist by adding `affinityKey=ArtistId` to the parameters list of the `WITH ...` operator.

4. As long as you changed the primary and affinity keys in runtime, you need to update the Ignite metadata before recreating the table:

    * Open a terminal window and navigate to the root directory of this project.
    
    * Enable the experimental features:
        ```bash
        export IGNITE_ENABLE_EXPERIMENTAL_COMMAND=true
        ```
    * Clean the metadata for the `Track` object:
        ```bash
        java -cp libs/core.jar org.apache.ignite.internal.commandline.CommandHandler --enable-experimental=true --meta remove --typeName training.model.Track
        ```
    * Clean the metadata for the `TrackKey` object:
        ```bash
        java -cp libs/core.jar org.apache.ignite.internal.commandline.CommandHandler --enable-experimental=true --meta remove --typeName training.model.TrackKey
        ```          
5. Recreate the table using the SQLLine tool:
    * Launch SQLine from a terminal window:
        ```bash
        java -cp libs/core.jar sqlline.SqlLine
        ```
       
    * Connect to the cluster:
        ```bash
        !connect jdbc:ignite:thin://127.0.0.1/ ignite ignite
        ```
    
    * Load the Media Store database:
        ```bash
        !run config/media_store.sql
        ```

6. In Control Center, run that query once again and you'll see that all the `artist` columns are filled in because now 
all the Tracks are stored together with their Artists on the same cluster node.

## Running Co-located Compute Tasks

Run `training.ComputeApp` that uses Apache Ignite compute capabilities for a calculation of top-5 paying customers.
The compute task executes on every cluster node, iterates through local records and responds to the application that 
merges partial results.

1. Run the app in a terminal window to see how it works (or just start the app with IntelliJ IDEA or Eclipse):
    ```bash
    java -cp libs/apps.jar training.ComputeApp
    ```

2. Check the logs of the `ServerStartup` processes (your Ignite server nodes) to see that the calculation
was executed across the cluster.

Modify the computation logic: 

1. Update the logic to return top-10 paying customers.

2. Build an executable JAR with the applications' classes (or just start the app with IntelliJ IDEA or Eclipse):
    ```bash
    mvn clean package -P apps
    ```
3. Run the app again:
    ```bash
    java -cp libs/apps.jar training.ComputeApp
    ```
