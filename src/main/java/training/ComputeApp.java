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
import java.util.stream.Collectors;

import org.apache.ignite.client.IgniteClient;
import org.apache.ignite.compute.ComputeJob;
import org.apache.ignite.compute.JobDescriptor;
import org.apache.ignite.compute.JobExecutionContext;
import org.apache.ignite.compute.TaskDescriptor;
import org.apache.ignite.compute.task.MapReduceJob;
import org.apache.ignite.compute.task.MapReduceTask;
import org.apache.ignite.compute.task.TaskExecutionContext;
import org.apache.ignite.deployment.DeploymentUnit;
import org.apache.ignite.marshalling.ByteArrayMarshaller;
import org.apache.ignite.marshalling.Marshaller;
import org.apache.ignite.table.Tuple;
import org.apache.ignite.Ignite;
import training.model.CustomerPrice;
import training.model.TopCustomer;

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

    private static final DeploymentUnit deploymentUnit = new DeploymentUnit("essentialsCompute", "1.0.0");

    private static void calculateTopPayingCustomers(Ignite ignite) {
        int customersCount = 5;

        // cluster unit deploy -up apps.jar -uv 1.0 essentials-compute
        var job = TaskDescriptor.builder(TopPayingCustomersTask.class)
                .units(deploymentUnit)
                .reduceJobResultMarshaller(ByteArrayMarshaller.create())
                .build();
        var results = ignite.compute().executeMapReduce(job, customersCount);
        printTopPayingCustomers(List.of(results), customersCount);
    }

    /**
     * Task that is executed on every cluster node and calculates top-5 local paying customers stored on a node.
     */
    private static class TopPayingCustomersTask implements MapReduceTask<Integer, Tuple, CustomerPrice[], TopCustomer[]> {

        public TopPayingCustomersTask() {
        }

        private Integer customerCount;

        @Override
        public Marshaller<TopCustomer[], byte[]> reduceJobResultMarshaller() {
            return ByteArrayMarshaller.create();
        }

        @Override
        public CompletableFuture<List<MapReduceJob<Tuple, CustomerPrice[]>>> splitAsync(TaskExecutionContext taskExecutionContext, Integer customersCount) {
            this.customerCount = customersCount;
            return taskExecutionContext.ignite().tables().table("InvoiceLine").partitionManager().primaryReplicasAsync()
                    .thenApply(x -> x.entrySet().stream()
                            .map(jobParameter ->
                                    MapReduceJob.<Tuple,CustomerPrice[]>builder()
                                            .nodes(List.of(jobParameter.getValue()))
                                            .args(Tuple.create()
                                                    // FIXME: don't use hashCode once API is finalised
                                                    .set("partition",jobParameter.getKey().hashCode())
                                                    .set("count", customersCount))
                                            .jobDescriptor(
                                                    JobDescriptor.builder(TopPayingCustomersJob.class)
                                                            .units(deploymentUnit)
                                                            .resultMarshaller(ByteArrayMarshaller.create())
                                                            .build()
                                            )
                                            .build())
                            .collect(Collectors.toList())
                    );
        }

        private static class TopPayingCustomersJob implements ComputeJob<Tuple, CustomerPrice[]> {
            // Verify results with: select customerid, sum(quantity * unitprice) as price from invoiceline group by customerid order by price desc limit 5
            private static String sql = "select customerid, quantity * unitprice as price from invoiceline where \"__part\" = ?";

            private int customerCount = 5;

            @Override
            public Marshaller<CustomerPrice[], byte[]> resultMarshaller() {
                return ByteArrayMarshaller.create();
            }

            @Override
            public CompletableFuture<CustomerPrice[]> executeAsync(JobExecutionContext jobExecutionContext, Tuple parameters) {
                final HashMap<Integer, BigDecimal> customerPurchases = new HashMap<>();

                customerCount = parameters.intValue("count");

                try (var results = jobExecutionContext.ignite().sql().execute(null, sql, parameters.intValue("partition"))) {
                    while (results.hasNext()) {
                        var row = results.next();
                        customerPurchases.merge(row.intValue("customerId"), row.value("price"), BigDecimal::add);
                    }
                }

                var r = new ArrayList<>(customerPurchases.entrySet());
                r.sort(Map.Entry.comparingByValue());
                Collections.reverse(r);

                var results = new ArrayList<CustomerPrice>();
                for (var p = 0; p < r.size() && p < customerCount; p++) {
                    results.add(new CustomerPrice(r.get(p).getKey(), r.get(p).getValue()));
                }
                return CompletableFuture.completedFuture(results.toArray(new CustomerPrice[customerCount]));
            }
        }

        @Override
        public CompletableFuture<TopCustomer[]> reduceAsync(TaskExecutionContext taskExecutionContext, Map<UUID, CustomerPrice[]> map) {
            var orderedResults = new ArrayList<CustomerPrice>();
            for (var result : map.values()) {
                orderedResults.addAll(Arrays.stream(result).collect(Collectors.toCollection(LinkedList::new)));
            }
            orderedResults.sort(Comparator.comparing(CustomerPrice::getPrice));
            Collections.reverse(orderedResults);

            var customersCache = taskExecutionContext.ignite().tables().table("Customer").recordView();
            var results = new ArrayList<TopCustomer>();
            for (var p = 0; p < orderedResults.size() && p < customerCount; p++) {
                var newRecord = Tuple.create();

                var key = orderedResults.get(p).getCustomerId();

                var customerRecord = customersCache.get(null, Tuple.create().set("customerId", key));

                if (customerRecord != null) {
                    newRecord.set("firstName",customerRecord.stringValue("firstName"))
                            .set("lastName", customerRecord.stringValue("lastName"))
                            .set("city", customerRecord.stringValue("city"))
                            .set("country", customerRecord.stringValue("country"));
                }
                else {
                    newRecord.set("firstName", "unknown")
                            .set("lastName", "unknown")
                            .set("city", "unknown")
                            .set("country", "unknown");
                }
                newRecord.set("price", orderedResults.get(p).getPrice());

                var val = new TopCustomer(key, orderedResults.get(p).getPrice());
                val.setFullName(customerRecord.stringValue("firstName") + " " + customerRecord.stringValue("lastName"));
                val.setCity(customerRecord.stringValue("city"));
                val.setCountry(customerRecord.stringValue("country"));
                results.add(val);
            }
            return CompletableFuture.completedFuture(results.toArray(new TopCustomer[customerCount]));
        }
    }

    private static void printTopPayingCustomers(List<TopCustomer> results, int customersCount) {
        System.out.println(">>> Top " + customersCount + " Paying Listeners Across All Cluster Nodes");

        for (var i : results) {
            System.out.println(i);
        }
    }
}
