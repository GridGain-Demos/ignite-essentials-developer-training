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
package training.compute;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import javax.cache.Cache;
import training.model.TopCustomer;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.IgniteException;
import org.apache.ignite.binary.BinaryObject;
import org.apache.ignite.cache.CachePeekMode;
import org.apache.ignite.cache.query.QueryCursor;
import org.apache.ignite.cache.query.ScanQuery;
import org.apache.ignite.cluster.ClusterNode;
import org.apache.ignite.compute.ComputeJob;
import org.apache.ignite.compute.ComputeJobAdapter;
import org.apache.ignite.compute.ComputeJobResult;
import org.apache.ignite.compute.ComputeTaskAdapter;
import org.apache.ignite.resources.IgniteInstanceResource;

/**
 * Server-deployed compute task. Invoked by ComputeApp (thin client) via
 * client.compute().execute("training.compute.TopPayingCustomersTask", N).
 *
 * Deployment: this class is packaged into server-tasks.jar (task classes
 * only, no dependencies) and written to docker/libs/ by the build.
 * That directory is bind-mounted into every server container as
 * /opt/gridgain/libs/user_libs/ (see docker-compose.yaml), where the
 * GridGain image loads every jar at startup. Rebuilding the jar requires
 * `docker compose restart` to take effect.
 *
 * Per-node job (LocalTopJob): scans the node's LOCAL slice of the
 * InvoiceLine cache, aggregates per customer, returns its top-N.
 * A local scan is correct here because InvoiceLine rows are co-located
 * with their Customer row by customerId (affinityKey=CustomerId in
 * config/media_store.sql, mirrored by @AffinityKeyMapped in InvoiceLineKey.java).
 */
public class TopPayingCustomersTask extends ComputeTaskAdapter<Integer, TreeSet<TopCustomer>> {

    @Override
    public Map<? extends ComputeJob, ClusterNode> map(List<ClusterNode> subgrid, Integer customersCount) {
        Map<ComputeJob, ClusterNode> jobs = new HashMap<>();
        for (ClusterNode node : subgrid) {
            jobs.put(new LocalTopJob(customersCount), node);
        }
        return jobs;
    }

    @Override
    public TreeSet<TopCustomer> reduce(List<ComputeJobResult> results) {
        TreeSet<TopCustomer> merged = new TreeSet<>();
        for (ComputeJobResult r : results) {
            TreeSet<TopCustomer> partial = r.getData();
            if (partial != null) {
                merged.addAll(partial);
            }
        }
        return merged;
    }

    private static class LocalTopJob extends ComputeJobAdapter {
        private final int customersCount;

        private final HashMap<Integer, BigDecimal> customerPurchases = new HashMap<>();

        @IgniteInstanceResource
        private Ignite localNode;

        LocalTopJob(int customersCount) {
            this.customersCount = customersCount;
        }

        @Override
        public TreeSet<TopCustomer> execute() throws IgniteException {
            IgniteCache<BinaryObject, BinaryObject> invoiceLineCache = localNode.cache(
                "InvoiceLine").withKeepBinary();

            ScanQuery<BinaryObject, BinaryObject> scanQuery = new ScanQuery<>();
            scanQuery.setLocal(true);

            try (QueryCursor<Cache.Entry<BinaryObject, BinaryObject>> cursor = invoiceLineCache.query(scanQuery)) {
                for (Cache.Entry<BinaryObject, BinaryObject> entry : cursor) {
                    BinaryObject val = entry.getValue();
                    BinaryObject key = entry.getKey();

                    BigDecimal unitPrice = val.field("unitPrice");
                    int quantity = val.field("quantity");

                    processPurchase(key.field("customerId"), unitPrice.multiply(new BigDecimal(quantity)));
                }
            }

            return calculateTopCustomers();
        }

        private void processPurchase(int customerId, BigDecimal price) {
            customerPurchases.merge(customerId, price, BigDecimal::add);
        }

        private TreeSet<TopCustomer> calculateTopCustomers() {
            IgniteCache<Integer, BinaryObject> customersCache = localNode.cache("Customer").withKeepBinary();

            TreeSet<TopCustomer> sortedPurchases = new TreeSet<>();
            for (Map.Entry<Integer, BigDecimal> entry : customerPurchases.entrySet()) {
                sortedPurchases.add(new TopCustomer(entry.getKey(), entry.getValue()));
            }

            TreeSet<TopCustomer> top = new TreeSet<>();
            Iterator<TopCustomer> iterator = sortedPurchases.descendingSet().iterator();
            int counter = 0;

            while (iterator.hasNext() && counter++ < customersCount) {
                TopCustomer customer = iterator.next();

                // Safe: InvoiceLine rows are stored on the same node as their Customer row
                // (same affinity key), so localPeek never returns null for a customer that
                // appeared in this node's InvoiceLine scan.
                BinaryObject customerRecord = customersCache.localPeek(customer.getCustomerId(), CachePeekMode.PRIMARY);

                if (customerRecord != null) {
                    customer.setFullName(customerRecord.field("firstName") + " " + customerRecord.field("lastName"));
                    customer.setCity(customerRecord.field("city"));
                    customer.setCountry(customerRecord.field("country"));
                }

                top.add(customer);
            }

            return top;
        }
    }
}
