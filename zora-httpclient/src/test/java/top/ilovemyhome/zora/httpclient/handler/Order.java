package top.ilovemyhome.zora.httpclient.handler;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.math.BigDecimal;

@JsonPropertyOrder({"id", "counterparty", "amount"})
public class Order {

    private Long id;
    private String counterparty;
    private BigDecimal amount;

    public Order() {
    }

    public Order(Long id, String counterparty, BigDecimal amount) {
        this.id = id;
        this.counterparty = counterparty;
        this.amount = amount;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCounterparty() {
        return counterparty;
    }

    public void setCounterparty(String counterparty) {
        this.counterparty = counterparty;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }
}
