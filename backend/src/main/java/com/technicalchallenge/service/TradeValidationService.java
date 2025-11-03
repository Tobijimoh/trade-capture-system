package com.technicalchallenge.service;

import com.technicalchallenge.dto.TradeDTO;
import com.technicalchallenge.dto.TradeLegDTO;
import com.technicalchallenge.model.ApplicationUser;
import com.technicalchallenge.repository.*;
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
 *  - Entity (Book / Counterparty / User) active status checks
 *  - Role-based privilege enforcement
 *
 * This service ensures all trade data meets business constraints before persistence.
 */
@Service
public class TradeValidationService {

    private static final Logger log = LoggerFactory.getLogger(TradeValidationService.class);

    private final BookRepository bookRepository;
    private final CounterpartyRepository counterpartyRepository;
    private final ApplicationUserRepository applicationUserRepository;
    private final UserProfileService userProfileService;

    // Role-based operation constants
    public static final String OP_CREATE = "CREATE";
    public static final String OP_AMEND = "AMEND";
    public static final String OP_TERMINATE = "TERMINATE";
    public static final String OP_CANCEL = "CANCEL";
    public static final String OP_VIEW = "VIEW";

    public TradeValidationService(BookRepository bookRepository,
                                  CounterpartyRepository counterpartyRepository,
                                  ApplicationUserRepository applicationUserRepository,
                                  UserProfileService userProfileService) {
        this.bookRepository = bookRepository;
        this.counterpartyRepository = counterpartyRepository;
        this.applicationUserRepository = applicationUserRepository;
        this.userProfileService = userProfileService;
    }

    /**
     * Validates business rules across an entire trade.
     */
    public ValidationResult validateTradeBusinessRules(TradeDTO dto) {
        ValidationResult vr = ValidationResult.ok();
        log.debug("Validating business rules for tradeId={}", dto.getTradeId());

        applyDateRules(dto, vr);
        applyEntityStatusRules(dto, vr);
        applyUserActivityRules(dto, vr);

        return vr;
    }

    /**
     * Validates role-based privileges for a given operation.
     * Uses user profile info to determine permissions.
     */
    public ValidationResult validateUserPrivileges(Long userId, String operation, TradeDTO dto) {
        ValidationResult vr = ValidationResult.ok();

        if (userId == null) {
            vr.addError("User ID is required for privilege validation");
            return vr;
        }

        var profileOpt = userProfileService.getUserProfileById(userId);
        if (profileOpt.isEmpty()) {
            vr.addError("User profile not found for ID: " + userId);
            return vr;
        }

        String role = profileOpt.get().getUserType();
        boolean allowed;

        switch (role.toUpperCase()) {
            case "TRADER":
                allowed = isAllowed(operation, OP_CREATE, OP_AMEND, OP_TERMINATE, OP_CANCEL, OP_VIEW);
                break;
            case "SALES":
                allowed = isAllowed(operation, OP_CREATE, OP_AMEND, OP_VIEW);
                break;
            case "MIDDLE_OFFICE":
                allowed = isAllowed(operation, OP_AMEND, OP_VIEW);
                break;
            case "SUPPORT":
                allowed = OP_VIEW.equalsIgnoreCase(operation);
                break;
            default:
                vr.addError("Unknown user role: " + role);
                return vr;
        }

        if (!allowed) {
            vr.addError("Role '" + role + "' not allowed to perform operation: " + operation);
        }

        return vr;
    }

    /**
     * Validates consistency and logical correctness across trade legs.
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
        applyMaturityMatchCheck(dto, legs, vr);
        applyPayReceiveCheck(legA, legB, vr);
        applyLegTypeRules(legA, "A", vr);
        applyLegTypeRules(legB, "B", vr);
        applyCashflowConsistencyCheck(legs, dto, vr);

        return vr;
    }

    // ========== PRIVATE HELPERS ==========

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

    /** Ensures trader and inputter users exist and are active. */
    private void applyUserActivityRules(TradeDTO dto, ValidationResult vr) {
        if (dto.getTraderUserId() != null) {
            applicationUserRepository.findById(dto.getTraderUserId())
                    .ifPresentOrElse(u -> {
                        if (!u.isActive()) vr.addError("Trader user is not active");
                    }, () -> vr.addError("Trader user not found"));
        }
        if (dto.getTradeInputterUserId() != null) {
            applicationUserRepository.findById(dto.getTradeInputterUserId())
                    .ifPresentOrElse(u -> {
                        if (!u.isActive()) vr.addError("Inputter user is not active");
                    }, () -> vr.addError("Inputter user not found"));
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

        /** Ensures all legs have cashflows that align with trade maturity date. */
    private void applyMaturityMatchCheck(TradeDTO dto, List<TradeLegDTO> legs, ValidationResult vr) {
        if (dto.getTradeMaturityDate() == null) {
            return;
        }

        for (TradeLegDTO leg : legs) {
            if (leg.getCashflows() == null) {
                continue;
            }

            leg.getCashflows().forEach(cf -> {
                if (cf.getValueDate() != null && cf.getValueDate().isAfter(dto.getTradeMaturityDate())) {
                    vr.addError("Cashflow date " + cf.getValueDate() + " cannot exceed trade maturity date");
                }
            });
        }
    }

    /** Ensures all cashflows occur within the trade's valid date range (start → maturity). */
    private void applyCashflowConsistencyCheck(List<TradeLegDTO> legs, TradeDTO dto, ValidationResult vr) {
        if (dto.getTradeStartDate() == null || dto.getTradeMaturityDate() == null) {
            return;
        }

        for (TradeLegDTO leg : legs) {
            if (leg.getCashflows() == null) {
                continue;
            }

            leg.getCashflows().forEach(cf -> {
                if (cf.getValueDate() == null) {
                    return;
                }

                if (cf.getValueDate().isBefore(dto.getTradeStartDate())) {
                    vr.addError("Cashflow date " + cf.getValueDate() + " cannot be before trade start date");
                }

                if (cf.getValueDate().isAfter(dto.getTradeMaturityDate())) {
                    vr.addError("Cashflow date " + cf.getValueDate() + " cannot exceed trade maturity date");
                }
            });
        }
    }


    /** Utility to simplify operation checks in privilege validation. */
    private boolean isAllowed(String operation, String... allowedOps) {
        for (String op : allowedOps) {
            if (op.equalsIgnoreCase(operation)) return true;
        }
        return false;
    }
}
