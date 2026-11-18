import { addDays, today } from '@/shared/lib/stay';

import { hotelSchema, toHotelRequest } from './hotelSchema';
import { inventorySchema } from './inventorySchema';
import { roomSchema } from './roomSchema';

const amenities = ['WiFi', 'Pool', 'Spa'];
const roomTypes = ['Standard', 'Deluxe', 'Suite'];
const roomAmenities = ['Balcony', 'TV'];

const messagesOf = (result: { error?: { issues: { message: string; path: PropertyKey[] }[] } }) =>
  result.error?.issues.map((issue) => `${issue.path.join('.')}: ${issue.message}`) ?? [];

describe('hotelSchema', () => {
  const schema = hotelSchema(amenities);
  const valid = {
    name: 'Lakeview Inn',
    placeLabel: 'Pune, Maharashtra, India',
    placeId: '1259229',
    amenities: ['WiFi'],
    photos: ['https://images.example.com/a.jpg'],
    address: '12 MG Road',
    phoneNumber: '+91 98765 43210',
    email: 'front@lakeview.example',
  };

  it('accepts a complete hotel', () => {
    expect(schema.safeParse(valid).success).toBe(true);
  });

  it('accepts a hotel with only the required details', () => {
    expect(
      schema.safeParse({ ...valid, amenities: [], photos: [], address: '', phoneNumber: '', email: '' })
        .success,
    ).toBe(true);
  });

  it('needs a place that was chosen from the list', () => {
    expect(messagesOf(schema.safeParse({ ...valid, placeLabel: '', placeId: '' }))).toEqual([
      'placeLabel: Choose a place from the list.',
      'placeId: Choose a place from the list.',
    ]);
  });

  it('needs a name of 2 to 100 characters', () => {
    expect(messagesOf(schema.safeParse({ ...valid, name: ' a ' }))).toEqual([
      'name: Use at least 2 characters.',
    ]);
    expect(messagesOf(schema.safeParse({ ...valid, name: 'a'.repeat(101) }))).toEqual([
      'name: Use at most 100 characters.',
    ]);
  });

  it('only allows amenities from the list', () => {
    expect(messagesOf(schema.safeParse({ ...valid, amenities: ['WiFi', 'Helipad'] }))).toEqual([
      'amenities: Choose from the list.',
    ]);
  });

  it.each(['http://images.example.com/a.jpg', 'javascript:alert(1)', 'not a url'])(
    'refuses the photo %s',
    (photo) => {
      expect(messagesOf(schema.safeParse({ ...valid, photos: [photo] }))).toEqual([
        'photos.0: Upload a photo or enter an HTTPS address.',
      ]);
    },
  );

  it('allows at most ten photos', () => {
    const photos = Array.from({ length: 11 }, (_, i) => `https://images.example.com/${String(i)}.jpg`);

    expect(messagesOf(schema.safeParse({ ...valid, photos }))).toEqual(['photos: Add at most 10 photos.']);
  });

  it.each(['call me', '12345', '+', '98765abc43'])('refuses the phone number %j', (phoneNumber) => {
    expect(messagesOf(schema.safeParse({ ...valid, phoneNumber }))).toEqual([
      'phoneNumber: Enter a phone number such as +91 98765 43210.',
    ]);
  });

  it.each(['+91 98765 43210', '(022) 2345-6789', '1234567'])('accepts the phone number %j', (phoneNumber) => {
    expect(schema.safeParse({ ...valid, phoneNumber }).success).toBe(true);
  });

  it('refuses an invalid email', () => {
    expect(schema.safeParse({ ...valid, email: 'not-an-email' }).success).toBe(false);
  });

  describe('toHotelRequest', () => {
    it('sends the place id, never a typed city', () => {
      const request = toHotelRequest(schema.parse(valid));

      expect(request.placeId).toBe(1259229);
      expect(request).not.toHaveProperty('city');
    });

    it('leaves out contact details that were left empty', () => {
      const request = toHotelRequest(schema.parse({ ...valid, address: '', phoneNumber: '', email: '' }));

      expect(request).not.toHaveProperty('contactInfo');
    });

    it('sends only the contact details that were filled in', () => {
      const request = toHotelRequest(
        schema.parse({ ...valid, address: '', phoneNumber: '', email: 'a@b.co' }),
      );

      expect(request.contactInfo).toEqual({ email: 'a@b.co' });
    });
  });
});

describe('roomSchema', () => {
  const schema = roomSchema(roomTypes, roomAmenities);
  const valid = { type: 'Deluxe', basePrice: 2500, totalCount: 5, capacity: 2, amenities: ['Balcony'] };

  it('accepts a valid room', () => {
    expect(schema.safeParse(valid).success).toBe(true);
  });

  it('only allows room types from the list', () => {
    expect(messagesOf(schema.safeParse({ ...valid, type: 'Penthouse' }))).toEqual([
      'type: Choose a room type from the list.',
    ]);
  });

  it('keeps the price positive, within range and in whole paise', () => {
    expect(schema.safeParse({ ...valid, basePrice: 0.01 }).success).toBe(true);
    expect(schema.safeParse({ ...valid, basePrice: 1999.99 }).success).toBe(true);
    expect(schema.safeParse({ ...valid, basePrice: 0 }).success).toBe(false);
    expect(schema.safeParse({ ...valid, basePrice: -5 }).success).toBe(false);
    expect(schema.safeParse({ ...valid, basePrice: 100_000_000 }).success).toBe(false);
    expect(messagesOf(schema.safeParse({ ...valid, basePrice: 10.005 }))).toEqual([
      'basePrice: Use at most two decimal places.',
    ]);
  });

  it('asks for a price when the field is empty', () => {
    expect(messagesOf(schema.safeParse({ ...valid, basePrice: Number.NaN }))).toEqual([
      'basePrice: Enter a price.',
    ]);
  });

  it('keeps the room count between 1 and 1000, whole numbers', () => {
    expect(schema.safeParse({ ...valid, totalCount: 1000 }).success).toBe(true);
    expect(schema.safeParse({ ...valid, totalCount: 0 }).success).toBe(false);
    expect(schema.safeParse({ ...valid, totalCount: 1001 }).success).toBe(false);
    expect(schema.safeParse({ ...valid, totalCount: 2.5 }).success).toBe(false);
  });

  it('keeps the capacity between 1 and 20 guests', () => {
    expect(schema.safeParse({ ...valid, capacity: 20 }).success).toBe(true);
    expect(schema.safeParse({ ...valid, capacity: 0 }).success).toBe(false);
    expect(schema.safeParse({ ...valid, capacity: 21 }).success).toBe(false);
  });

  it('only allows room amenities from the list', () => {
    expect(schema.safeParse({ ...valid, amenities: ['Pool'] }).success).toBe(false);
  });
});

describe('inventorySchema', () => {
  const now = new Date(2026, 10, 17);
  const schema = inventorySchema(now);
  const valid = { startDate: '2026-12-20', endDate: '2026-12-24', surgeFactor: 1.25, closed: false };

  it('accepts a valid change', () => {
    expect(schema.safeParse(valid).success).toBe(true);
  });

  it('accepts a single night', () => {
    expect(schema.safeParse({ ...valid, endDate: '2026-12-20' }).success).toBe(true);
  });

  it('does not allow changing nights in the past', () => {
    expect(messagesOf(schema.safeParse({ ...valid, startDate: '2026-11-16' }))).toEqual([
      'startDate: Nights in the past cannot be changed.',
    ]);
  });

  it('does not allow the last night before the first', () => {
    expect(messagesOf(schema.safeParse({ ...valid, endDate: '2026-12-19' }))).toEqual([
      'endDate: The last night cannot be before the first.',
    ]);
  });

  it.each([
    [0.5, true],
    [5, true],
    [0.49, false],
    [5.01, false],
    [1.234, false],
  ])('the price factor %d is valid: %s', (surgeFactor, ok) => {
    expect(schema.safeParse({ ...valid, surgeFactor }).success).toBe(ok);
  });

  it('uses today by default', () => {
    expect(
      inventorySchema().safeParse({ ...valid, startDate: today(), endDate: addDays(today(), 1) }).success,
    ).toBe(true);
  });
});
