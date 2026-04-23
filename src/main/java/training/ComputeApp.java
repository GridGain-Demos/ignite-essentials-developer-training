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

import java.util.Iterator;
import java.util.TreeSet;
import training.model.TopCustomer;
import org.apache.ignite.Ignition;
import org.apache.ignite.client.IgniteClient;
import org.apache.ignite.configuration.ClientConfiguration;

/**
 * Thin-client driver that triggers a server-deployed compute task to
 * calculate the top-N paying customers. The real work happens in
 * training.compute.TopPayingCustomersTask.
 *
 * Update the task implementation to return the top-10 paying customers.
 */
public class ComputeApp {

    public static void main(String[] args) throws InterruptedException {
        String addr = System.getenv().getOrDefault("IGNITE_ADDRESS", "localhost:10800");
        ClientConfiguration cfg = new ClientConfiguration().setAddresses(addr);

        int customersCount = 5;

        try (IgniteClient client = Ignition.startClient(cfg)) {
            System.out.println(">>> Connected to " + addr);

            TreeSet<TopCustomer> top = client.compute().execute(
                "training.compute.TopPayingCustomersTask", customersCount);

            printTopPayingCustomers(top, customersCount);
        }
    }

    private static void printTopPayingCustomers(TreeSet<TopCustomer> top, int customersCount) {
        System.out.println(">>> Top " + customersCount + " Paying Listeners Across All Cluster Nodes");

        Iterator<TopCustomer> iterator = top.descendingSet().iterator();
        int counter = 0;

        while (iterator.hasNext() && counter++ < customersCount) {
            System.out.println(iterator.next());
        }
    }
}
