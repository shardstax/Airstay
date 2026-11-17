import { zodResolver } from '@hookform/resolvers/zod';
import { useMemo } from 'react';
import { useForm, useWatch } from 'react-hook-form';
import { useNavigate } from 'react-router-dom';

import { addDays, MAX_ROOMS, today } from '@/shared/lib/stay';
import { Button } from '@/shared/ui/Button';
import { Card } from '@/shared/ui/Card';
import { Input } from '@/shared/ui/Input';
import { Select } from '@/shared/ui/Select';

import { searchSchema, type SearchValues, toSearchParams } from './searchQuery';

interface SearchFormProps {
  defaultValues: SearchValues;
}

const roomOptions = Array.from({ length: MAX_ROOMS }, (_, index) => index + 1);

/** City, dates and rooms. Submitting moves to the results page, with the search in the URL. */
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

  return (
    <Card>
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
        <Input
          label="City"
          autoComplete="off"
          placeholder="Where to?"
          error={errors.city?.message}
          {...register('city')}
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
      </form>
    </Card>
  );
}
