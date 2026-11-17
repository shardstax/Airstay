import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Route, Routes } from 'react-router-dom';

import { mockApi } from '@/test/mockApi';
import { renderRoute, Where } from '@/test/renderRoute';

import { HotelFormPage } from './HotelFormPage';

const catalog = {
  hotelAmenities: ['Breakfast', 'Gym', 'Pool', 'WiFi'],
  roomAmenities: ['Balcony', 'TV'],
  roomTypes: ['Standard', 'Deluxe', 'Suite'],
};
const pune = {
  id: 1259229,
  name: 'Pune',
  region: 'Maharashtra',
  country: 'India',
  countryCode: 'IN',
  latitude: 18.5,
  longitude: 73.8,
  label: 'Pune, Maharashtra, India',
};
const base = { 'GET /catalog': { data: catalog }, 'GET /places': { data: [pune] } };

function renderForm(route: string) {
  return renderRoute(
    <>
      <Routes>
        <Route path="/manage/hotels/new" element={<HotelFormPage />} />
        <Route path="/manage/hotels/:hotelId/edit" element={<HotelFormPage />} />
        <Route path="/manage/hotels/:hotelId" element={<p>The hotel dashboard</p>} />
        <Route path="/manage" element={<p>All hotels</p>} />
      </Routes>
      <Where />
    </>,
    { route },
  );
}

async function pickPune(user: ReturnType<typeof userEvent.setup>) {
  await user.type(screen.getByRole('combobox', { name: 'City' }), 'pun');
  await user.click(await screen.findByRole('option', { name: 'Pune, Maharashtra, India' }));
}

describe('adding a hotel', () => {
  it('has no free-text city: the city is chosen from real places', async () => {
    mockApi(base);
    renderForm('/manage/hotels/new');

    expect(await screen.findByRole('combobox', { name: 'City' })).toBeInTheDocument();
    expect(screen.queryByRole('textbox', { name: 'City' })).not.toBeInTheDocument();
  });

  it('creates the hotel, sending the place id (never a city) and the chosen options', async () => {
    const backend = mockApi({ ...base, 'POST /admin/hotels': { status: 201, data: { id: 9 } } });
    const user = userEvent.setup();
    renderForm('/manage/hotels/new');

    await user.type(await screen.findByLabelText('Hotel name'), 'Lakeview Inn');
    await pickPune(user);
    await user.click(screen.getByLabelText('WiFi'));
    await user.click(screen.getByLabelText('Pool'));
    await user.type(screen.getByLabelText('Photo address'), 'https://images.example.com/front.jpg');
    await user.click(screen.getByRole('button', { name: 'Add photo' }));
    await user.type(screen.getByLabelText('Phone (optional)'), '+91 98765 43210');
    await user.click(screen.getByRole('button', { name: 'Create hotel' }));

    expect(await screen.findByText('The hotel dashboard')).toBeInTheDocument();
    expect(screen.getByTestId('where')).toHaveTextContent('/manage/hotels/9');
    const body = backend.calls.find((call) => call.key === 'POST /admin/hotels')?.body;
    expect(body).toEqual({
      name: 'Lakeview Inn',
      placeId: 1259229,
      amenities: ['WiFi', 'Pool'],
      photos: ['https://images.example.com/front.jpg'],
      contactInfo: { phoneNumber: '+91 98765 43210' },
    });
    expect(body).not.toHaveProperty('city');
  });

  it('asks for a place chosen from the list before saving', async () => {
    const backend = mockApi({ ...base, 'POST /admin/hotels': { status: 201, data: { id: 9 } } });
    const user = userEvent.setup();
    renderForm('/manage/hotels/new');

    await user.type(await screen.findByLabelText('Hotel name'), 'Lakeview Inn');
    await user.type(screen.getByRole('combobox', { name: 'City' }), 'Atlantis');
    await user.click(screen.getByRole('button', { name: 'Create hotel' }));

    expect(await screen.findByText('Choose a place from the list.')).toBeInTheDocument();
    expect(backend.count('POST /admin/hotels')).toBe(0);
  });

  it('refuses a photo that is not an https address, without adding it', async () => {
    mockApi(base);
    const user = userEvent.setup();
    renderForm('/manage/hotels/new');

    await user.type(await screen.findByLabelText('Photo address'), 'http://images.example.com/front.jpg');
    await user.click(screen.getByRole('button', { name: 'Add photo' }));

    expect(await screen.findByText(/Enter a full https address/)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Remove/ })).not.toBeInTheDocument();
  });

  it('removes a photo again', async () => {
    mockApi(base);
    const user = userEvent.setup();
    renderForm('/manage/hotels/new');

    await user.type(await screen.findByLabelText('Photo address'), 'https://images.example.com/a.jpg');
    await user.click(screen.getByRole('button', { name: 'Add photo' }));
    await user.click(screen.getByRole('button', { name: 'Remove https://images.example.com/a.jpg' }));

    expect(screen.queryByText('https://images.example.com/a.jpg')).not.toBeInTheDocument();
  });

  it('refuses a phone number that is not one', async () => {
    const backend = mockApi({ ...base, 'POST /admin/hotels': { status: 201, data: { id: 9 } } });
    const user = userEvent.setup();
    renderForm('/manage/hotels/new');

    await user.type(await screen.findByLabelText('Hotel name'), 'Lakeview Inn');
    await pickPune(user);
    await user.type(screen.getByLabelText('Phone (optional)'), 'call me');
    await user.click(screen.getByRole('button', { name: 'Create hotel' }));

    expect(await screen.findByText(/Enter a phone number/)).toBeInTheDocument();
    expect(backend.count('POST /admin/hotels')).toBe(0);
  });

  it('shows the server’s reason when it refuses the hotel', async () => {
    mockApi({
      ...base,
      'POST /admin/hotels': { status: 503, error: { message: 'Place lookup is temporarily unavailable' } },
    });
    const user = userEvent.setup();
    renderForm('/manage/hotels/new');

    await user.type(await screen.findByLabelText('Hotel name'), 'Lakeview Inn');
    await pickPune(user);
    await user.click(screen.getByRole('button', { name: 'Create hotel' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Place lookup is temporarily unavailable');
  });

  it('goes back to the list on Cancel', async () => {
    mockApi(base);
    const user = userEvent.setup();
    renderForm('/manage/hotels/new');

    await user.click(await screen.findByRole('button', { name: 'Cancel' }));

    expect(await screen.findByText('All hotels')).toBeInTheDocument();
  });
});

describe('editing a hotel', () => {
  const existing = {
    id: 7,
    name: 'Lakeview Inn',
    city: 'Pune, Maharashtra, India',
    placeId: 1259229,
    amenities: ['WiFi'],
    photos: ['https://images.example.com/a.jpg'],
    contactInfo: { address: '12 MG Road', phoneNumber: '+91 98765 43210', email: 'front@lakeview.example' },
    active: true,
  };

  it('starts from what is saved', async () => {
    mockApi({ ...base, 'GET /admin/hotels/7': { data: existing } });
    renderForm('/manage/hotels/7/edit');

    expect(await screen.findByLabelText('Hotel name')).toHaveValue('Lakeview Inn');
    expect(screen.getByRole('combobox', { name: 'City' })).toHaveValue('Pune, Maharashtra, India');
    expect(screen.getByLabelText('WiFi')).toBeChecked();
    expect(screen.getByLabelText('Address (optional)')).toHaveValue('12 MG Road');
    expect(screen.getByText('https://images.example.com/a.jpg')).toBeInTheDocument();
  });

  it('saves with the same place id when the place is left alone', async () => {
    const backend = mockApi({
      ...base,
      'GET /admin/hotels/7': { data: existing },
      'PUT /admin/hotels/7': { data: existing },
    });
    const user = userEvent.setup();
    renderForm('/manage/hotels/7/edit');

    await user.clear(await screen.findByLabelText('Hotel name'));
    await user.type(screen.getByLabelText('Hotel name'), 'Lakeview Grand');
    await user.click(screen.getByRole('button', { name: 'Save changes' }));

    expect(await screen.findByText('The hotel dashboard')).toBeInTheDocument();
    const body = backend.calls.find((call) => call.key === 'PUT /admin/hotels/7')?.body;
    expect(body).toMatchObject({ name: 'Lakeview Grand', placeId: 1259229 });
    expect(body).not.toHaveProperty('city');
  });

  it('makes the owner pick a place for a hotel created before places existed', async () => {
    const legacy = { ...existing, placeId: undefined, city: 'pune' };
    const backend = mockApi({
      ...base,
      'GET /admin/hotels/7': { data: legacy },
      'PUT /admin/hotels/7': { data: existing },
    });
    const user = userEvent.setup();
    renderForm('/manage/hotels/7/edit');

    expect(await screen.findByRole('combobox', { name: 'City' })).toHaveValue('');
    await user.click(screen.getByRole('button', { name: 'Save changes' }));
    expect(await screen.findByText('Choose a place from the list.')).toBeInTheDocument();
    expect(backend.count('PUT /admin/hotels/7')).toBe(0);

    await pickPune(user);
    await user.click(screen.getByRole('button', { name: 'Save changes' }));

    expect(await screen.findByText('The hotel dashboard')).toBeInTheDocument();
  });

  it('says the hotel was not found for a hotel that is not theirs', async () => {
    mockApi({ ...base, 'GET /admin/hotels/7': { status: 403, error: { message: 'You are not the owner' } } });
    renderForm('/manage/hotels/7/edit');

    expect(await screen.findByRole('heading', { name: 'Hotel not found' })).toBeInTheDocument();
  });

  it('shows an error when the options cannot be loaded', async () => {
    mockApi({ 'GET /catalog': { status: 500, error: { message: 'Something broke' } } });
    renderForm('/manage/hotels/new');

    expect(await screen.findByRole('heading', { name: 'We could not load the form' })).toBeInTheDocument();
  });
});
