package com.technicalchallenge.service;

import com.technicalchallenge.dto.TradeDTO;
import com.technicalchallenge.dto.TradeLegDTO;
import com.technicalchallenge.model.Book;
import com.technicalchallenge.model.Cashflow;
import com.technicalchallenge.model.Counterparty;
import com.technicalchallenge.model.Schedule;
import com.technicalchallenge.model.Trade;
import com.technicalchallenge.model.TradeLeg;
import com.technicalchallenge.model.TradeStatus;
import com.technicalchallenge.repository.BookRepository;
import com.technicalchallenge.repository.CashflowRepository;
import com.technicalchallenge.repository.CounterpartyRepository;
import com.technicalchallenge.repository.TradeLegRepository;
import com.technicalchallenge.repository.TradeRepository;
import com.technicalchallenge.repository.TradeStatusRepository;
import com.technicalchallenge.validation.ValidationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;


import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TradeServiceTest {

    @Mock
    private TradeRepository tradeRepository;

    @Mock
    private TradeLegRepository tradeLegRepository;

    @Mock
    private CashflowRepository cashflowRepository;

    @Mock
    private TradeStatusRepository tradeStatusRepository;

    @Mock
    private AdditionalInfoService additionalInfoService;

    @Mock
    private BookRepository bookRepository;

    @Mock
    private CounterpartyRepository counterpartyRepository;

    @Mock
    private TradeValidationService tradeValidationService;

    @InjectMocks
    private TradeService tradeService;

    private TradeDTO tradeDTO;
    private Trade trade;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        // Set up test data
        tradeDTO = new TradeDTO();
        tradeDTO.setTradeId(100001L);
        tradeDTO.setTradeDate(LocalDate.of(2025, 1, 15));
        tradeDTO.setTradeStartDate(LocalDate.of(2025, 1, 17));
        tradeDTO.setTradeMaturityDate(LocalDate.of(2026, 1, 17));

        TradeLegDTO leg1 = new TradeLegDTO();
        leg1.setNotional(BigDecimal.valueOf(1000000));
        leg1.setRate(0.05);

        TradeLegDTO leg2 = new TradeLegDTO();
        leg2.setNotional(BigDecimal.valueOf(1000000));
        leg2.setRate(0.0);

        tradeDTO.setTradeLegs(Arrays.asList(leg1, leg2));

        trade = new Trade();
        trade.setId(1L);
        trade.setTradeId(100001L);

        // Default stubbing for validation service
        when(tradeValidationService.validateTradeBusinessRules(any(TradeDTO.class)))
                .thenReturn(ValidationResult.ok());
        when(tradeValidationService.validateTradeLegConsistency(anyList(), any(TradeDTO.class)))
                .thenReturn(ValidationResult.ok());

        // Lenient stubbing for validations
        lenient().when(tradeValidationService.validateTradeBusinessRules(any(TradeDTO.class)))
                .thenReturn(ValidationResult.ok());
        lenient().when(tradeValidationService.validateTradeLegConsistency(anyList(), any(TradeDTO.class)))
                .thenReturn(ValidationResult.ok());
    }

    @Test
    void testCreateTrade_Success() {
        // Given
        tradeDTO.setBookName("Book1");
        tradeDTO.setCounterpartyName("Counterparty1");
        tradeDTO.setTradeStatus("NEW");

        Book mockBook = new Book();
        mockBook.setId(1L);

        Counterparty mockCounterparty = new Counterparty();
        mockCounterparty.setId(1L);

        TradeStatus mockStatus = new TradeStatus();
        mockStatus.setId(1L);

        TradeLeg mockLeg = new TradeLeg();
        mockLeg.setLegId(10L);

        when(bookRepository.findByBookName(anyString())).thenReturn(Optional.of(mockBook));
        when(counterpartyRepository.findByName(anyString())).thenReturn(Optional.of(mockCounterparty));
        when(tradeStatusRepository.findByTradeStatus(anyString())).thenReturn(Optional.of(mockStatus));

        when(tradeRepository.save(any(Trade.class))).thenReturn(trade);
        when(tradeLegRepository.save(any(TradeLeg.class))).thenReturn(mockLeg);

        // When
        Trade result = tradeService.createTrade(tradeDTO);

        // Then
        assertNotNull(result);
        assertEquals(100001L, result.getTradeId());
        verify(tradeRepository).save(any(Trade.class));
    }

    @Test
    void testCreateTrade_InvalidDates_ShouldFail() {
        // Given - This test is intentionally failing for candidates to fix
        tradeDTO.setTradeStartDate(LocalDate.of(2025, 1, 10)); // Before trade date

        // Override default validation response for this test
        when(tradeValidationService.validateTradeBusinessRules(any(TradeDTO.class)))
                .thenReturn(ValidationResult.error("Start date cannot be before trade date"));

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            tradeService.createTrade(tradeDTO);
        });

        // Assert that the correct error message is returned
        assertEquals("Start date cannot be before trade date", exception.getMessage());
    }

    @Test
    void testCreateTrade_InvalidLegCount_ShouldFail() {
        // Given
        tradeDTO.setTradeLegs(Arrays.asList(new TradeLegDTO())); // Only 1 leg

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            tradeService.createTrade(tradeDTO);
        });

        assertTrue(exception.getMessage().contains("exactly 2 legs"));
    }

    @Test
    void testGetTradeById_Found() {
        // Given
        when(tradeRepository.findByTradeIdAndActiveTrue(100001L)).thenReturn(Optional.of(trade));

        // When
        Optional<Trade> result = tradeService.getTradeById(100001L);

        // Then
        assertTrue(result.isPresent());
        assertEquals(100001L, result.get().getTradeId());
    }

    @Test
    void testGetTradeById_NotFound() {
        // Given
        when(tradeRepository.findByTradeIdAndActiveTrue(999L)).thenReturn(Optional.empty());

        // When
        Optional<Trade> result = tradeService.getTradeById(999L);

        // Then
        assertFalse(result.isPresent());
    }

    @Test
    void testAmendTrade_Success() {
        // Given
        trade.setVersion(1);

        when(tradeRepository.findByTradeIdAndActiveTrue(100001L)).thenReturn(Optional.of(trade));
        when(tradeStatusRepository.findByTradeStatus("AMENDED"))
                .thenReturn(Optional.of(new com.technicalchallenge.model.TradeStatus()));
        when(tradeRepository.save(any(Trade.class))).thenReturn(trade);

        TradeLeg mockLeg = new TradeLeg();
        mockLeg.setLegId(10L);
        when(tradeLegRepository.save(any(TradeLeg.class))).thenReturn(mockLeg);

        // When
        Trade result = tradeService.amendTrade(100001L, tradeDTO);

        // Then
        assertNotNull(result);
        verify(tradeRepository, times(2)).save(any(Trade.class)); // Save old and new
    }

    @Test
    void testAmendTrade_TradeNotFound() {
        // Given
        when(tradeRepository.findByTradeIdAndActiveTrue(999L)).thenReturn(Optional.empty());

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            tradeService.amendTrade(999L, tradeDTO);
        });

        assertTrue(exception.getMessage().contains("Trade not found"));
    }

    @Test
    void testCashflowGeneration_MonthlySchedule() throws Exception {
        // Given
        TradeLeg leg = new TradeLeg();
        leg.setLegId(1L);
        leg.setNotional(BigDecimal.valueOf(1_000_000));
        leg.setRate(0.05);

        Schedule monthlySchedule = new Schedule();
        monthlySchedule.setSchedule("Monthly");
        leg.setCalculationPeriodSchedule(monthlySchedule);

        when(cashflowRepository.save(any(Cashflow.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        LocalDate startDate = LocalDate.of(2025, 1, 1);
        LocalDate maturityDate = LocalDate.of(2025, 12, 31);

        // Calculate expected number of months between start and maturity
        long monthsBetween = java.time.temporal.ChronoUnit.MONTHS.between(startDate, maturityDate);
        int invocationsCount = (int) monthsBetween; // should be 11 for current logic

        // When — use reflection to call private generateCashflows()
        Method method = TradeService.class.getDeclaredMethod(
                "generateCashflows", TradeLeg.class, LocalDate.class, LocalDate.class);
        method.setAccessible(true);
        method.invoke(tradeService, leg, startDate, maturityDate);

        // Then — verify save() was called correct number of times
        verify(cashflowRepository, times(invocationsCount)).save(any(Cashflow.class));
    }
}
