package com.technicalchallenge.specification;

import com.technicalchallenge.model.Trade;

import java.time.LocalDate;

import org.springframework.data.jpa.domain.Specification;

public class TradeSpecification {

    public static Specification<Trade> hasCounterparty(String counterparty) {
        return (root, query, cb) -> counterparty == null ? null
                : cb.like(cb.lower(root.get("counterparty").get("name")), "%" + counterparty.toLowerCase() + "%");
    }

    public static Specification<Trade> hasBook(String book) {
        return (root, query, cb) -> book == null ? null
                : cb.like(cb.lower(root.get("book").get("bookName")), "%" + book.toLowerCase() + "%");
    }

    public static Specification<Trade> hasTrader(String trader) {
        return (root, query, cb) -> trader == null ? null
                : cb.like(cb.lower(root.get("traderName")), "%" + trader.toLowerCase() + "%");
    }

    public static Specification<Trade> hasStatus(String status) {
        return (root, query, cb) -> status == null ? null
                : cb.like(cb.lower(root.get("tradeStatus").get("tradeStatus")), "%" + status.toLowerCase() + "%");
    }

    public static Specification<Trade> hasTradeDateAfter(LocalDate fromDate) {
        return (root, query, cb) -> fromDate == null ? null : cb.greaterThanOrEqualTo(root.get("tradeDate"), fromDate);
    }

    public static Specification<Trade> hasMaturityDateBefore(LocalDate toDate) {
        return (root, query, cb) -> toDate == null ? null : cb.lessThanOrEqualTo(root.get("tradeMaturityDate"), toDate);
    }

}
