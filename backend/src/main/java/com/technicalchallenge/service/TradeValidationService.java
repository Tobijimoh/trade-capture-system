package com.technicalchallenge.service;

import com.technicalchallenge.dto.TradeDTO;
import com.technicalchallenge.dto.TradeLegDTO;
import com.technicalchallenge.repository.BookRepository;
import com.technicalchallenge.repository.CounterpartyRepository;
import com.technicalchallenge.validation.ValidationResult;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class TradeValidationService {

    private final BookRepository bookRepository;
    private final CounterpartyRepository counterpartyRepository;

    public TradeValidationService(BookRepository bookRepository,
            CounterpartyRepository counterpartyRepository) {
        this.bookRepository = bookRepository;
        this.counterpartyRepository = counterpartyRepository;
    }

    /** Business rules across the whole trade */
    public ValidationResult validateTradeBusinessRules(TradeDTO dto) {
        ValidationResult vr = ValidationResult.ok();

        LocalDate tradeDate = dto.getTradeDate();
        LocalDate startDate = dto.getTradeStartDate();
        LocalDate maturity = dto.getTradeMaturityDate();

        // Date rules
        if (maturity != null && startDate != null && maturity.isBefore(startDate))
            vr.addError("Maturity date cannot be before start date");
        if (maturity != null && tradeDate != null && maturity.isBefore(tradeDate))
            vr.addError("Maturity date cannot be before trade date");
        if (startDate != null && tradeDate != null && startDate.isBefore(tradeDate))
            vr.addError("Start date cannot be before trade date");
        if (tradeDate != null) {
            long days = ChronoUnit.DAYS.between(tradeDate, LocalDate.now());
            if (days > 30)
                vr.addError("Trade date cannot be more than 30 days in the past");
        }

        // Reference data must exist & be active (Book)
        if (dto.getBookId() != null) {
            bookRepository.findById(dto.getBookId())
                    .ifPresentOrElse(b -> {
                        if (Boolean.FALSE.equals(b.isActive()))
                            vr.addError("Book is not active");
                    },
                            () -> vr.addError("Book does not exist"));
        } else if (dto.getBookName() != null) {
            bookRepository.findByBookName(dto.getBookName())
                    .ifPresentOrElse(b -> {
                        if (Boolean.FALSE.equals(b.isActive()))
                            vr.addError("Book is not active");
                    },
                            () -> vr.addError("Book does not exist"));
        }

        // Reference data must exist & be active (Counterparty)
        if (dto.getCounterpartyId() != null) {
            counterpartyRepository.findById(dto.getCounterpartyId())
                    .ifPresentOrElse(c -> {
                        if (Boolean.FALSE.equals(c.isActive()))
                            vr.addError("Counterparty is not active");
                    },
                            () -> vr.addError("Counterparty does not exist"));
        } else if (dto.getCounterpartyName() != null) {
            counterpartyRepository.findByName(dto.getCounterpartyName())
                    .ifPresentOrElse(c -> {
                        if (Boolean.FALSE.equals(c.isActive()))
                            vr.addError("Counterparty is not active");
                    },
                            () -> vr.addError("Counterparty does not exist"));
        }

        return vr;
    }

    /** operation: CREATE | AMEND | TERMINATE | CANCEL | VIEW */
    public boolean validateUserPrivileges(String role, String operation, TradeDTO dto) {
        if (role == null || operation == null)
            return false;
        switch (role) {
            case "TRADER":
                return switch (operation) {
                    case "CREATE", "AMEND", "TERMINATE", "CANCEL", "VIEW" -> true;
                    default -> false;
                };
            case "SALES":
                return switch (operation) {
                    case "CREATE", "AMEND", "VIEW" -> true;
                    default -> false;
                };
            case "MIDDLE_OFFICE":
                return switch (operation) {
                    case "AMEND", "VIEW" -> true;
                    default -> false;
                };
            case "SUPPORT":
                return "VIEW".equals(operation);
            default:
                return false;
        }
    }

    /** Cross-leg rules */
    public ValidationResult validateTradeLegConsistency(List<TradeLegDTO> legs, TradeDTO dto) {
        ValidationResult vr = ValidationResult.ok();
        if (legs == null || legs.size() != 2) {
            vr.addError("Trade must have exactly 2 legs");
            return vr;
        }

        TradeLegDTO a = legs.get(0), b = legs.get(1);

        // Maturity consistency (either trade-level or legs-level but consistent)
        if (dto.getTradeMaturityDate() == null) {
            vr.addError("Trade maturity date must be defined");
        }

        // Opposite pay/receive flags
        if (a.getPayReceiveFlag() == null || b.getPayReceiveFlag() == null) {
            vr.addError("Both legs must have pay/receive flags");
        } else if (a.getPayReceiveFlag().equalsIgnoreCase(b.getPayReceiveFlag())) {
            vr.addError("Legs must have opposite pay/receive flags");
        }

        // Floating requires index; Fixed requires rate
        if ("Floating".equalsIgnoreCase(a.getLegType()) && a.getIndexName() == null)
            vr.addError("Floating leg A must have an index specified");
        if ("Floating".equalsIgnoreCase(b.getLegType()) && b.getIndexName() == null)
            vr.addError("Floating leg B must have an index specified");

        if ("Fixed".equalsIgnoreCase(a.getLegType()) && a.getRate() == null)
            vr.addError("Fixed leg A must have a valid rate");
        if ("Fixed".equalsIgnoreCase(b.getLegType()) && b.getRate() == null)
            vr.addError("Fixed leg B must have a valid rate");

        return vr;
    }
}
