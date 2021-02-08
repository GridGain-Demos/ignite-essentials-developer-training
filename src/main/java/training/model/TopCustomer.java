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

import java.math.BigDecimal;
import org.jetbrains.annotations.NotNull;

public class TopCustomer implements Comparable<TopCustomer>{
    private int customerId;

    private String fullName;

    private String country;

    private String city;

    BigDecimal totalPurchases;

    public TopCustomer(int customerId, BigDecimal totalPurchases) {
        this.customerId = customerId;
        this.totalPurchases = totalPurchases;
    }

    public int getCustomerId() {
        return customerId;
    }

    public void setCustomerId(int customerId) {
        this.customerId = customerId;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public BigDecimal getTotalPurchases() {
        return totalPurchases;
    }

    public void setTotalPurchases(BigDecimal totalPurchases) {
        this.totalPurchases = totalPurchases;
    }

    @Override public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;

        TopCustomer customer = (TopCustomer)o;

        if (customerId != customer.customerId)
            return false;

        return totalPurchases.equals(customer.totalPurchases);
    }

    @Override public int hashCode() {
        int result = customerId;
        result = 31 * result + totalPurchases.hashCode();
        return result;
    }

    @Override public int compareTo(@NotNull TopCustomer customer) {
        int result = totalPurchases.compareTo(customer.getTotalPurchases());

        if (result == 0)
            return customerId - customer.getCustomerId();

        return result;
    }

    @Override public String toString() {
        return "TopCustomer{" +
            "customerId=" + customerId +
            ", fullName='" + fullName + '\'' +
            ", country='" + country + '\'' +
            ", city='" + city + '\'' +
            ", totalPurchases=" + totalPurchases +
            '}';
    }
}
