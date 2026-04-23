/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package training;

import training.model.Artist;
import org.apache.ignite.Ignition;
import org.apache.ignite.client.ClientCache;
import org.apache.ignite.client.IgniteClient;
import org.apache.ignite.configuration.ClientConfiguration;


/**
 * Reads Artists from the cluster using key-value requests via the thin
 * client protocol. Connects to the address in the IGNITE_ADDRESS env var
 * (localhost:10800 by default; node1:10800 when running in the docker sidecar).
 */
public class KeyValueApp {

    public static void main(String[] args) {
        String addr = System.getenv().getOrDefault("IGNITE_ADDRESS", "localhost:10800");
        ClientConfiguration cfg = new ClientConfiguration().setAddresses(addr);

        try (IgniteClient client = Ignition.startClient(cfg)) {
            System.out.println(">>> Connected to " + addr);

            getArtistsDistribution(client);
        }
    }

    private static void getArtistsDistribution(IgniteClient client) {
        ClientCache<Integer, Artist> artistCache = client.cache("Artist");

        for (int artistKey = 1; artistKey < 100; artistKey++) {
            Artist artist = artistCache.get(artistKey);

            /*
             * TODO #1: for each artistKey, determine which partition it
             * belongs to and which node currently holds that partition.
             *
             * No Affinity API on the thin client — use SYS views instead:
             *
             *   SELECT * FROM SYS.NODES;
             *   SELECT CACHE_NAME, CACHE_GROUP_NAME FROM SYS.CACHES
             *     WHERE CACHE_NAME = 'Artist';
             *   SELECT * FROM SYS.PARTITION_STATES
             *     WHERE CACHE_GROUP_NAME = 'Artist' AND STATE = 'OWNING'
             *     ORDER BY PARTITION;
             *
             * (Hint: client.query(new SqlFieldsQuery(...)).getAll())
             */

            System.out.println(artist);
        }
    }
}
