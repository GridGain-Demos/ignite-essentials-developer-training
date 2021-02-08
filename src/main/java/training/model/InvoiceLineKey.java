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
package training.model;

import org.apache.ignite.cache.affinity.AffinityKeyMapped;

public class InvoiceLineKey {
    private int invoiceLineId;

    @AffinityKeyMapped
    private int CUSTOMERID;

    public InvoiceLineKey(int invoiceLineId, int customerId) {
        this.invoiceLineId = invoiceLineId;
        this.CUSTOMERID = customerId;
    }

    public int getInvoiceLineId() {
        return invoiceLineId;
    }

    public int getCustomerId() {
        return CUSTOMERID;
    }

    @Override public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;

        InvoiceLineKey key = (InvoiceLineKey)o;

        if (invoiceLineId != key.invoiceLineId)
            return false;
        return CUSTOMERID == key.CUSTOMERID;
    }

    @Override public int hashCode() {
        int result = invoiceLineId;
        result = 31 * result + CUSTOMERID;
        return result;
    }
}
