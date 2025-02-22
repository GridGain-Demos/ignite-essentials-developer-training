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

package org.gridgain.training;

import org.apache.ignite.client.IgniteClient;
import org.gridgain.training.model.Artist;
import org.apache.ignite.Ignite;


/**
 * The application reads Artists from the cluster using key-value requests. Complete the TODO
 * item to see how Ignite distributes records across partitions and nodes.
 */
public class KeyValueApp {

    public static void main(String[] args) throws Exception {
        try (var ignite = IgniteClient.builder()
                .addresses("127.0.0.1:10800")
                .build()
        ) {
            getArtistsDistribution(ignite);
        }
    }

    private static void getArtistsDistribution(Ignite ignite) {

        var artistCache = ignite.tables().table("Artist").keyValueView(Integer.class, Artist.class);

        for (int artistKey = 1; artistKey < 100; artistKey++) {
            Artist artist = artistCache.get(null, artistKey);

            /**
             * TODO #1: print partitions and nodes every artistKey is mapped to.
             * Hint, use ignite.affinity(...).partition(...) and .mapPartitionToNode(...) methods.
             */

            System.out.println(artist);
        }

    }
}
