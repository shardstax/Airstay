package com.shardx.hotelManagement.airBnb.catalog;

import com.shardx.hotelManagement.airBnb.dto.HotelDto;
import com.shardx.hotelManagement.airBnb.dto.RoomDto;
import com.shardx.hotelManagement.airBnb.entity.HotelContactInfo;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** The fixed choices and input formats a hotel manager is held to, checked on the real request objects. */
class CatalogValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private HotelDto validHotel() {
        HotelDto hotel = new HotelDto();
        hotel.setName("Lakeview Inn");
        hotel.setPlaceId(1259229L);
        hotel.setAmenities(new String[]{"WiFi", "Pool"});
        hotel.setPhotos(new String[]{"https://images.example.com/a.jpg"});
        return hotel;
    }

    private RoomDto validRoom() {
        RoomDto room = new RoomDto();
        room.setType("Deluxe");
        room.setBasePrice(new BigDecimal("2500.00"));
        room.setTotalCount(5);
        room.setCapacity(2);
        room.setAmenities(new String[]{"Balcony", "TV"});
        return room;
    }

    private static Set<String> problemsWith(Set<? extends ConstraintViolation<?>> violations) {
        return violations.stream().map(v -> v.getPropertyPath().toString()).collect(java.util.stream.Collectors.toSet());
    }

    // ---- hotel ---------------------------------------------------------------------------------------------

    @Test
    void acceptsAValidHotel() {
        assertThat(validator.validate(validHotel())).isEmpty();
    }

    @Test
    void aHotelNeedsAPlace() {
        HotelDto hotel = validHotel();
        hotel.setPlaceId(null);

        assertThat(problemsWith(validator.validate(hotel))).containsExactly("placeId");
    }

    @Test
    void theCityCannotBeTypedIn() {
        // city is read-only and filled from the place: whatever a client sends there is not validated, and never stored
        HotelDto hotel = validHotel();
        hotel.setCity("Anywhere I like");

        assertThat(validator.validate(hotel)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "A", "x"})
    void aHotelNameNeedsAtLeastTwoCharacters(String name) {
        HotelDto hotel = validHotel();
        hotel.setName(name);

        assertThat(problemsWith(validator.validate(hotel))).containsExactly("name");
    }

    @Test
    void aHotelNameIsLimitedTo100Characters() {
        HotelDto hotel = validHotel();
        hotel.setName("a".repeat(101));

        assertThat(problemsWith(validator.validate(hotel))).containsExactly("name");
    }

    @Test
    void amenitiesMustComeFromTheList() {
        HotelDto hotel = validHotel();
        hotel.setAmenities(new String[]{"WiFi", "Helipad"});

        assertThat(problemsWith(validator.validate(hotel))).containsExactly("amenities");
    }

    @Test
    void amenityNamesMustMatchExactly() {
        HotelDto hotel = validHotel();
        hotel.setAmenities(new String[]{"wifi"});

        assertThat(problemsWith(validator.validate(hotel))).containsExactly("amenities");
    }

    @Test
    void everyAmenityInTheCatalogIsAccepted() {
        HotelDto hotel = validHotel();
        hotel.setAmenities(Catalog.HOTEL_AMENITIES.toArray(String[]::new));

        assertThat(validator.validate(hotel)).isEmpty();
    }

    @Test
    void theValidationMessageDoesNotRepeatWhatTheClientSent() {
        HotelDto hotel = validHotel();
        hotel.setAmenities(new String[]{"${7*7}"});

        assertThat(validator.validate(hotel)).extracting(ConstraintViolation::getMessage)
                .allSatisfy(message -> assertThat(message).doesNotContain("${").doesNotContain("49"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://images.example.com/a.jpg", "javascript:alert(1)", "data:image/png;base64,AAAA",
            "file:///etc/passwd", "not a url", "https://", "//images.example.com/a.jpg", ""})
    void photosMustBeHttpsAddresses(String photo) {
        HotelDto hotel = validHotel();
        hotel.setPhotos(new String[]{photo});

        assertThat(problemsWith(validator.validate(hotel))).containsExactly("photos");
    }

    @Test
    void aHotelMayHaveAtMostTenPhotos() {
        HotelDto hotel = validHotel();
        hotel.setPhotos(java.util.Collections.nCopies(11, "https://images.example.com/a.jpg").toArray(String[]::new));

        assertThat(problemsWith(validator.validate(hotel))).containsExactly("photos");
    }

    @Test
    void aPhotoAddressIsLimitedTo2048Characters() {
        HotelDto hotel = validHotel();
        hotel.setPhotos(new String[]{"https://images.example.com/" + "a".repeat(2048)});

        assertThat(problemsWith(validator.validate(hotel))).containsExactly("photos");
    }

    // ---- contact details -----------------------------------------------------------------------------------

    private static HotelContactInfo contact(String phone, String email) {
        HotelContactInfo info = new HotelContactInfo();
        info.setPhoneNumber(phone);
        info.setEmail(email);
        return info;
    }

    @ParameterizedTest
    @ValueSource(strings = {"+91 98765 43210", "+919876543210", "022 2345 6789", "(022) 2345-6789", "1234567"})
    void acceptsNormalPhoneNumbers(String phone) {
        HotelDto hotel = validHotel();
        hotel.setContactInfo(contact(phone, "front@desk.example"));

        assertThat(validator.validate(hotel)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"call me", "12345", "+", "98765abc43", "<script>", "+91 98765 43210 98765 43210"})
    void refusesAnythingThatIsNotAPhoneNumber(String phone) {
        HotelDto hotel = validHotel();
        hotel.setContactInfo(contact(phone, null));

        assertThat(problemsWith(validator.validate(hotel))).containsExactly("contactInfo.phoneNumber");
    }

    @ParameterizedTest
    @ValueSource(strings = {"not-an-email", "a@", "@b.com", "a b@c.com"})
    void refusesAnInvalidEmail(String email) {
        HotelDto hotel = validHotel();
        hotel.setContactInfo(contact(null, email));

        assertThat(problemsWith(validator.validate(hotel))).containsExactly("contactInfo.email");
    }

    @Test
    void contactDetailsAreOptional() {
        HotelDto hotel = validHotel();
        hotel.setContactInfo(contact(null, null));

        assertThat(validator.validate(hotel)).isEmpty();
    }

    // ---- rooms ---------------------------------------------------------------------------------------------

    @Test
    void acceptsAValidRoom() {
        assertThat(validator.validate(validRoom())).isEmpty();
    }

    @Test
    void everyRoomTypeInTheCatalogIsAccepted() {
        for (String type : Catalog.ROOM_TYPES) {
            RoomDto room = validRoom();
            room.setType(type);
            assertThat(validator.validate(room)).as(type).isEmpty();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"Penthouse", "deluxe", "Room type 0", "<b>Suite</b>"})
    void aRoomTypeMustComeFromTheList(String type) {
        RoomDto room = validRoom();
        room.setType(type);

        assertThat(problemsWith(validator.validate(room))).containsExactly("type");
    }

    @Test
    void roomAmenitiesMustComeFromTheirOwnList() {
        RoomDto room = validRoom();
        room.setAmenities(new String[]{"Pool"}); // a hotel amenity, not a room one

        assertThat(problemsWith(validator.validate(room))).containsExactly("amenities");
    }

    @Test
    void theRoomNumbersStayWithinSensibleRanges() {
        RoomDto room = validRoom();
        room.setBasePrice(new BigDecimal("0.00"));
        room.setTotalCount(0);
        room.setCapacity(21);

        assertThat(problemsWith(validator.validate(room))).containsExactlyInAnyOrder("basePrice", "totalCount", "capacity");
    }
}
