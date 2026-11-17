import { zodResolver } from '@hookform/resolvers/zod';
import { useMemo } from 'react';
import { Controller, useForm } from 'react-hook-form';

import { describeRequestError } from '@/shared/api/errors';
import { Button } from '@/shared/ui/Button';
import { CheckboxGroup } from '@/shared/ui/CheckboxGroup';
import { Input } from '@/shared/ui/Input';
import { Select } from '@/shared/ui/Select';

import { roomSchema, type RoomValues } from './roomSchema';
import type { Catalog } from './useCatalog';
import { type SavedRoom, useSaveRoom } from './useRooms';

interface RoomFormProps {
  hotelId: number;
  catalog: Catalog;
  /** The room type being edited; leave out to add a new one. */
  initial?: SavedRoom;
  onSaved: () => void;
  onCancel: () => void;
}

/** Add or change a room type. The type and amenities are chosen from fixed lists; the numbers have sensible limits. */
export function RoomForm({ hotelId, catalog, initial, onSaved, onCancel }: RoomFormProps) {
  const save = useSaveRoom(hotelId, initial?.id);
  const schema = useMemo(() => roomSchema(catalog.roomTypes, catalog.roomAmenities), [catalog]);
  const {
    register,
    handleSubmit,
    control,
    formState: { errors },
  } = useForm<RoomValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      type: initial?.type ?? '',
      basePrice: initial?.basePrice,
      totalCount: initial?.totalCount,
      capacity: initial?.capacity,
      amenities: initial?.amenities ?? [],
    },
  });

  return (
    <form
      noValidate
      aria-label={initial ? `Edit ${initial.type}` : 'Add a room type'}
      className="grid gap-4 sm:grid-cols-2"
      onSubmit={(event) => {
        void handleSubmit((values) => {
          save.mutate(values, { onSuccess: onSaved });
        })(event);
      }}
    >
      <Select label="Room type" error={errors.type?.message} {...register('type')}>
        <option value="">Choose a type</option>
        {catalog.roomTypes.map((type) => (
          <option key={type} value={type}>
            {type}
          </option>
        ))}
      </Select>
      <Input
        label="Base price per night (₹)"
        type="number"
        inputMode="decimal"
        step="0.01"
        min="0.01"
        error={errors.basePrice?.message}
        {...register('basePrice', { valueAsNumber: true })}
      />
      <Input
        label="Rooms of this type"
        type="number"
        min="1"
        max="1000"
        error={errors.totalCount?.message}
        {...register('totalCount', { valueAsNumber: true })}
      />
      <Input
        label="Guests per room"
        type="number"
        min="1"
        max="20"
        error={errors.capacity?.message}
        {...register('capacity', { valueAsNumber: true })}
      />
      <div className="sm:col-span-2">
        <Controller
          control={control}
          name="amenities"
          render={({ field }) => (
            <CheckboxGroup
              legend="Room amenities"
              options={catalog.roomAmenities}
              value={field.value}
              onChange={field.onChange}
              error={errors.amenities?.message}
            />
          )}
        />
      </div>

      {save.isError ? (
        <p role="alert" className="text-sm text-danger sm:col-span-2">
          {describeRequestError(save.error)}
        </p>
      ) : null}

      <div className="flex gap-3 sm:col-span-2">
        <Button type="submit" loading={save.isPending}>
          {initial ? 'Save changes' : 'Add room type'}
        </Button>
        <Button variant="ghost" onClick={onCancel}>
          Cancel
        </Button>
      </div>
    </form>
  );
}
