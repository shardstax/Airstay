# AirStay events (Kafka)

AirStay publishes what happens to a booking as events on Kafka, so other services can react without calling the API.
This document is the contract. **Only ever add fields; never rename or remove one.**

## How an event gets to Kafka (and why it cannot be lost)

1. The booking changes (for example it is confirmed) **and** the event is saved in the `outbox_event` table, in the same
   database transaction. Both are committed or neither is.
2. `OutboxRelay` reads unpublished rows in order, publishes them and marks them published once Kafka acknowledged them.
3. If Kafka is down, bookings keep working and events wait in the table; they are published when Kafka returns.

Delivery is **at-least-once**: an event can be delivered twice (for example after a crash between publishing and
marking). Consumers must ignore an `eventId` they have already handled.

## Topics

| Topic | Key | Contents |
|---|---|---|
| `airstay.booking-events` | booking id | Facts about a booking: `BOOKING_CONFIRMED`, `BOOKING_CANCELLED`, `BOOKING_EXPIRED`, `REFUND_COMPLETED` |
| `airstay.refund-requests` | booking id | Work for AirStay's own refund worker: `REFUND_REQUESTED` (carries the payment session) |
| `airstay.refund-requests-retry*` | booking id | Internal: refund attempts waiting to be retried |
| `airstay.refund-requests.DLT` | booking id | Refund requests that still failed after every attempt: **needs a human** |

The key is the booking id, so all events of one booking go to the same partition and stay in order.
Each Kafka message also has the headers `event-id` and `event-type`.

## Message (JSON)

```json
{
  "eventId": "6f1c6f0a-4f6e-4b53-9b1e-2f3d3b7a9c10",
  "type": "BOOKING_CONFIRMED",
  "occurredAt": "2026-11-17T10:00:00",
  "bookingId": 99,
  "userId": 7,
  "userEmail": "asha@example.com",
  "userName": "Asha",
  "hotelName": "Sea View Resort",
  "roomType": "Deluxe",
  "checkInDate": "2026-12-01",
  "checkOutDate": "2026-12-03",
  "roomsCount": 2,
  "amount": 4000.00,
  "paymentSessionId": null
}
```

| Field | Meaning |
|---|---|
| `eventId` | Unique per event. Use it to ignore duplicates. A `REFUND_COMPLETED` event's id is derived from the refund request that caused it, so a redelivered request can never produce a second one. |
| `type` | One of the types above. |
| `occurredAt` | When the event was recorded (server time, ISO-8601, no zone). |
| `bookingId`, `userId`, `userEmail`, `userName` | Who the booking belongs to. |
| `hotelName`, `roomType`, `checkInDate`, `checkOutDate`, `roomsCount`, `amount` | A snapshot of the booking, so a consumer never has to call back. `checkOutDate` is the departure day (not a night of the stay). |
| `paymentSessionId` | **Only on `REFUND_REQUESTED`.** It is null on every event in the public topic. |

## Events

| Type | When | Typical use |
|---|---|---|
| `BOOKING_CONFIRMED` | Payment succeeded and the rooms are booked | Confirmation e-mail |
| `BOOKING_CANCELLED` | A guest cancelled a confirmed booking | Cancellation e-mail |
| `BOOKING_EXPIRED` | An unpaid hold lapsed or its payment session expired | "Your hold expired" notice |
| `REFUND_REQUESTED` | A refund is needed (cancellation, or payment that arrived for an expired booking) | Consumed by the refund worker only |
| `REFUND_COMPLETED` | The refund was issued | "Your refund is on its way" e-mail |

## Refunds: retries and the dead-letter topic

The refund worker retries a failed refund through retry topics (non-blocking, so a Stripe outage does not stall other
refunds): 6 attempts, delays 10 s, 30 s, 90 s, 270 s, 600 s (about 17 minutes), then the message is published to
`airstay.refund-requests.DLT` and `REFUND_DEAD_LETTER` is logged at ERROR. Stripe is called with an idempotency key per
booking, so repeating an attempt that already succeeded changes nothing. Unreadable or incomplete requests skip the
retries and go straight to the dead-letter topic.

To look at dead letters:

```bash
docker compose exec kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 \
  --topic airstay.refund-requests.DLT --from-beginning --property print.headers=true
```

To retry one after fixing the cause, publish its payload again to `airstay.refund-requests` with the booking id as key.
