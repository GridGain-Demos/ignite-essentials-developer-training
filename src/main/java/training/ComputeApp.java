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
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.TreeSet;
import javax.cache.Cache;
import training.model.TopCustomer;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.Ignition;
import org.apache.ignite.binary.BinaryObject;
import org.apache.ignite.cache.CachePeekMode;
import org.apache.ignite.cache.query.QueryCursor;
import org.apache.ignite.cache.query.ScanQuery;
import org.apache.ignite.cluster.ClusterGroup;
import org.apache.ignite.lang.IgniteCallable;
import org.apache.ignite.resources.IgniteInstanceResource;

/**
 * The application uses Apache Ignite compute capabilities for a calculation of the top-5 paying customers. The compute
 * task executes on every cluster node, iterates through local records and responds to the application that merges partial
 * results.
 *
 * Update the implementation of the compute task to return top-10 paying customers.
 */
public class ComputeApp {

    public static void main(String[] args) {
        Ignition.setClientMode(true);

        Ignite ignite = Ignition.start("ignite-config.xml");

        calculateTopPayingCustomers(ignite);

        ignite.close();
    }

    private static void calculateTopPayingCustomers(Ignite ignite) {
        ClusterGroup serversGroup = ignite.cluster().forServers();

        int customersCount = 5;

        Collection<TreeSet<TopCustomer>> results = ignite.compute(
            serversGroup).broadcast(new TopPayingCustomersTask(customersCount));

        printTopPayingCustomers(results, customersCount);
    }

    /**
     * Task that is executed on every cluster node and calculates top-5 local paying customers stored on a node.
     */
    private static class TopPayingCustomersTask implements IgniteCallable<TreeSet<TopCustomer>> {
        @IgniteInstanceResource
        private Ignite localNode;

        private HashMap<Integer, BigDecimal> customerPurchases = new HashMap<>();

        int customersCount;

        public TopPayingCustomersTask(int customersCount) {
            this.customersCount = customersCount;
        }

        public TreeSet<TopCustomer> call() throws Exception {
            IgniteCache<BinaryObject, BinaryObject> invoiceLineCache = localNode.cache(
                "InvoiceLine").withKeepBinary();

            ScanQuery scanQuery = new ScanQuery();
            scanQuery.setLocal(true);

            QueryCursor<Cache.Entry<BinaryObject, BinaryObject>> cursor = invoiceLineCache.query(scanQuery);


            cursor.forEach(entry -> {
                BinaryObject val = entry.getValue();
                BinaryObject key = entry.getKey();

                BigDecimal unitPrice = val.field("unitPrice");
                int quantity = val.field("quantity");

                processPurchase(key.field("customerId"), unitPrice.multiply(new BigDecimal(quantity)));
            });

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
            IgniteCache<Integer, BinaryObject> customersCache = localNode.cache("Customer").withKeepBinary();

            TreeSet<TopCustomer> sortedPurchases = new TreeSet<>();

            TreeSet<TopCustomer> top = new TreeSet<>();

            customerPurchases.entrySet().forEach(entry -> {
                TopCustomer topCustomer = new TopCustomer(entry.getKey(), entry.getValue());

                sortedPurchases.add(topCustomer);
            });

            Iterator<TopCustomer> iterator = sortedPurchases.descendingSet().iterator();

            int counter = 0;

            System.out.println(">>> Top " + customersCount + " Paying Listeners: ");

            while (iterator.hasNext() && counter++ < customersCount) {
                TopCustomer customer = iterator.next();

                // It's safe to use localPeek because invoices are co-located with customer data.
                BinaryObject customerRecord = customersCache.localPeek(customer.getCustomerId(), CachePeekMode.PRIMARY);

                customer.setFullName(customerRecord.field("firstName") +
                    " " + customerRecord.field("lastName"));
                customer.setCity(customerRecord.field("city"));
                customer.setCountry(customerRecord.field("country"));

                top.add(customer);

                System.out.println(customer);
            }

            return top;
        }
    }

    private static void printTopPayingCustomers(Collection<TreeSet<TopCustomer>> results, int customersCount) {
        System.out.println(">>> Top " + customersCount + " Paying Listeners Across All Cluster Nodes");

        Iterator<TreeSet<TopCustomer>> iterator = results.iterator();

        TreeSet<TopCustomer> firstSet = iterator.next();

        while (iterator.hasNext())
            firstSet.addAll(iterator.next());

        Iterator<TopCustomer> customerIterator = firstSet.descendingSet().iterator();

        int counter = 0;

        while (customerIterator.hasNext() && counter++ < customersCount)
            System.out.println(customerIterator.next());
    }
}
