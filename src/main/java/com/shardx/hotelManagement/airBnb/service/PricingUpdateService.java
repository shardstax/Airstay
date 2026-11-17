package com.shardx.hotelManagement.airBnb.service;

import com.shardx.hotelManagement.airBnb.entity.Hotel;
import com.shardx.hotelManagement.airBnb.entity.Inventory;
import com.shardx.hotelManagement.airBnb.repository.HotelRepository;
import com.shardx.hotelManagement.airBnb.repository.InventoryRepository;
import com.shardx.hotelManagement.airBnb.repository.PriceWriter;
import com.shardx.hotelManagement.airBnb.strategy.PricingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Keeps stored nightly prices (inventory.price) and each hotel's daily "from" price (hotel_min_price) up to date.
 * Runs hourly, and on demand after owners change prices or surge factors.
 *
 * <p>Cost per hotel is constant in the number of nights: one query reads the hotel's nights (with their rooms), the prices
 * are computed in memory, and at most two statements write the result (only rows whose price changed). It used to run
 * one query per night for the "from" prices and one write per changed row.
 *
 * <p><b>Concurrency.</b> Every refresh of a hotel takes a per-hotel advisory lock before it reads, so two refreshes of one
 * hotel never compute from stale data. Owner changes (surge factor, room price or count, deleting a room) refresh only
 * AFTER their own transaction committed ({@link #refreshAfterCommit}), so no transaction ever holds room row locks while
 * waiting for the refresh's locks. Together with locking rows in id order this rules out deadlocks between the refresh,
 * owner changes and bookings.
 */
@Service
@Slf4j
public class PricingUpdateService {

    /** What one run of the scheduled refresh did. */
    public record RefreshResult(int hotels, int failed) {
    }

    private final HotelRepository hotelRepository;
    private final InventoryRepository inventoryRepository;
    private final PriceWriter priceWriter;
    private final PricingService pricingService;
    private final TransactionTemplate newTransaction;
    private final Clock clock;
    private final int hotelBatchSize;

    public PricingUpdateService(HotelRepository hotelRepository, InventoryRepository inventoryRepository,
                                PriceWriter priceWriter, PricingService pricingService,
                                PlatformTransactionManager transactionManager, Clock clock,
                                @Value("${airstay.pricing.refresh-batch-size:100}") int hotelBatchSize) {
        this.hotelRepository = hotelRepository;
        this.inventoryRepository = inventoryRepository;
        this.priceWriter = priceWriter;
        this.pricingService = pricingService;
        // Always a NEW transaction: also correct inside afterCommit, where the committed one is still bound to the thread.
        this.newTransaction = new TransactionTemplate(transactionManager);
        this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.clock = clock;
        this.hotelBatchSize = hotelBatchSize;
    }

    /**
     * Each hotel is priced in its own short transaction, so bookings are never blocked for the whole run. Hourly by
     * default; {@code PRICING_REFRESH_CRON} changes that (the load tests run it every minute to time it).
     */
    @Scheduled(cron = "${airstay.pricing.refresh-cron:0 0 * * * *}")
    public RefreshResult updatePrices() {
        long startedAt = System.nanoTime();
        int hotels = 0;
        int failed = 0;
        int page = 0;
        Slice<Long> hotelIds;
        do {
            hotelIds = hotelRepository.findAllIds(PageRequest.of(page, hotelBatchSize));
            for (Long hotelId : hotelIds.getContent()) {
                hotels++;
                try {
                    newTransaction.executeWithoutResult(status -> updateHotelPrices(hotelId));
                } catch (RuntimeException e) {
                    failed++;
                    log.error("Price update failed for hotel {}; will retry next run", hotelId, e);
                }
            }
            page++;
        } while (hotelIds.hasNext());
        log.info("Price refresh finished: {} hotels ({} failed) in {} ms", hotels, failed,
                (System.nanoTime() - startedAt) / 1_000_000);
        return new RefreshResult(hotels, failed);
    }

    /**
     * Refreshes in a new transaction once the caller's transaction has committed (or right away if there is none). Use it
     * after changes that lock inventory rows. If the refresh fails the change stays committed and the hourly refresh
     * corrects the prices, instead of the owner's change failing because of a price update.
     */
    public void refreshAfterCommit(Long hotelId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    refreshInNewTransaction(hotelId);
                }
            });
        } else {
            refreshInNewTransaction(hotelId);
        }
    }

    private void refreshInNewTransaction(Long hotelId) {
        try {
            newTransaction.executeWithoutResult(status -> updateHotelPrices(hotelId));
        } catch (RuntimeException e) {
            log.error("Price refresh after a change failed for hotel {}; the hourly refresh will correct the prices",
                    hotelId, e);
        }
    }

    /**
     * Recomputes stored nightly prices and the hotel's daily "from" price, inside the caller's transaction. Used when the
     * caller has only just created the hotel's inventory (activation, a new room), which nobody else can see or lock yet.
     */
    @Transactional
    public void updateHotelPrices(Hotel hotel) {
        updateHotelPrices(hotel.getId());
    }

    @Transactional
    public void updateHotelPrices(Long hotelId) {
        priceWriter.lockHotelForPriceRefresh(hotelId); // BEFORE reading: see PriceWriter
        LocalDate startDate = LocalDate.now(clock);
        LocalDate endDate = startDate.plusYears(1);
        LocalDateTime now = LocalDateTime.now(clock);

        List<Inventory> nights = inventoryRepository.findForPricing(hotelId, startDate, endDate);

        List<Long> changedIds = new ArrayList<>();
        List<BigDecimal> changedPrices = new ArrayList<>();
        // Lowest room price per night for the hotel (sorted by date)
        Map<LocalDate, BigDecimal> cheapestByDate = new TreeMap<>();
        for (Inventory night : nights) {
            BigDecimal price = pricingService.calculateDynamicPricing(night);
            if (night.getPrice().compareTo(price) != 0) {
                changedIds.add(night.getId());
                changedPrices.add(price);
            }
            cheapestByDate.merge(night.getDate(), price, BigDecimal::min);
        }

        // Only the price is written, in set-based statements, so booking counts are never touched.
        int pricesChanged = changedIds.isEmpty() ? 0 : priceWriter.updateInventoryPrices(changedIds, changedPrices, now);
        int fromPricesWritten = cheapestByDate.isEmpty() ? 0 : priceWriter.upsertHotelMinPrices(hotelId, cheapestByDate, now);
        log.debug("Hotel {}: {} nights read, {} nightly prices and {} 'from' prices written",
                hotelId, nights.size(), pricesChanged, fromPricesWritten);
    }
}
