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
import java.util.stream.Collectors;
import javax.cache.Cache;

import org.apache.ignite.IgniteException;
import org.apache.ignite.cluster.ClusterNode;
import org.apache.ignite.compute.*;
import org.apache.ignite.lang.IgniteBiTuple;
import org.apache.ignite.resources.TaskSessionResource;
import training.model.TopCustomer;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.Ignition;
import org.apache.ignite.binary.BinaryObject;
import org.apache.ignite.cache.CachePeekMode;
import org.apache.ignite.cache.query.QueryCursor;
import org.apache.ignite.cache.query.ScanQuery;
import org.apache.ignite.resources.IgniteInstanceResource;

/**
 * The application uses Apache Ignite compute capabilities for a calculation of the top-5 paying customers. The compute
 * task executes on every cluster node, iterates through local records and responds to the application that merges partial
 * results.
 * <p>
 * Update the implementation of the compute task to return top-10 paying customers.
 */
public class ComputeApp {

    private static final int customersCount = 5;

    public static void main(String[] args) {
        Ignition.setClientMode(true);

        try (Ignite ignite = Ignition.start("ignite-config.xml")) {
            TreeSet<TopCustomer> results = ignite.compute().execute(new TopPayingCustomersTask(), customersCount);
            System.out.println(">>> Top " + customersCount + " Paying Listeners Across All Cluster Nodes");
            System.out.println(results);

            // make sure events have made it to Control Center before quitting
            Thread.sleep(5000);
        }
        catch (InterruptedException e) {

        }
    }

    @ComputeTaskSessionFullSupport
    private static class TopPayingCustomersTask extends ComputeTaskAdapter<Integer, TreeSet<TopCustomer>> {

        @TaskSessionResource
        ComputeTaskSession session;

        private int customersCount;

        @Override
        public Map<? extends ComputeJob, ClusterNode> map(List<ClusterNode> subgrid, Integer arg) throws IgniteException {
            session.setAttribute("client", "gridgain");
            session.setAttribute("training", "ignite-essentials");
            session.setAttribute("deployment", "nebula");

            customersCount = arg;
            return subgrid.stream()
                    .map(x -> new IgniteBiTuple<ComputeJob, ClusterNode>(new TopCustomersNode(arg), x))
                    .collect(Collectors.toMap(IgniteBiTuple::get1, IgniteBiTuple::get2));
        }

        @Override
        public TreeSet<TopCustomer> reduce(List<ComputeJobResult> results) throws IgniteException {
            System.out.println(">>> Top " + customersCount + " Paying Listeners Across All Cluster Nodes");

            Iterator<ComputeJobResult> iterator = results.iterator();

            TreeSet<TopCustomer> firstSet = iterator.next().getData();

            while (iterator.hasNext())
                firstSet.addAll(iterator.next().getData());

            Iterator<TopCustomer> customerIterator = firstSet.descendingSet().iterator();

            int counter = 0;

            while (customerIterator.hasNext() && counter++ < customersCount)
                System.out.println(customerIterator.next());

            TreeSet<TopCustomer> mergedResults = results.stream()
                    .map(x -> (TreeSet<TopCustomer>)x.getData())
                    .reduce((x,y) -> { x.addAll(y); return x; })
                    .get();

            return mergedResults;
        }

        private static class TopCustomersNode implements ComputeJob {
            @IgniteInstanceResource
            Ignite localNode;

            private final HashMap<Integer, BigDecimal> customerPurchases = new HashMap<>();

            int customersCount;

            public TopCustomersNode(int customersCount) {
                this.customersCount = customersCount;
            }

            @Override
            public void cancel() {

            }

            @Override
            public TreeSet<TopCustomer> execute() throws IgniteException {
                IgniteCache<BinaryObject, BinaryObject> invoiceLineCache = localNode.cache(
                        "InvoiceLine").withKeepBinary();

                ScanQuery<BinaryObject,BinaryObject> scanQuery = new ScanQuery<>();
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
                customerPurchases.merge(itemId, price, BigDecimal::add);
            }

            private TreeSet<TopCustomer> calculateTopCustomers() {
                IgniteCache<Integer, BinaryObject> customersCache = localNode.cache("Customer").withKeepBinary();

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
    }
}
