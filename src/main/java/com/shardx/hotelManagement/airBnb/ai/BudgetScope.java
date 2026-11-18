package com.shardx.hotelManagement.airBnb.ai;

/** Budget needs an explicit basis: a total is not interchangeable with one room for one night. */
public enum BudgetScope {
    TOTAL_STAY,
    PER_ROOM_PER_NIGHT
}
