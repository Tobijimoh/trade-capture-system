package com.technicalchallenge.specification;

import com.technicalchallenge.model.Trade;
import jakarta.persistence.criteria.*;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * TradeRsqlSpecification
 *
 * Enables advanced RSQL-like filtering for Trade entities.
 * Example queries:
 * counterparty==UBS;book==London
 * tradeStatus==LIVE;tradeDate>2024-01-01
 * tradeDate>=2024-01-01;tradeMaturityDate<=2025-12-31
 */
public class TradeRsqlSpecification implements Specification<Trade> {

    private final String query;

    public TradeRsqlSpecification(String query) {
        this.query = query;
    }

    @Override
    public Predicate toPredicate(Root<Trade> root, CriteriaQuery<?> cq, CriteriaBuilder cb) {
        if (query == null || query.isBlank()) {
            return cb.conjunction();
        }

        List<Predicate> predicates = new ArrayList<>();
        String[] expressions = query.split(";");

        for (String expr : expressions) {
            expr = expr.trim();

            try {
                if (expr.contains("==")) {
                    String[] parts = expr.split("==");
                    if (parts.length == 2) {
                        addStringMatchPredicate(root, cb, predicates, parts[0].trim(), parts[1].trim());
                    }
                } else if (expr.contains(">=")) {
                    String[] parts = expr.split(">=");
                    if (parts.length == 2) {
                        addDatePredicate(root, cb, predicates, parts[0].trim(), parts[1].trim(), ">=");
                    }
                } else if (expr.contains("<=")) {
                    String[] parts = expr.split("<=");
                    if (parts.length == 2) {
                        addDatePredicate(root, cb, predicates, parts[0].trim(), parts[1].trim(), "<=");
                    }
                } else if (expr.contains(">")) {
                    String[] parts = expr.split(">");
                    if (parts.length == 2) {
                        addDatePredicate(root, cb, predicates, parts[0].trim(), parts[1].trim(), ">");
                    }
                } else if (expr.contains("<")) {
                    String[] parts = expr.split("<");
                    if (parts.length == 2) {
                        addDatePredicate(root, cb, predicates, parts[0].trim(), parts[1].trim(), "<");
                    }
                }
            } catch (Exception e) {
                System.out.println("DEBUG - Skipped invalid expression: " + expr + " (" + e.getMessage() + ")");
            }
        }

        return cb.and(predicates.toArray(new Predicate[0]));
    }

    private void addStringMatchPredicate(Root<Trade> root, CriteriaBuilder cb, List<Predicate> predicates,
                                         String field, String value) {
        try {
            switch (field.toLowerCase()) {
                case "counterparty":
                    predicates.add(cb.like(
                            cb.lower(root.get("counterparty").get("counterpartyName")),
                            "%" + value.toLowerCase() + "%"
                    ));
                    break;
                case "book":
                    predicates.add(cb.like(
                            cb.lower(root.get("book").get("bookName")),
                            "%" + value.toLowerCase() + "%"
                    ));
                    break;
                case "tradestatus":
                    predicates.add(cb.equal(
                            root.get("tradeStatus").get("tradeStatus"),
                            value
                    ));
                    break;
                case "trader":
                    predicates.add(cb.like(
                            cb.lower(root.get("traderUser").get("firstName")),
                            "%" + value.toLowerCase() + "%"
                    ));
                    break;
                default:
                    // Safe fallback: only add if field exists on Trade
                    if (root.getModel().getAttributes().stream().anyMatch(a -> a.getName().equals(field))) {
                        predicates.add(cb.like(cb.lower(root.get(field)), "%" + value.toLowerCase() + "%"));
                    } else {
                        System.out.println("DEBUG - Unknown field ignored in RSQL: " + field);
                    }
                    break;
            }
        } catch (Exception e) {
            System.out.println("DEBUG - Failed to process string predicate for field " + field + ": " + e.getMessage());
        }
    }

    private void addDatePredicate(Root<Trade> root, CriteriaBuilder cb, List<Predicate> predicates,
                                  String field, String value, String operator) {
        try {
            LocalDate date = LocalDate.parse(value);

            Path<LocalDate> path;
            switch (field.toLowerCase()) {
                case "tradedate":
                    path = root.get("tradeDate");
                    break;
                case "maturitydate":
                case "tradematuritydate":
                    path = root.get("tradeMaturityDate");
                    break;
                default:
                    System.out.println("DEBUG - Unknown date field: " + field);
                    return;
            }

            switch (operator) {
                case ">":
                    predicates.add(cb.greaterThan(path, date));
                    break;
                case "<":
                    predicates.add(cb.lessThan(path, date));
                    break;
                case ">=":
                    predicates.add(cb.greaterThanOrEqualTo(path, date));
                    break;
                case "<=":
                    predicates.add(cb.lessThanOrEqualTo(path, date));
                    break;
            }

        } catch (Exception e) {
            System.out.println("DEBUG - Invalid date expression for " + field + ": " + value);
        }
    }
}
