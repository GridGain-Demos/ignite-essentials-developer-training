# [Foundation Course] Apache Ignite Essentials: Key Design Principles for Building Data-Intensive Applications

This project is designed for a free instructor-led training on the Ignite essential capabilities and architecture internals.
Check [the complete schedule](https://www.gridgain.com/products/services/training/apache-ignite-workshop-Key-design-principles-for-building-data-intensive-applications) and join one of our upcoming training sessions.

## Setting Up Environment

* GridGain license file. Instructions will be emailed when you register, but, in short, grab your key [here](https://www.gridgain.com/tryfree)
* Docker and Docker Compose installed on your system
* Basic familiarity with command-line operations
* Java 11, 17 or 21 (for connecting to the cluster)
* Maven
* (An IDE such as IntelliJ will make things a lot easier but is not strictly necessary)

## Clone The Project

1. Clone the training project with Git or download it as an archive:

    ```bash
    git clone https://github.com/GridGain-Demos/ignite-essentials-developer-training.git
    ```

2. (optionally), open the project in your favourite IDE such as IntelliJ or Eclipse, or just use a simple text editor
and command-line instructions prepared for all the samples.

## Sign up for GridGain's Nebula service

We'll use the Control Center component to execute SQL queries and view cluster internals.

1. Open portal.gridgain.com in your browser
2. Click the "Sign up" button
3. Enter your details

## Starting Ignite Cluster

Start a two-node Ignite cluster:

1. Open a terminal window and navigate to the root directory of this project.

2. Open `src/main/resources/controlcenter.conf` in your IDE or text editor

3. Update the `connector.username` and `connector.password` values to the values you used to create your Nebula account.

4. Start your nodes using Docker Compose:

    ```bash
   docker compose up -d
   ```

5. Switch back to your browser and select `Attach GridGain`
6. In the "Connector" dropdown, select `Ignite Essentials`
7. The `URL of the REST API` is `http://node1:10300`
8. Click `Continue`
9. Click `Attach`
10. Initialize the cluster by clicking the `Initialize` button at the top-right of the screen. Drop in your license file as suggested

## Creating Media Store Schema and Loading Data

Now you need to create a Media Store schema and load the cluster with sample data. Use SQLLine tool to achieve that:

1. Open a terminal window and navigate to the root directory of this project.
2. Load the media store database:

	a. Start the Command Line Interface (CLI)

    ```bash
   docker run -v ./config/media_store.sql:/opt/gridgain/downloads/media_store.sql --rm --network ignite3_default -it gridgain/gridgain9:9.1.8 cli
   ```

   b. Connect to the cluster.

   ```bash
   connect http://node1:10300
   ```

   c. Execute SQL command to load the sample data.

   ```bash
   sql --file=/opt/gridgain/downloads/media_store.sql
    ```

Keep the connection open as you'll use it for following exercises.

## Data Partitioning - Checking Data Distribution

With the Media Store database loaded, you can check how Ignite distributed the records within the cluster:

1. Switch to your browser and select the "Tables" tab
2. While on that screen, follow the instructor to learn some insights.

## Affinity Co-location - Optimizing Complex SQL Queries With JOINs

Ignite supports SQL for data processing including distributed joins, grouping and sorting. In this section, you're
going to run basic SQL operations as well as more advanced ones.

### Querying Single Table

1. In your browser, select the "Queries" tab

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
	Sort
		collation: [DURATION DESC]
		fetch: 20
		est: (rows=1)
	  ColocatedHashAggregate
		  fieldNames: [TRACKID, TRACK_NAME, GENRE, ARTIST, DURATION]
		  group: [TRACKID, TRACK_NAME, GENRE, ARTIST]
		  aggregation: [MAX($f4)]
		  est: (rows=1)
		Project
			fieldNames: [TRACKID, TRACK_NAME, GENRE, ARTIST, $f4]
			projection: [TRACKID, NAME, NAME$1, NAME$0, /(MILLISECONDS, *(1000, 60))]
			est: (rows=1)
		  HashJoin
			  predicate: =(GENREID, GENREID$0)
			  fieldNames: [TRACKID, NAME, ARTISTID, GENREID, MILLISECONDS, ARTISTID$0, NAME$0, GENREID$0, NAME$1]
			  type: inner
			  est: (rows=1)
			Exchange
				distribution: single
				est: (rows=1)
			  HashJoin
				  predicate: =(ARTISTID, ARTISTID$0)
				  fieldNames: [TRACKID, NAME, ARTISTID, GENREID, MILLISECONDS, ARTISTID$0, NAME$0]
				  type: left
				  est: (rows=1)
				TableScan
					table: PUBLIC.TRACK
					predicate: <(GENREID, 17)
					fieldNames: [TRACKID, NAME, ARTISTID, GENREID, MILLISECONDS]
					est: (rows=1)
				TableScan
					table: PUBLIC.ARTIST
					fieldNames: [ARTISTID, NAME]
					est: (rows=1)
			Exchange
				distribution: single
				est: (rows=1)
			  TableScan
				  table: PUBLIC.GENRE
				  fieldNames: [GENREID, NAME]
				  est: (rows=1)
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

	a. Start the CLI.

    ```bash
   docker run -v ./target/ignite-essentials-developer-training-1.0-SNAPSHOT.jar:/opt/gridgain/downloads/ignite-essentials-developer-training-1.0-SNAPSHOT.jar --rm --network ignite3_default -it gridgain/gridgain9:9.1.8 cli
   ```

	b. Connect to the cluster.

	```bash
   connect http://node1:10300
   ```

   c. Deploy the code to the cluster.

   ```bash
   cluster unit deploy --version 1.0.0 --path=/opt/gridgain/downloads/ignite-essentials-developer-training-1.0-SNAPSHOT.jar essentialsCompute
    ```

    It's also possible to use Control Center to deploy your code if you prefer. Study the "Deployment" tab to find out more.
3. Execute the `ComputeApp` program with the following command:

```shell
mvn exec:java
```

Or run directly from your preferred IDE.

4. Edit `ComputeApp` and change the number of customers to return from 5 to 10. There's no need to redeploy the compute job because only the caller needs to change.

## Tidying up

When you've finished with the exercises, you can shut down the cluster and clean up with the following command:

```shell
docker compose down
```
