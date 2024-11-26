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
import org.apache.ignite.table.Tuple;
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

    private static DeploymentUnit deploymentUnit = new DeploymentUnit("essentialsCompute", "1.0.0");

    private static void calculateTopPayingCustomers(Ignite ignite) {
        int customersCount = 5;

        // cluster unit deploy -up apps.jar -uv 1.0 essentials-compute
        var job = TaskDescriptor.builder(TopPayingCustomersTask.class)
                .units(deploymentUnit)
                .build();
        var results = ignite.compute().executeMapReduce(job, customersCount);

        printTopPayingCustomers(results, customersCount);
    }

    /**
     * Task that is executed on every cluster node and calculates top-5 local paying customers stored on a node.
     */
    private static class TopPayingCustomersTask implements MapReduceTask<Integer, Tuple, Tuple, Tuple> {

        public TopPayingCustomersTask() {
        }

        private Integer customerCount;

        @Override
        public CompletableFuture<List<MapReduceJob<Tuple, Tuple>>> splitAsync(TaskExecutionContext taskExecutionContext, Integer customersCount) {
            this.customerCount = customersCount;
            return taskExecutionContext.ignite().tables().table("InvoiceLine").partitionManager().primaryReplicasAsync()
                    .thenApply(x -> x.entrySet().stream()
                            .map(jobParameter ->
                                    MapReduceJob.<Tuple,Tuple>builder()
                                            .nodes(List.of(jobParameter.getValue()))
                                            .args(Tuple.create()
                                                    // FIXME: don't use hashCode once API is finalised
                                                    .set("partition",jobParameter.getKey().hashCode())
                                                    .set("count", customersCount))
                                            .jobDescriptor(
                                                    JobDescriptor.builder(TopPayingCustomersJob.class)
                                                            .units(deploymentUnit)
                                                            .build()
                                            )
                                            .build())
                            .collect(Collectors.toList())
                    );
        }

        private static class TopPayingCustomersJob implements ComputeJob<Tuple, Tuple> {
            private static String sql = "select customerid, quantity * unitprice as price from invoiceline where \"__part\" = ?";

            private int customerCount = 5;

            @Override
            public CompletableFuture<Tuple> executeAsync(JobExecutionContext jobExecutionContext, Tuple objects) {
                final HashMap<Integer, BigDecimal> customerPurchases = new HashMap<>();

                customerCount = objects.intValue("count");

                try (var results = jobExecutionContext.ignite().sql().execute(null, sql, objects.intValue("partition"))) {
                    while (results.hasNext()) {
                        var row = results.next();
                        customerPurchases.merge(row.intValue("customerId"), row.value("price"), BigDecimal::add);
                    }
                }

                var r = new ArrayList<>(customerPurchases.entrySet());
                r.sort(Map.Entry.comparingByValue());
                Collections.reverse(r);

                var results = Tuple.create();
                for (var p = 0; p < r.size(); p++) {
                    results.set(r.get(p).getKey().toString(), r.get(p).getValue());
                }
                return CompletableFuture.completedFuture(results);
            }
        }

        @Override
        public CompletableFuture<Tuple> reduceAsync(TaskExecutionContext taskExecutionContext, Map<UUID, Tuple> map) {
            var r = new HashMap<Integer,BigDecimal>();
            for (var result : map.values()) {
                for (var e = 0; e < result.columnCount(); e++) {
                    var customerId = result.columnName(e).replaceAll("\"", "");
                    var count = result.<BigDecimal>value(customerId);
                    r.put(Integer.valueOf(customerId), count);
                }
            }
            var orderedResults = new ArrayList<>(r.entrySet());
            orderedResults.sort(Map.Entry.comparingByValue());
            Collections.reverse(orderedResults);

            var topN = Tuple.create();
            for (var i = 0; i < orderedResults.size() && i < customerCount; i++) {
                topN.set(orderedResults.get(i).getKey().toString(), orderedResults.get(i).getValue());
            }

            var customersCache = taskExecutionContext.ignite().tables().table("Customer").recordView();
            var results = Tuple.create();
            for (var p = 0; p < orderedResults.size() && p < customerCount; p++) {
                var key = orderedResults.get(p).getKey();

                var customerRecord = customersCache.get(null, Tuple.create().set("customerId", key));

                String customer;
                if (customerRecord != null) {
                    customer = customerRecord.stringValue("firstName") + " " +
                            customerRecord.stringValue("lastName") + " / " +
                            customerRecord.stringValue("city") + " / " +
                            customerRecord.stringValue("country");
                }
                else {
                    customer = key.toString();
                }

                results.set(String.valueOf(key), orderedResults.get(p).getValue());
            }
            return CompletableFuture.completedFuture(topN);
        }
    }

    private static void printTopPayingCustomers(Tuple results, int customersCount) {
        System.out.println(">>> Top " + customersCount + " Paying Listeners Across All Cluster Nodes");

        var hashResults = TupleHelper.tupleToHashMap(results);

        for (var i = 0; i < results.columnCount(); i++) {
            System.out.println(results.columnName(i) + " / " + results.value(i));
        }
    }
}
