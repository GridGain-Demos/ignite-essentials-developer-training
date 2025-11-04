package training.model;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Comparator;

public class CustomerPrice implements Serializable, Comparator<CustomerPrice> {
    private final Integer customerId;
    private final BigDecimal price;

    public CustomerPrice(Integer customerId, BigDecimal price) {
        this.customerId = customerId;
        this.price = price;
    }

    public Integer getCustomerId() {
        return customerId;
    }

    public BigDecimal getPrice() {
        return price;
    }

    @Override
    public int compare(CustomerPrice o1, CustomerPrice o2) {
        return o1.getPrice().compareTo(o2.getPrice());
    }

    @Override
    public String toString() {
        return "CustomerPrice{" +
                "customerId=" + customerId +
                ", price=" + price +
                '}';
    }
}
