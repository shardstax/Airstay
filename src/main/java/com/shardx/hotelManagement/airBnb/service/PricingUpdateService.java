package com.shardx.hotelManagement.airBnb.service;

import com.shardx.hotelManagement.airBnb.entity.Hotel;
import com.shardx.hotelManagement.airBnb.entity.HotelMinPrice;
import com.shardx.hotelManagement.airBnb.entity.Inventory;
import com.shardx.hotelManagement.airBnb.repository.HotelMinPriceRepository;
import com.shardx.hotelManagement.airBnb.repository.HotelRepository;
import com.shardx.hotelManagement.airBnb.repository.InventoryRepository;
import com.shardx.hotelManagement.airBnb.strategy.PricingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Keeps stored nightly prices (inventory.price) and each hotel's daily "from" price (hotel_min_price)
 * up to date. Runs hourly, and on demand after owners change prices or surge factors.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PricingUpdateService {

    private static final int HOTEL_BATCH_SIZE = 100;

    private final HotelRepository hotelRepository;
    private final InventoryRepository inventoryRepository;
    private final HotelMinPriceRepository hotelMinPriceRepository;
    private final PricingService pricingService;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    /** Each hotel is priced in its own short transaction, so bookings are never blocked for the whole run. */
    @Scheduled(cron = "0 0 * * * *")
    public void updatePrices() {
        int page = 0;
        while (true) {
            Page<Hotel> hotelPage = hotelRepository.findAll(PageRequest.of(page, HOTEL_BATCH_SIZE));
            if (hotelPage.isEmpty()) {
                break;
            }
            for (Hotel hotel : hotelPage.getContent()) {
                try {
                    transactionTemplate.executeWithoutResult(status -> updateHotelPrices(hotel));
                } catch (RuntimeException e) {
                    log.error("Price update failed for hotel {}; will retry next run", hotel.getId(), e);
                }
            }
            page++;
        }
    }

    /** Recomputes stored nightly prices and the hotel's daily "from" price. Joins the caller's transaction. */
    @Transactional
    public void updateHotelPrices(Hotel hotel) {
        log.info("Updating hotel prices for hotel ID: {}", hotel.getId());
        LocalDate startDate = LocalDate.now(clock);
        LocalDate endDate = startDate.plusYears(1);

        List<Inventory> inventoryList = inventoryRepository.findByHotelAndDateBetween(hotel, startDate, endDate);

        updateInventoryPrices(inventoryList);
        updateHotelMinPrice(hotel, inventoryList);
    }

    private void updateHotelMinPrice(Hotel hotel, List<Inventory> inventoryList) {
        // Lowest room price per night for the hotel
        Map<LocalDate, BigDecimal> dailyMinPrices = inventoryList.stream()
                .collect(Collectors.groupingBy(
                        Inventory::getDate,
                        Collectors.mapping(Inventory::getPrice, Collectors.minBy(Comparator.naturalOrder()))
                ))
                .entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().orElse(BigDecimal.ZERO)));

        List<HotelMinPrice> hotelPrices = new ArrayList<>();
        dailyMinPrices.forEach((date, price) -> {
            HotelMinPrice hotelPrice = hotelMinPriceRepository.findByHotelAndDate(hotel, date)
                    .orElse(new HotelMinPrice(hotel, date));
            hotelPrice.setPrice(price);
            hotelPrices.add(hotelPrice);
        });

        hotelMinPriceRepository.saveAll(hotelPrices);
    }

    private void updateInventoryPrices(List<Inventory> inventoryList) {
        // Only `price` changes here; Inventory uses @DynamicUpdate so counts are never overwritten.
        inventoryList.forEach(inventory -> inventory.setPrice(pricingService.calculateDynamicPricing(inventory)));
        inventoryRepository.saveAll(inventoryList);
    }
}
