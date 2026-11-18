import { zodResolver } from '@hookform/resolvers/zod';
import { useMemo, useState } from 'react';
import { Controller, useForm } from 'react-hook-form';

import { describeRequestError } from '@/shared/api/errors';
import { PlaceCombobox } from '@/shared/places/PlaceCombobox';
import { Button } from '@/shared/ui/Button';
import { Card } from '@/shared/ui/Card';
import { CheckboxGroup } from '@/shared/ui/CheckboxGroup';
import { Input } from '@/shared/ui/Input';

import {
  type HotelFormValues,
  hotelSchema,
  type HotelValues,
  MAX_PHOTOS,
  toHotelRequest,
} from './hotelSchema';
import { UrlListField } from './UrlListField';
import type { Catalog } from './useCatalog';
import { type ManagedHotel, useSaveHotel } from './useHotels';

interface HotelFormProps {
  catalog: Catalog;
  /** The hotel being edited; leave out to create a new one. */
  initial?: ManagedHotel;
  onSaved: (hotel: ManagedHotel) => void;
  onCancel: () => void;
}

/**
 * Create or edit a hotel. Nothing is free text where a real value exists: the city is a place chosen from the picker (the
 * server derives the city name from it), and amenities are ticked from a fixed list.
 */
export function HotelForm({ catalog, initial, onSaved, onCancel }: HotelFormProps) {
  const save = useSaveHotel(initial?.id);
  const [uploading, setUploading] = useState(false);
  const schema = useMemo(() => hotelSchema(catalog.hotelAmenities), [catalog.hotelAmenities]);
  const {
    register,
    handleSubmit,
    control,
    setValue,
    formState: { errors },
  } = useForm<HotelFormValues, unknown, HotelValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      name: initial?.name ?? '',
      placeLabel: initial?.placeId === undefined ? '' : (initial.city ?? ''),
      placeId: initial?.placeId === undefined ? '' : String(initial.placeId),
      amenities: initial?.amenities ?? [],
      photos: initial?.photos ?? [],
      address: initial?.contactInfo?.address ?? '',
      phoneNumber: initial?.contactInfo?.phoneNumber ?? '',
      email: initial?.contactInfo?.email ?? '',
    },
  });

  return (
    <form
      noValidate
      aria-label={initial ? 'Edit hotel' : 'Add a hotel'}
      className="flex flex-col gap-6"
      onSubmit={(event) => {
        if (uploading) {
          event.preventDefault();
          return;
        }
        void handleSubmit((values) => {
          save.mutate(toHotelRequest(values), { onSuccess: onSaved });
        })(event);
      }}
    >
      <Card className="flex flex-col gap-4">
        <h2 className="text-lg font-semibold text-ink">Basics</h2>
        <Input label="Hotel name" error={errors.name?.message} {...register('name')} />
        <Controller
          control={control}
          name="placeLabel"
          render={({ field }) => (
            <PlaceCombobox
              label="City"
              placeholder="Search for the city or town"
              name={field.name}
              value={field.value}
              onBlur={field.onBlur}
              error={errors.placeLabel?.message ?? errors.placeId?.message}
              onChange={(label, place) => {
                field.onChange(label);
                setValue('placeId', place ? String(place.id) : '', { shouldValidate: place !== undefined });
              }}
            />
          )}
        />
      </Card>

      <Card className="flex flex-col gap-4">
        <h2 className="text-lg font-semibold text-ink">Amenities</h2>
        <Controller
          control={control}
          name="amenities"
          render={({ field }) => (
            <CheckboxGroup
              legend="What the hotel offers"
              options={catalog.hotelAmenities}
              value={field.value}
              onChange={field.onChange}
              error={errors.amenities?.message}
            />
          )}
        />
      </Card>

      <Card className="flex flex-col gap-4">
        <h2 className="text-lg font-semibold text-ink">Photos</h2>
        <Controller
          control={control}
          name="photos"
          render={({ field }) => (
            <UrlListField
              label="Photos"
              value={field.value}
              onChange={field.onChange}
              max={MAX_PHOTOS}
              onUploadState={setUploading}
              error={errors.photos?.message}
            />
          )}
        />
      </Card>

      <Card className="grid gap-4 sm:grid-cols-2">
        <h2 className="text-lg font-semibold text-ink sm:col-span-2">Contact details</h2>
        <div className="sm:col-span-2">
          <Input
            label="Address (optional)"
            autoComplete="street-address"
            error={errors.address?.message}
            {...register('address')}
          />
        </div>
        <Input
          label="Phone (optional)"
          type="tel"
          autoComplete="tel"
          placeholder="+91 98765 43210"
          error={errors.phoneNumber?.message}
          {...register('phoneNumber')}
        />
        <Input
          label="Email (optional)"
          type="email"
          autoComplete="email"
          error={errors.email?.message}
          {...register('email')}
        />
      </Card>

      {save.isError ? (
        <p role="alert" className="rounded-lg bg-danger-bg px-3 py-2 text-sm text-danger">
          {describeRequestError(save.error)}
        </p>
      ) : null}

      <div className="flex gap-3">
        <Button type="submit" loading={save.isPending} disabled={uploading}>
          {initial ? 'Save changes' : 'Create hotel'}
        </Button>
        <Button variant="ghost" onClick={onCancel}>
          Cancel
        </Button>
      </div>
    </form>
  );
}
