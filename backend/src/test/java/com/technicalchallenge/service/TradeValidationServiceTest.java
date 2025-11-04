package com.technicalchallenge.service;

import com.technicalchallenge.dto.CashflowDTO;
import com.technicalchallenge.dto.TradeDTO;
import com.technicalchallenge.dto.TradeLegDTO;
import com.technicalchallenge.model.Book;
import com.technicalchallenge.model.Counterparty;
import com.technicalchallenge.model.ApplicationUser;
import com.technicalchallenge.model.UserProfile;
import com.technicalchallenge.repository.*;
import com.technicalchallenge.validation.ValidationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class TradeValidationServiceTest {

    @Mock
    private BookRepository bookRepository;
    @Mock
    private CounterpartyRepository counterpartyRepository;
    @Mock
    private ApplicationUserRepository applicationUserRepository;
    @Mock
    private UserProfileService userProfileService;

    @InjectMocks
    private TradeValidationService tradeValidationService;

    private TradeDTO tradeDTO;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        tradeDTO = new TradeDTO();
        tradeDTO.setTradeId(1001L);
        tradeDTO.setTradeDate(LocalDate.now());
        tradeDTO.setTradeStartDate(LocalDate.now().plusDays(2));
        tradeDTO.setTradeMaturityDate(LocalDate.now().plusMonths(6));
        tradeDTO.setBookName("Book1");
        tradeDTO.setCounterpartyName("CP1");

        TradeLegDTO legA = new TradeLegDTO();
        legA.setLegType("Fixed");
        legA.setRate(0.05);
        legA.setNotional(BigDecimal.valueOf(100000));
        legA.setPayReceiveFlag("Pay");

        TradeLegDTO legB = new TradeLegDTO();
        legB.setLegType("Floating");
        legB.setIndexName("LIBOR");
        legB.setNotional(BigDecimal.valueOf(100000));
        legB.setPayReceiveFlag("Receive");

        tradeDTO.setTradeLegs(List.of(legA, legB));
    }

    @Test
    void validateTradeBusinessRules_validData_returnsOk() {
        Book book = new Book();
        book.setActive(true);
        when(bookRepository.findByBookName("Book1")).thenReturn(Optional.of(book));

        Counterparty cp = new Counterparty();
        cp.setActive(true);
        when(counterpartyRepository.findByName("CP1")).thenReturn(Optional.of(cp));

        ValidationResult result = tradeValidationService.validateTradeBusinessRules(tradeDTO);

        assertTrue(result.isValid(), "Expected valid business rules for correct trade");
    }

    @Test
    void validateTradeBusinessRules_inactiveBook_addsError() {
        Book book = new Book();
        book.setActive(false);
        when(bookRepository.findByBookName("Book1")).thenReturn(Optional.of(book));

        ValidationResult result = tradeValidationService.validateTradeBusinessRules(tradeDTO);

        assertFalse(result.isValid());
        assertTrue(result.getErrors().contains("Book is not active"));
    }

    @Test
    void validateUserPrivileges_traderCanCreate_returnsOk() {
        UserProfile profile = new UserProfile();
        profile.setUserType("TRADER");

        when(userProfileService.getUserProfileById(1L)).thenReturn(Optional.of(profile));

        ValidationResult result = tradeValidationService.validateUserPrivileges(1L, "CREATE", tradeDTO);

        assertTrue(result.isValid(), "Trader should be allowed to CREATE");
    }

    @Test
    void validateUserPrivileges_salesCannotTerminate_addsError() {
        UserProfile profile = new UserProfile();
        profile.setUserType("SALES");

        when(userProfileService.getUserProfileById(2L)).thenReturn(Optional.of(profile));

        ValidationResult result = tradeValidationService.validateUserPrivileges(2L, "TERMINATE", tradeDTO);

        assertFalse(result.isValid());
        assertTrue(result.getErrors().get(0).contains("not allowed"));
    }

    @Test
    void validateTradeLegConsistency_invalidSamePayRec_addsError() {
        TradeLegDTO leg1 = new TradeLegDTO();
        leg1.setLegType("Fixed");
        leg1.setRate(0.05);
        leg1.setNotional(BigDecimal.valueOf(100000));
        leg1.setPayReceiveFlag("Pay");

        TradeLegDTO leg2 = new TradeLegDTO();
        leg2.setLegType("Floating");
        leg2.setIndexName("LIBOR");
        leg2.setNotional(BigDecimal.valueOf(100000));
        leg2.setPayReceiveFlag("Pay"); // both "Pay"

        tradeDTO.setTradeLegs(List.of(leg1, leg2));

        ValidationResult result = tradeValidationService.validateTradeLegConsistency(tradeDTO.getTradeLegs(), tradeDTO);

        assertFalse(result.isValid());
        assertTrue(result.getErrors().contains("Legs must have opposite pay/receive flags"));
    }

    @Test
    void validateTradeLegConsistency_cashflowBeyondMaturity_addsError() {
        TradeLegDTO leg1 = new TradeLegDTO();
        leg1.setLegType("Fixed");
        leg1.setRate(0.05);
        leg1.setNotional(BigDecimal.valueOf(100000));
        leg1.setPayReceiveFlag("Pay");

        CashflowDTO cf = new CashflowDTO();
        cf.setValueDate(tradeDTO.getTradeMaturityDate().plusDays(10)); // Beyond maturity
        leg1.setCashflows(List.of(cf));

        TradeLegDTO leg2 = new TradeLegDTO();
        leg2.setLegType("Floating");
        leg2.setIndexName("LIBOR");
        leg2.setNotional(BigDecimal.valueOf(100000));
        leg2.setPayReceiveFlag("Receive");

        tradeDTO.setTradeLegs(List.of(leg1, leg2));

        ValidationResult result = tradeValidationService.validateTradeLegConsistency(tradeDTO.getTradeLegs(), tradeDTO);

        assertFalse(result.isValid());
        assertTrue(result.getErrors().get(0).contains("cannot exceed trade maturity date"));
    }
}
