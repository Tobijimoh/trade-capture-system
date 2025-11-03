package com.technicalchallenge.service;

import com.technicalchallenge.dto.TradeDTO;
import com.technicalchallenge.dto.TradeLegDTO;
import com.technicalchallenge.repository.BookRepository;
import com.technicalchallenge.repository.CounterpartyRepository;
import com.technicalchallenge.validation.ValidationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * TradeValidationService
 *
 * Provides comprehensive business validation for trade creation and amendment.
 * Includes:
 *  - Date validation rules
 *  - Cross-leg consistency checks
 *  - Entity (Book / Counterparty) active status checks
 *  - Role-based privilege enforcement
 *
 * This service ensures all trade data meets business constraints before persistence.
 */
@Service
public class TradeValidationService {

    private static final Logger log = LoggerFactory.getLogger(TradeValidationService.class);

    private final BookRepository bookRepository;
    private final CounterpartyRepository counterpartyRepository;

    // Role-based operation constants for clarity and reuse
    public static final String OP_CREATE = "CREATE";
    public static final String OP_AMEND = "AMEND";
    public static final String OP_TERMINATE = "TERMINATE";
    public static final String OP_CANCEL = "CANCEL";
    public static final String OP_VIEW = "VIEW";

    public TradeValidationService(BookRepository bookRepository,
                                  CounterpartyRepository counterpartyRepository) {
        this.bookRepository = bookRepository;
        this.counterpartyRepository = counterpartyRepository;
    }

    /**
     * Validates business rules across an entire trade.
     * Covers:
     *  - Date sequencing and range rules
     *  - Book and Counterparty reference validity
     */
    public ValidationResult validateTradeBusinessRules(TradeDTO dto) {
        ValidationResult vr = ValidationResult.ok();
        log.debug("Validating business rules for tradeId={}", dto.getTradeId());

        applyDateRules(dto, vr);
        applyEntityStatusRules(dto, vr);

        return vr;
    }

    /**
     * Validates role-based privileges for a given operation.
     * Used to enforce the allowed actions for TRADER, SALES, MIDDLE_OFFICE, and SUPPORT.
     */
    public boolean validateUserPrivileges(String role, String operation, TradeDTO dto) {
        if (role == null || operation == null) {
            log.warn("Privilege validation failed: role or operation is null");
            return false;
        }

        switch (role.toUpperCase()) {
            case "TRADER":
                return isAllowed(operation, OP_CREATE, OP_AMEND, OP_TERMINATE, OP_CANCEL, OP_VIEW);
            case "SALES":
                return isAllowed(operation, OP_CREATE, OP_AMEND, OP_VIEW);
            case "MIDDLE_OFFICE":
                return isAllowed(operation, OP_AMEND, OP_VIEW);
            case "SUPPORT":
                return OP_VIEW.equalsIgnoreCase(operation);
            default:
                log.warn("Privilege validation failed: unknown role '{}'", role);
                return false;
        }
    }

    /**
     * Validates consistency and logical correctness across trade legs.
     * Ensures:
     *  - Exactly two legs exist
     *  - Legs have opposite pay/receive flags
     *  - Floating legs have an index
     *  - Fixed legs have a rate
     *  - Trade maturity is defined
     */
    public ValidationResult validateTradeLegConsistency(List<TradeLegDTO> legs, TradeDTO dto) {
        ValidationResult vr = ValidationResult.ok();
        log.debug("Validating leg consistency for tradeId={}", dto.getTradeId());

        if (legs == null || legs.size() != 2) {
            vr.addError("Trade must have exactly 2 legs");
            return vr;
        }

        TradeLegDTO legA = legs.get(0);
        TradeLegDTO legB = legs.get(1);

        applyMaturityCheck(dto, vr);
        applyPayReceiveCheck(legA, legB, vr);
        applyLegTypeRules(legA, "A", vr);
        applyLegTypeRules(legB, "B", vr);

        return vr;
    }

    // Helper Methods

    /** Validates logical ordering of trade, start, and maturity dates. */
    private void applyDateRules(TradeDTO dto, ValidationResult vr) {
        LocalDate tradeDate = dto.getTradeDate();
        LocalDate startDate = dto.getTradeStartDate();
        LocalDate maturityDate = dto.getTradeMaturityDate();

        if (maturityDate != null && startDate != null && maturityDate.isBefore(startDate))
            vr.addError("Maturity date cannot be before start date");

        if (maturityDate != null && tradeDate != null && maturityDate.isBefore(tradeDate))
            vr.addError("Maturity date cannot be before trade date");

        if (startDate != null && tradeDate != null && startDate.isBefore(tradeDate))
            vr.addError("Start date cannot be before trade date");

        if (tradeDate != null) {
            long days = ChronoUnit.DAYS.between(tradeDate, LocalDate.now());
            if (days > 30)
                vr.addError("Trade date cannot be more than 30 days in the past");
        }
    }

    /** Ensures referenced entities (Book, Counterparty) exist and are active. */
    private void applyEntityStatusRules(TradeDTO dto, ValidationResult vr) {
        // --- Book validation ---
        if (dto.getBookId() != null) {
            bookRepository.findById(dto.getBookId())
                    .ifPresentOrElse(b -> {
                        if (!b.isActive()) vr.addError("Book is not active");
                    }, () -> vr.addError("Book does not exist"));
        } else if (dto.getBookName() != null) {
            bookRepository.findByBookName(dto.getBookName())
                    .ifPresentOrElse(b -> {
                        if (!b.isActive()) vr.addError("Book is not active");
                    }, () -> vr.addError("Book does not exist"));
        }

        // --- Counterparty validation ---
        if (dto.getCounterpartyId() != null) {
            counterpartyRepository.findById(dto.getCounterpartyId())
                    .ifPresentOrElse(c -> {
                        if (!c.isActive()) vr.addError("Counterparty is not active");
                    }, () -> vr.addError("Counterparty does not exist"));
        } else if (dto.getCounterpartyName() != null) {
            counterpartyRepository.findByName(dto.getCounterpartyName())
                    .ifPresentOrElse(c -> {
                        if (!c.isActive()) vr.addError("Counterparty is not active");
                    }, () -> vr.addError("Counterparty does not exist"));
        }
    }

    /** Ensures a trade has a maturity date defined. */
    private void applyMaturityCheck(TradeDTO dto, ValidationResult vr) {
        if (dto.getTradeMaturityDate() == null)
            vr.addError("Trade maturity date must be defined");
    }

    /** Ensures both legs have pay/receive flags and they are opposite. */
    private void applyPayReceiveCheck(TradeLegDTO a, TradeLegDTO b, ValidationResult vr) {
        if (a.getPayReceiveFlag() == null || b.getPayReceiveFlag() == null) {
            vr.addError("Both legs must have pay/receive flags");
        } else if (a.getPayReceiveFlag().equalsIgnoreCase(b.getPayReceiveFlag())) {
            vr.addError("Legs must have opposite pay/receive flags");
        }
    }

    /** Validates leg-specific type logic (Floating needs index, Fixed needs rate). */
    private void applyLegTypeRules(TradeLegDTO leg, String label, ValidationResult vr) {
        if ("Floating".equalsIgnoreCase(leg.getLegType()) && leg.getIndexName() == null)
            vr.addError("Floating leg " + label + " must have an index specified");
        if ("Fixed".equalsIgnoreCase(leg.getLegType()) && leg.getRate() == null)
            vr.addError("Fixed leg " + label + " must have a valid rate");
    }

    /** Utility to simplify operation checks in privilege validation. */
    private boolean isAllowed(String operation, String... allowedOps) {
        for (String op : allowedOps) {
            if (op.equalsIgnoreCase(operation)) return true;
        }
        return false;
    }
}
