#!/usr/bin/env bash
# Event path smoke test against a RUNNING Docker Compose stack (docker compose up --build -d --wait).
#
# Proves the whole chain with real components: booking -> signed Stripe webhook -> outbox -> Kafka -> notification
# service -> SMTP -> Mailpit. It confirms a booking and waits for the confirmation e-mail, then cancels it and waits
# for the cancellation e-mail. Run it from the repository root (it uses `docker compose exec` for one SQL statement).
#
# Needs: bash, curl, openssl, node (only to read JSON). Settings (all optional):
#   API=http://localhost:8080/api/v1   MAILPIT=http://localhost:8025   STRIPE_WEBHOOK_SECRET=whsec_not_configured
#
# Why one SQL statement: starting a Stripe Checkout session needs a real Stripe key. Instead the script puts the booking
# in the state "payment pending with a Stripe session" (what a started checkout would do) and then confirms it with a
# webhook signed exactly like Stripe signs it, so the real webhook code path runs.
#
# The steps are functions so that other scripts can `source` this file and combine them (for example to stop Kafka
# between creating a booking and confirming it).
set -euo pipefail

API="${API:-http://localhost:8080/api/v1}"
MAILPIT="${MAILPIT:-http://localhost:8025}"
WEBHOOK_SECRET="${STRIPE_WEBHOOK_SECRET:-whsec_not_configured}"
PASSWORD="Password123!"

# json '<javascript expression over o>' < document
json() {
  node -e "let d='';process.stdin.on('data',c=>d+=c).on('end',()=>{const o=JSON.parse(d);console.log(Function('o','return ('+process.argv[1]+')')(o))})" "$1"
}

# call METHOD PATH [TOKEN] [JSON_BODY]
call() {
  local method="$1" path="$2" token="${3:-}" body="${4:-}"
  local args=(-fsS -X "$method" "$API$path" -H 'Content-Type: application/json')
  [ -n "$token" ] && args+=(-H "Authorization: Bearer $token")
  [ -n "$body" ] && args+=(-d "$body")
  curl "${args[@]}"
}

# wait_for_email RECIPIENT SUBJECT_FRAGMENT: polls Mailpit until a matching e-mail exists (up to 90 s), prints its id
wait_for_email() {
  local recipient="$1" fragment="$2" found
  for _ in $(seq 1 45); do
    found="$(curl -fsS -G "$MAILPIT/api/v1/search" --data-urlencode "query=to:$recipient" |
      json "(o.messages||[]).filter(m=>m.Subject.includes('$fragment')).map(m=>m.ID)[0]||''")"
    if [ -n "$found" ]; then echo "$found"; return 0; fi
    sleep 2
  done
  echo "TIMEOUT: no e-mail to $recipient with subject containing '$fragment'" >&2
  return 1
}

# count_emails RECIPIENT SUBJECT_FRAGMENT: how many matching e-mails Mailpit holds right now
count_emails() {
  curl -fsS -G "$MAILPIT/api/v1/search" --data-urlencode "query=to:$1" |
    json "(o.messages||[]).filter(m=>m.Subject.includes('$2')).length"
}

# setup_booking: a hotel with one room, a guest, and a held booking with guests added.
# Sets RUN, GUEST, GUEST_TOKEN, HOTEL, ROOM, BOOKING.
setup_booking() {
  RUN="$(date +%s)$RANDOM"
  local manager="manager-$RUN@example.test" manager_token saved_guest check_in check_out
  GUEST="guest-$RUN@example.test"
  call POST /auth/signup "" "{\"email\":\"$manager\",\"password\":\"$PASSWORD\",\"name\":\"Smoke Manager\",\"accountType\":\"HOTEL_MANAGER\"}" >/dev/null
  manager_token="$(call POST /auth/login "" "{\"email\":\"$manager\",\"password\":\"$PASSWORD\"}" | json 'o.data.accessToken')"
  HOTEL="$(call POST /admin/hotels "$manager_token" "{\"name\":\"Smoke Test Hotel $RUN\",\"city\":\"Smoke-$RUN\"}" | json 'o.data.id')"
  ROOM="$(call POST "/admin/hotels/$HOTEL/rooms" "$manager_token" '{"type":"Deluxe","basePrice":2500,"totalCount":1,"capacity":2}' | json 'o.data.id')"
  call PATCH "/admin/hotels/$HOTEL/activate" "$manager_token" >/dev/null
  call POST /auth/signup "" "{\"email\":\"$GUEST\",\"password\":\"$PASSWORD\",\"name\":\"Smoke Guest\"}" >/dev/null
  GUEST_TOKEN="$(call POST /auth/login "" "{\"email\":\"$GUEST\",\"password\":\"$PASSWORD\"}" | json 'o.data.accessToken')"
  saved_guest="$(call POST /users/guests "$GUEST_TOKEN" '{"name":"Smoke Traveller"}' | json 'o.data.id')"
  check_in="$(date -d '+30 days' +%F 2>/dev/null || date -v+30d +%F)"
  check_out="$(date -d '+32 days' +%F 2>/dev/null || date -v+32d +%F)"
  BOOKING="$(call POST /bookings/init "$GUEST_TOKEN" \
    "{\"hotelId\":$HOTEL,\"roomId\":$ROOM,\"checkInDate\":\"$check_in\",\"checkOutDate\":\"$check_out\",\"roomsCount\":1}" | json 'o.data.id')"
  call POST "/bookings/$BOOKING/addGuests" "$GUEST_TOKEN" "[$saved_guest]" >/dev/null
  echo "hotel=$HOTEL room=$ROOM booking=$BOOKING stay=$check_in..$check_out guest=$GUEST"
}

# make_payment_pending: the state a started Stripe checkout leaves the booking in (needs no real Stripe key)
make_payment_pending() {
  docker compose exec -T postgres psql -U airstay -d airstay -qAt -c \
    "UPDATE booking SET booking_status='PAYMENTS_PENDING', payment_session_id='cs_smoke_$RUN', expires_at=now()+interval '30 minutes' WHERE id=$BOOKING AND booking_status='GUESTS_ADDED'" >/dev/null
}

# send_paid_webhook: checkout.session.completed, signed exactly like Stripe signs it. Fails unless the API answers 204.
send_paid_webhook() {
  local payload ts signature status
  payload="$(node -e 'const run=process.argv[1];console.log(JSON.stringify({id:"evt_smoke_"+run,object:"event",api_version:"2025-03-31.basil",created:Math.floor(Date.now()/1000),livemode:false,type:"checkout.session.completed",data:{object:{id:"cs_smoke_"+run,object:"checkout.session",payment_status:"paid",status:"complete"}}}))' "$RUN")"
  ts="$(date +%s)"
  signature="$(printf '%s' "$ts.$payload" | openssl dgst -sha256 -hmac "$WEBHOOK_SECRET" | sed 's/^.* //')"
  status="$(curl -sS -o /dev/null -w '%{http_code}' -X POST "$API/webhook/payment" \
    -H 'Content-Type: application/json' -H "Stripe-Signature: t=$ts,v1=$signature" --data-binary "$payload")"
  [ "$status" = "204" ] || { echo "FAIL: webhook answered HTTP $status (expected 204)" >&2; return 1; }
}

booking_status() {
  call GET "/bookings/$BOOKING/status" "$GUEST_TOKEN" | json 'o.data.bookingStatus'
}

# outbox_waiting: events saved in PostgreSQL but not yet published to Kafka
outbox_waiting() {
  docker compose exec -T postgres psql -U airstay -d airstay -qAt -c "SELECT count(*) FROM outbox_event WHERE published_at IS NULL"
}

# check_confirmation_email: the e-mail names this booking, the hotel and the amount
check_confirmation_email() {
  local message_id body
  message_id="$(wait_for_email "$GUEST" "confirmed")"
  body="$(curl -fsS "$MAILPIT/api/v1/message/$message_id" | json 'o.Text')"
  echo "$body" | grep -q "Booking:    #$BOOKING" || { echo "FAIL: e-mail does not mention booking #$BOOKING" >&2; return 1; }
  echo "$body" | grep -q "Smoke Test Hotel $RUN" || { echo "FAIL: e-mail does not mention the hotel" >&2; return 1; }
  echo "$body" | grep -q "Amount:     INR 5000.00" || { echo "FAIL: e-mail has the wrong amount" >&2; return 1; }
}

cancel_booking() {
  local status
  status="$(curl -sS -o /dev/null -w '%{http_code}' -X POST "$API/bookings/$BOOKING/cancel" -H "Authorization: Bearer $GUEST_TOKEN")"
  [ "$status" = "204" ] || { echo "FAIL: cancel answered HTTP $status (expected 204)" >&2; return 1; }
}

main() {
  echo "== set up: manager, hotel, room, guest, held booking =="
  setup_booking

  echo "== payment pending with a Stripe session, then the signed Stripe webhook =="
  make_payment_pending
  send_paid_webhook
  [ "$(booking_status)" = "CONFIRMED" ] || { echo "FAIL: booking is not CONFIRMED" >&2; exit 1; }
  echo "webhook accepted (204), booking is CONFIRMED"

  echo "== confirmation e-mail (outbox -> Kafka -> notification service -> Mailpit) =="
  check_confirmation_email
  echo "confirmation e-mail received with the right booking, hotel and amount"

  echo "== cancel: cancellation e-mail =="
  cancel_booking
  wait_for_email "$GUEST" "was cancelled" >/dev/null
  echo "cancellation e-mail received"

  echo
  echo "OK: confirmation and cancellation e-mails arrived for booking $BOOKING (inbox: $MAILPIT)"
}

# Run only when executed, not when another script sources the functions.
if [ "${BASH_SOURCE[0]}" = "$0" ]; then
  main "$@"
fi
