# [Foundation Course] GridGain Nebula Essentials: Key Design Principles for Building Data-Intensive Applications

This project is designed for a free instructor-led training on the Ignite essential capabilities and architecture internals.
Check [the complete schedule](https://www.gridgain.com/products/services/training/apache-ignite-workshop-Key-design-principles-for-building-data-intensive-applications) and join one of our upcoming training sessions.

## Setting Up Environment

* Java Developer Kit, version 8 or later
* Apache Maven 3.0 or later
* Your favorite IDE, such as IntelliJ IDEA, or Eclipse, or a simple text editor
* GridGain Nebula account

## Clone The Project

1. Clone the training project with Git or download it as an archive:
    ```bash
    git clone https://github.com/GridGain-Demos/ignite-essentials-developer-training.git
    ```

2. (optionally), open the project in your favourite IDE such as IntelliJ or Eclipse, or just use a simple text editor
and command-line instructions prepared for all the samples.    

## Starting GridGain Cluster

Start a two-node Ignite cluster in GridGain Nebula.

## Creating Media Store Schema and Loading Data

Now you need to create a Media Store schema and load the cluster with sample data. Use SQLLine tool to achieve that:

1. Open a terminal window and navigate to the root directory of this project.

2. Build the core executable:
   ```bash
   mvn clean package -P core
   ```
   
4. Assuming that you've already assembled the core executable JAR with all the dependencies, launch a SQLLine process:
    ```bash
    java -cp libs/core.jar sqlline.SqlLine
    ```
   
5. Connect to the cluster:
    ```bash
    !connect jdbc:ignite:thin://SERVER:10800?sslMode=require USERNAME PASSWORD
    ```

6. Load the Media Store database:
    ```bash
    !run config/media_store.sql
    ```

Keep the connection open as you'll use it for following exercises.

## Data Partitioning - Checking Data Distribution

With the Media Store database loaded, you can check how Ignite distributed the records within the cluster:

1. Open the [Caches Screen](https://www.gridgain.com/docs/control-center/latest/caches#partition-distribution) of 
GridGain Nebula.

2. While on that screen, follow the instructor to learn some insights.

Optional, scale out the cluster by the third node. You'll see that some partitions were rebalanced to the new node.

## Affinity Co-location - Optimizing Complex SQL Queries With JOINs

Ignite supports SQL for data processing including distributed joins, grouping and sorting. In this section, you're 
going to run basic SQL operations as well as more advanced ones.

### Querying Single Table

1. Go to the [SQL Notebooks Screen](https://www.gridgain.com/docs/control-center/latest/querying) of GridGain Nebula.
 
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

## Running Co-located Compute Tasks

Run `training.ComputeApp` that uses Apache Ignite compute capabilities for a calculation of top-5 paying customers.
The compute task executes on every cluster node, iterates through local records and responds to the application that 
merges partial results.

1. Update `src/main/resources/ignite-config.xml` with the username, password and hostname of your cluster

2. Build an executable JAR with the applications' classes (or just start the app with IntelliJ IDEA or Eclipse):
    ```bash
    mvn clean package -P apps
    ```
3. Deploy the resulting JAR file to Nebula (you'll need to upload it somewhere public on the internet, possibly an S3 bucket and use the "Deployment" tab in Nebula to load it onto the server nodes.)

4. Run the app in the terminal:
    ```bash
    java -cp libs/apps.jar:libs/core.jar -DIGNITE_EVENT_DRIVEN_SERVICE_PROCESSOR_ENABLED=true training.ComputeApp
    ```
   (If it hangs, you probably missed or mis-typed the IGNITE_EVENT_DRIVEN_SERVICE_PROCESSOR_ENABLED bit.)

Modify the computation logic: 

1. Update the logic to return top-10 paying customers.

2. Re-build an executable JAR with the applications' classes (or just start the app with IntelliJ IDEA or Eclipse):
    ```bash
    mvn clean package -P apps
    ```
3. Redeploy the JAR file
4. Run the app again:
    ```bash
    java -cp libs/apps.jar:libs/core.jar -DIGNITE_EVENT_DRIVEN_SERVICE_PROCESSOR_ENABLED=true training.ComputeApp
    ```
