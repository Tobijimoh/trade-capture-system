package com.technicalchallenge.repository;

import com.technicalchallenge.model.Trade;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TradeRepository extends JpaRepository<Trade, Long>, JpaSpecificationExecutor<Trade> {

    // Find all trades by tradeId
    List<Trade> findByTradeId(Long tradeId);

    // Get the maximum tradeId
    @Query("SELECT MAX(t.tradeId) FROM Trade t")
    Optional<Long> findMaxTradeId();

    // Get the maximum version for a given tradeId
    @Query("SELECT MAX(t.version) FROM Trade t WHERE t.tradeId = :tradeId")
    Optional<Integer> findMaxVersionByTradeId(@Param("tradeId") Long tradeId);

    // Find active trade by tradeId
    Optional<Trade> findByTradeIdAndActiveTrue(Long tradeId);

    // Find all active trades ordered by tradeId descending
    List<Trade> findByActiveTrueOrderByTradeIdDesc();

    // Find latest active version of a trade by tradeId
    @Query("SELECT t FROM Trade t WHERE t.tradeId = :tradeId AND t.active = true ORDER BY t.version DESC")
    Optional<Trade> findLatestActiveVersionByTradeId(@Param("tradeId") Long tradeId);

    // Paginated trades for a specific trader (Trader Dashboard / Blotter)
    Page<Trade> findByTraderUser_Id(Long traderId, Pageable pageable);

    // Count methods for dashboard summary
    long countByActiveTrue();
    long countByTradeStatus_TradeStatus(String tradeStatus);
}
