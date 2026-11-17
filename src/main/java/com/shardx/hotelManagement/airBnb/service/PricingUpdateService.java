package com.shardx.hotelManagement.airBnb.service;

import com.shardx.hotelManagement.airBnb.entity.Hotel;
import com.shardx.hotelManagement.airBnb.entity.Inventory;
import com.shardx.hotelManagement.airBnb.repository.HotelRepository;
import com.shardx.hotelManagement.airBnb.repository.InventoryRepository;
import com.shardx.hotelManagement.airBnb.repository.PriceWriter;
import com.shardx.hotelManagement.airBnb.strategy.PricingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PricingUpdateService {

    private static final int HOTEL_BATCH_SIZE = 100;

    private final HotelRepository hotelRepository;
    private final InventoryRepository inventoryRepository;
    private final PriceWriter priceWriter;
    private final PricingService pricingService;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    /**
     * Each hotel is priced in its own short transaction, so bookings are never blocked for the whole run. Hourly by
     * default; {@code PRICING_REFRESH_CRON} changes that (the load tests run it every minute to time it).
     */
    @Scheduled(cron = "${airstay.pricing.refresh-cron:0 0 * * * *}")
    public void updatePrices() {
        long startedAt = System.nanoTime();
        int hotels = 0;
        int failed = 0;
        int page = 0;
        Slice<Long> hotelIds;
        do {
            hotelIds = hotelRepository.findAllIds(PageRequest.of(page, HOTEL_BATCH_SIZE));
            for (Long hotelId : hotelIds.getContent()) {
                hotels++;
                try {
                    transactionTemplate.executeWithoutResult(status -> updateHotelPrices(hotelId));
                } catch (RuntimeException e) {
                    failed++;
                    log.error("Price update failed for hotel {}; will retry next run", hotelId, e);
                }
            }
            page++;
        } while (hotelIds.hasNext());
        log.info("Price refresh finished: {} hotels ({} failed) in {} ms", hotels, failed,
                (System.nanoTime() - startedAt) / 1_000_000);
    }

    /** Recomputes stored nightly prices and the hotel's daily "from" price. Joins the caller's transaction. */
    @Transactional
    public void updateHotelPrices(Hotel hotel) {
        updateHotelPrices(hotel.getId());
    }

    @Transactional
    public void updateHotelPrices(Long hotelId) {
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
