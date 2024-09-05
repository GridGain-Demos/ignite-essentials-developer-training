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

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import org.apache.ignite.client.IgniteClient;
import org.apache.ignite.compute.ComputeJob;
import org.apache.ignite.compute.JobDescriptor;
import org.apache.ignite.compute.JobExecutionContext;
import org.apache.ignite.deployment.DeploymentUnit;
import org.apache.ignite.lang.Cursor;
import org.apache.ignite.network.ClusterNode;
import org.apache.ignite.table.Tuple;
import training.model.TopCustomer;
import org.apache.ignite.Ignite;

/**
 * The application uses Apache Ignite compute capabilities for a calculation of the top-5 paying customers. The compute
 * task executes on every cluster node, iterates through local records and responds to the application that merges partial
 * results.
 *
 * Update the implementation of the compute task to return top-10 paying customers.
 */
public class ComputeApp {

    public static void main(String[] args) throws Exception {
        try (var ignite = IgniteClient.builder()
                .addresses("127.0.0.1:10800")
                .build()
        ) {
            calculateTopPayingCustomers(ignite);

            // wait for metrics to flush to Control Center
            Thread.sleep(5000L);
        }
    }

    private static void calculateTopPayingCustomers(Ignite ignite) {
        int customersCount = 5;

        // cluster unit deploy -up apps.jar -uv 1.0 essentials-compute
        var nodes = new HashSet<>(ignite.clusterNodes());
        var unit = new DeploymentUnit("essentialsCompute", "1.0.0");
        var job = JobDescriptor.<Object, TreeSet<TopCustomer>>builder("TopPayingCustomersTask")
                .units(unit)
                .build();
        var results = ignite.compute().<Object, TreeSet<TopCustomer>>executeBroadcast(nodes, job, customersCount);

        printTopPayingCustomers(results, customersCount);
    }

    /**
     * Task that is executed on every cluster node and calculates top-5 local paying customers stored on a node.
     */
    private static class TopPayingCustomersTask implements ComputeJob<Object, TreeSet<TopCustomer>> {
        private Ignite ignite;
        private final HashMap<Integer, BigDecimal> customerPurchases = new HashMap<>();

        int customersCount;

        public TopPayingCustomersTask(int customersCount) {
            this.customersCount = customersCount;
        }

        @Override
        public CompletableFuture<TreeSet<TopCustomer>> executeAsync(JobExecutionContext context, Object args) {
            ignite = context.ignite();
            var invoiceLineCache = ignite.tables().table("InvoiceLine").recordView();

            var q = ignite.sql().createStatement("select customerid, quantity from invoiceline");
                try (Cursor<Tuple> it = invoiceLineCache.query(null, null)) {
                    while (it.hasNext()) {
                        var val = it.next();

                        BigDecimal unitPrice = BigDecimal.valueOf(val.doubleValue("unitPrice")); // FIXME: what is  DECIMAL(10,2)?
                        int quantity = val.intValue("quantity");

                        processPurchase(val.intValue("customerId"), unitPrice.multiply(new BigDecimal(quantity)));
                    }
                }

                return calculateTopCustomers();
        }

        private void processPurchase(int itemId, BigDecimal price) {
            BigDecimal totalPrice = customerPurchases.get(itemId);

            if (totalPrice == null)
                customerPurchases.put(itemId, price);
            else
                customerPurchases.put(itemId, totalPrice.add(price));
        }

        private TreeSet<TopCustomer> calculateTopCustomers() {
            var customersCache = ignite.tables().table("Customer").recordView();

            TreeSet<TopCustomer> sortedPurchases = new TreeSet<>();

            TreeSet<TopCustomer> top = new TreeSet<>();

            customerPurchases.forEach((key, value) -> {
                TopCustomer topCustomer = new TopCustomer(key, value);

                sortedPurchases.add(topCustomer);
            });

            Iterator<TopCustomer> iterator = sortedPurchases.descendingSet().iterator();

            int counter = 0;

            System.out.println(">>> Top " + customersCount + " Paying Listeners: ");

            while (iterator.hasNext() && counter++ < customersCount) {
                TopCustomer customer = iterator.next();

                // It's safe to use localPeek because invoices are co-located with customer data.
                var customerRecord = customersCache.get(null, Tuple.create().set("customerId", customer.getCustomerId()));

                customer.setFullName(customerRecord.stringValue("firstName") +
                        " " + customerRecord.stringValue("lastName"));
                customer.setCity(customerRecord.stringValue("city"));
                customer.setCountry(customerRecord.stringValue("country"));

                top.add(customer);

                System.out.println(customer);
            }

            return top;
        }
    }

    private static void printTopPayingCustomers(Map<ClusterNode, TreeSet<TopCustomer>> results, int customersCount) {
        System.out.println(">>> Top " + customersCount + " Paying Listeners Across All Cluster Nodes");

        TreeSet<TopCustomer> firstSet = new TreeSet<>();
        for (var cs : results.values()) {
            firstSet.addAll(cs);
        }

        Iterator<TopCustomer> customerIterator = firstSet.descendingSet().iterator();

        int counter = 0;

        while (customerIterator.hasNext() && counter++ < customersCount) {
            System.out.println(customerIterator.next());
        }
    }
}
