import { type KeyboardEvent, useId, useState } from 'react';

import { cn } from '@/shared/lib/cn';
import { Field } from '@/shared/ui/Field';

import { MIN_QUERY_LENGTH, type Place, usePlaceSuggestions } from './usePlaceSuggestions';

interface PlaceComboboxProps {
  label: string;
  /** The label of the place chosen so far ("Pune, Maharashtra, India"), or '' when nothing valid is chosen. */
  value: string;
  /** Called with the chosen place's label and the place, or with '' as soon as the text no longer matches a choice. */
  onChange: (label: string, place?: Place) => void;
  error?: string;
  placeholder?: string;
  name?: string;
  onBlur?: () => void;
}

/**
 * Pick a real place from a list that appears as you type (ARIA combobox with a listbox). Free text is never accepted:
 * the value only becomes non-empty by choosing a suggestion, so a city that does not exist cannot be entered.
 */
export function PlaceCombobox({
  label,
  value,
  onChange,
  error,
  placeholder,
  name,
  onBlur,
}: PlaceComboboxProps) {
  const listId = useId();
  const [text, setText] = useState(value);
  const [open, setOpen] = useState(false);
  const [active, setActive] = useState(0);
  // Only text the user typed is searched for, never the label of a place already chosen (which is just being displayed).
  const { places, isSearching, isError, isTooShort } = usePlaceSuggestions(text === value ? '' : text);

  // The parent resetting the value (for example a form reset) must show up in the box.
  const [lastValue, setLastValue] = useState(value);
  if (value !== lastValue) {
    setLastValue(value);
    setText(value);
  }

  const showList = open && !isTooShort && text !== value;
  const choose = (place: Place) => {
    setText(place.label);
    setLastValue(place.label);
    setOpen(false);
    onChange(place.label, place);
  };

  const onKeyDown = (event: KeyboardEvent) => {
    if (event.key === 'ArrowDown') {
      event.preventDefault();
      setOpen(true);
      setActive((index) => Math.max(0, Math.min(index + 1, places.length - 1)));
    } else if (event.key === 'ArrowUp') {
      event.preventDefault();
      setActive((index) => Math.max(index - 1, 0));
    } else if (event.key === 'Enter' && showList && places[active]) {
      event.preventDefault();
      choose(places[active]);
    } else if (event.key === 'Escape') {
      setOpen(false);
    }
  };

  const status = isError
    ? 'Place search is unavailable right now. Try again in a moment.'
    : isSearching
      ? 'Searching…'
      : places.length === 0
        ? 'No places found. Check the spelling.'
        : null;

  return (
    <div className="relative">
      <Field
        label={label}
        error={error}
        hint={`Type at least ${String(MIN_QUERY_LENGTH)} letters and choose from the list.`}
      >
        {(control) => (
          <input
            {...control}
            name={name}
            type="text"
            role="combobox"
            autoComplete="off"
            aria-expanded={showList}
            aria-controls={listId}
            aria-autocomplete="list"
            aria-activedescendant={showList && places[active] ? `${listId}-${String(active)}` : undefined}
            placeholder={placeholder}
            value={text}
            onChange={(event) => {
              setText(event.target.value);
              setActive(0);
              setOpen(true);
              if (value !== '') {
                setLastValue('');
                onChange('');
              }
            }}
            onFocus={() => {
              setOpen(true);
            }}
            onBlur={() => {
              // The options prevent pointer focus so the input stays focused until a choice is made.
              setOpen(false);
              onBlur?.();
            }}
            onKeyDown={onKeyDown}
          />
        )}
      </Field>

      {showList ? (
        <div className="absolute z-20 mt-2 w-full overflow-hidden rounded-xl border border-line bg-white shadow-lg">
          {places.length > 0 ? (
            <ul
              id={listId}
              role="listbox"
              aria-label={`${label} suggestions`}
              className="max-h-72 overflow-auto py-1"
            >
              {places.map((place, index) => (
                <li
                  key={place.id}
                  id={`${listId}-${String(index)}`}
                  role="option"
                  aria-label={place.label}
                  ref={(option) => {
                    if (index === active && option && 'scrollIntoView' in option) {
                      option.scrollIntoView({ block: 'nearest' });
                    }
                  }}
                  aria-selected={index === active}
                  className={cn(
                    'cursor-pointer px-4 py-2.5 text-sm text-ink',
                    index === active ? 'bg-brand-50' : 'hover:bg-canvas',
                  )}
                  onPointerDown={(event) => {
                    event.preventDefault();
                  }}
                  onClick={() => {
                    choose(place);
                  }}
                  onKeyDown={onKeyDown}
                  onMouseEnter={() => {
                    setActive(index);
                  }}
                >
                  <span className="block font-medium">{place.name}</span>
                  <span className="block text-xs text-ink-muted">
                    {[place.region, place.country].filter(Boolean).join(', ')}
                  </span>
                </li>
              ))}
            </ul>
          ) : (
            <ul id={listId} role="listbox" aria-label={`${label} suggestions`} />
          )}
          {status ? (
            <p role="status" className="px-3 py-2 text-sm text-ink-muted">
              {status}
            </p>
          ) : null}
        </div>
      ) : null}
    </div>
  );
}
