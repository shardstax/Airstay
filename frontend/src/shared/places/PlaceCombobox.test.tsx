import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useState } from 'react';

import { mockApi } from '@/test/mockApi';
import { renderRoute } from '@/test/renderRoute';

import { PlaceCombobox } from './PlaceCombobox';
import type { Place } from './usePlaceSuggestions';

const place = (id: number, name: string, region: string, country: string): Place => ({
  id,
  name,
  region,
  country,
  countryCode: 'XX',
  latitude: 1,
  longitude: 2,
  label: `${name}, ${region}, ${country}`,
});
const PUNE = place(1259229, 'Pune', 'Maharashtra', 'India');
const PUNO = place(3931276, 'Puno', 'Puno', 'Peru');

/** A parent that keeps the chosen label, as a form would. */
function Harness({
  initial = '',
  onChoose,
}: {
  initial?: string;
  onChoose?: (label: string, p?: Place) => void;
}) {
  const [value, setValue] = useState(initial);
  return (
    <>
      <PlaceCombobox
        label="City"
        value={value}
        onChange={(label, chosen) => {
          setValue(label);
          onChoose?.(label, chosen);
        }}
      />
      <p data-testid="chosen">{value}</p>
    </>
  );
}

const suggestions = { 'GET /places': { data: [PUNE, PUNO] } };

describe('the place picker', () => {
  it('does not search until two letters are typed', async () => {
    const backend = mockApi(suggestions);
    const user = userEvent.setup();
    renderRoute(<Harness />);

    await user.type(screen.getByRole('combobox', { name: 'City' }), 'p');

    expect(screen.queryByRole('listbox')).not.toBeInTheDocument();
    expect(backend.calls).toHaveLength(0);
  });

  it('shows real places after a pause in typing, and asks only once for a quick burst of typing', async () => {
    const backend = mockApi(suggestions);
    const user = userEvent.setup();
    renderRoute(<Harness />);

    await user.type(screen.getByRole('combobox', { name: 'City' }), 'pune');

    expect(await screen.findByRole('option', { name: 'Pune, Maharashtra, India' })).toBeInTheDocument();
    expect(screen.getByRole('option', { name: 'Puno, Puno, Peru' })).toBeInTheDocument();
    expect(backend.calls.map((call) => call.query.get('query'))).toEqual(['pune']);
  });

  it('chooses a place with the mouse', async () => {
    mockApi(suggestions);
    const onChoose = vi.fn();
    const user = userEvent.setup();
    renderRoute(<Harness onChoose={onChoose} />);

    await user.type(screen.getByRole('combobox', { name: 'City' }), 'pu');
    await user.click(await screen.findByRole('option', { name: 'Puno, Puno, Peru' }));

    expect(screen.getByRole('combobox', { name: 'City' })).toHaveValue('Puno, Puno, Peru');
    expect(screen.getByTestId('chosen')).toHaveTextContent('Puno, Puno, Peru');
    expect(onChoose).toHaveBeenLastCalledWith('Puno, Puno, Peru', PUNO);
    expect(screen.queryByRole('listbox')).not.toBeInTheDocument();
  });

  it('chooses a place with the keyboard', async () => {
    mockApi(suggestions);
    const user = userEvent.setup();
    renderRoute(<Harness />);

    await user.type(screen.getByRole('combobox', { name: 'City' }), 'pu');
    await screen.findByRole('option', { name: 'Pune, Maharashtra, India' });
    await user.keyboard('{ArrowDown}{Enter}');

    expect(screen.getByTestId('chosen')).toHaveTextContent('Puno, Puno, Peru');
  });

  it('marks the highlighted option for screen readers', async () => {
    mockApi(suggestions);
    const user = userEvent.setup();
    renderRoute(<Harness />);

    const input = screen.getByRole('combobox', { name: 'City' });
    await user.type(input, 'pu');
    const first = await screen.findByRole('option', { name: 'Pune, Maharashtra, India' });

    expect(first).toHaveAttribute('aria-selected', 'true');
    expect(input).toHaveAttribute('aria-activedescendant', first.id);
    expect(input).toHaveAttribute('aria-expanded', 'true');
  });

  it('closes the list with Escape without choosing anything', async () => {
    mockApi(suggestions);
    const user = userEvent.setup();
    renderRoute(<Harness />);

    await user.type(screen.getByRole('combobox', { name: 'City' }), 'pu');
    await screen.findByRole('listbox');
    await user.keyboard('{Escape}');

    expect(screen.queryByRole('listbox')).not.toBeInTheDocument();
    expect(screen.getByTestId('chosen')).toBeEmptyDOMElement();
  });

  it('never accepts text that was not chosen from the list', async () => {
    mockApi(suggestions);
    const user = userEvent.setup();
    renderRoute(<Harness />);

    await user.type(screen.getByRole('combobox', { name: 'City' }), 'Atlantis');
    await user.tab();

    expect(screen.getByTestId('chosen')).toBeEmptyDOMElement();
  });

  it('forgets the choice as soon as the text is edited', async () => {
    mockApi(suggestions);
    const user = userEvent.setup();
    renderRoute(<Harness />);

    await user.type(screen.getByRole('combobox', { name: 'City' }), 'pu');
    await user.click(await screen.findByRole('option', { name: 'Pune, Maharashtra, India' }));
    expect(screen.getByTestId('chosen')).toHaveTextContent('Pune, Maharashtra, India');

    await user.type(screen.getByRole('combobox', { name: 'City' }), 'x');

    expect(screen.getByTestId('chosen')).toBeEmptyDOMElement();
  });

  it('shows a chosen place passed in, without opening a list', () => {
    mockApi({});
    renderRoute(<Harness initial="Pune, Maharashtra, India" />);

    expect(screen.getByRole('combobox', { name: 'City' })).toHaveValue('Pune, Maharashtra, India');
    expect(screen.queryByRole('listbox')).not.toBeInTheDocument();
  });

  it('says so when nothing matches', async () => {
    mockApi({ 'GET /places': { data: [] } });
    const user = userEvent.setup();
    renderRoute(<Harness />);

    await user.type(screen.getByRole('combobox', { name: 'City' }), 'zzzz');

    expect(await screen.findByText('No places found. Check the spelling.')).toBeInTheDocument();
  });

  it('says so when the place search is unavailable', async () => {
    mockApi({
      'GET /places': { status: 503, error: { message: 'Place search is temporarily unavailable' } },
    });
    const user = userEvent.setup();
    renderRoute(<Harness />);

    await user.type(screen.getByRole('combobox', { name: 'City' }), 'pune');

    expect(await screen.findByText(/place search is unavailable/i)).toBeInTheDocument();
  });

  it('shows an error under the field', () => {
    mockApi({});
    renderRoute(
      <PlaceCombobox
        label="City"
        value=""
        onChange={() => undefined}
        error="Choose a place from the list."
      />,
    );

    expect(screen.getByRole('combobox', { name: 'City' })).toHaveAccessibleDescription(
      'Choose a place from the list.',
    );
  });
});
