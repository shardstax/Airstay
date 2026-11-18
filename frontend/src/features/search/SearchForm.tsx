import { zodResolver } from '@hookform/resolvers/zod';
import { useMemo, useState } from 'react';
import { Controller, useForm, useWatch } from 'react-hook-form';
import { useNavigate } from 'react-router-dom';

import { useCatalog } from '@/shared/api/useCatalog';
import { addDays, MAX_ROOMS, today } from '@/shared/lib/stay';
import { PlaceCombobox } from '@/shared/places/PlaceCombobox';
import { Button } from '@/shared/ui/Button';
import { Card } from '@/shared/ui/Card';
import { Input } from '@/shared/ui/Input';
import { Select } from '@/shared/ui/Select';

import { searchSchema, type SearchValues, toSearchParams } from './searchQuery';

interface SearchFormProps {
  defaultValues: SearchValues;
}

const roomOptions = Array.from({ length: MAX_ROOMS }, (_, index) => index + 1);

/** Place, dates and rooms. Submitting moves to the results page, with the search in the URL. */
export function SearchForm({ defaultValues }: SearchFormProps) {
  const navigate = useNavigate();
  const schema = useMemo(() => searchSchema(), []);
  const {
    register,
    handleSubmit,
    control,
    formState: { errors },
  } = useForm<SearchValues>({ resolver: zodResolver(schema), defaultValues });

  const checkIn = useWatch({ control, name: 'startDate' });
  const [amenitiesOpen, setAmenitiesOpen] = useState(false);
  const catalog = useCatalog(amenitiesOpen);

  return (
    <Card className="rounded-2xl shadow-lg">
      <form
        noValidate
        aria-label="Search hotels"
        className="grid gap-4 sm:grid-cols-2 lg:grid-cols-[2fr_1fr_1fr_7rem_auto] lg:items-start"
        onSubmit={(event) => {
          void handleSubmit((values) => {
            void navigate({ pathname: '/search', search: toSearchParams(values).toString() });
          })(event);
        }}
      >
        <Controller
          control={control}
          name="city"
          render={({ field }) => (
            <PlaceCombobox
              label="City"
              placeholder="e.g. Pune"
              name={field.name}
              value={field.value}
              onChange={(label) => {
                field.onChange(label);
              }}
              onBlur={field.onBlur}
              error={errors.city?.message}
            />
          )}
        />
        <Input
          label="Check-in"
          type="date"
          min={today()}
          error={errors.startDate?.message}
          {...register('startDate')}
        />
        <Input
          label="Check-out"
          type="date"
          min={checkIn ? addDays(checkIn, 1) : undefined}
          error={errors.endDate?.message}
          {...register('endDate')}
        />
        <Select label="Rooms" error={errors.rooms?.message} {...register('rooms', { valueAsNumber: true })}>
          {roomOptions.map((count) => (
            <option key={count} value={count}>
              {count}
            </option>
          ))}
        </Select>
        <Button type="submit" className="sm:col-span-2 lg:col-span-1 lg:mt-[1.625rem]">
          Search
        </Button>
        <Controller
          control={control}
          name="amenities"
          render={({ field }) => {
            const selected = field.value ?? [];
            return (
              <details
                className="col-span-full rounded-lg border border-line px-3 py-2"
                onToggle={(event) => {
                  setAmenitiesOpen(event.currentTarget.open);
                }}
              >
                <summary className="cursor-pointer text-sm font-medium">
                  {selected.length ? `Hotel amenities · ${selected.join(', ')}` : 'Hotel amenities'}
                </summary>
                <fieldset className="mt-3 flex flex-wrap gap-2">
                  <legend className="sr-only">Required hotel amenities</legend>
                  {amenitiesOpen && catalog.isPending && (
                    <p role="status" aria-label="Loading amenities">
                      Loading amenities…
                    </p>
                  )}
                  {catalog.isError && (
                    <Button
                      variant="secondary"
                      onClick={() => {
                        void catalog.refetch();
                      }}
                    >
                      Retry loading amenities
                    </Button>
                  )}
                  {(catalog.data?.hotelAmenities ?? selected).map((option) => (
                    <label
                      key={option}
                      className="flex cursor-pointer items-center gap-2 rounded-md border border-line px-2 py-1 text-sm"
                    >
                      <input
                        type="checkbox"
                        checked={selected.includes(option)}
                        onChange={(event) => {
                          field.onChange(
                            event.target.checked
                              ? [...selected, option]
                              : selected.filter((item) => item !== option),
                          );
                        }}
                      />
                      {option}
                    </label>
                  ))}
                </fieldset>
              </details>
            );
          }}
        />
      </form>
    </Card>
  );
}
