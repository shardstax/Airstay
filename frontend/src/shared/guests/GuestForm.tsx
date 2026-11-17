import { zodResolver } from '@hookform/resolvers/zod';
import { useMemo } from 'react';
import { useForm } from 'react-hook-form';

import { describeRequestError } from '@/shared/api/errors';
import { today } from '@/shared/lib/stay';
import { Button } from '@/shared/ui/Button';
import { Input } from '@/shared/ui/Input';
import { Select } from '@/shared/ui/Select';

import { guestSchema, type GuestValues, toGuest } from './guestSchema';
import { type Guest, type SavedGuest, useCreateGuest } from './useGuests';

interface GuestFormProps {
  /** The guest being edited; leave out to start with an empty form. */
  initial?: SavedGuest;
  submitLabel: string;
  isPending: boolean;
  error: unknown;
  onSubmit: (guest: Guest) => void;
  onCancel?: () => void;
}

/** Name, gender and date of birth of a guest. It only collects and checks; saving is up to the caller. */
export function GuestForm({ initial, submitLabel, isPending, error, onSubmit, onCancel }: GuestFormProps) {
  const schema = useMemo(() => guestSchema(), []);
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<GuestValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      name: initial?.name ?? '',
      gender: initial?.gender ?? '',
      dateOfBirth: initial?.dateOfBirth ?? '',
    },
  });

  return (
    <form
      noValidate
      aria-label={initial ? 'Edit guest' : 'Add a guest'}
      className="grid gap-4 sm:grid-cols-3"
      onSubmit={(event) => {
        void handleSubmit((values) => {
          onSubmit(toGuest(values));
        })(event);
      }}
    >
      <Input label="Full name" autoComplete="off" error={errors.name?.message} {...register('name')} />
      <Select label="Gender (optional)" error={errors.gender?.message} {...register('gender')}>
        <option value="">Prefer not to say</option>
        <option value="FEMALE">Female</option>
        <option value="MALE">Male</option>
        <option value="OTHER">Other</option>
      </Select>
      <Input
        label="Date of birth (optional)"
        type="date"
        max={today()}
        error={errors.dateOfBirth?.message}
        {...register('dateOfBirth')}
      />

      {error ? (
        <p role="alert" className="text-sm text-danger sm:col-span-3">
          {describeRequestError(error)}
        </p>
      ) : null}

      <div className="flex gap-3 sm:col-span-3">
        <Button type="submit" loading={isPending}>
          {submitLabel}
        </Button>
        {onCancel ? (
          <Button variant="ghost" onClick={onCancel}>
            Cancel
          </Button>
        ) : null}
      </div>
    </form>
  );
}

/** The form for adding a new guest to the user's saved list. */
export function NewGuestForm({
  onCreated,
  onCancel,
}: {
  onCreated: (guest: SavedGuest) => void;
  onCancel?: () => void;
}) {
  const create = useCreateGuest();
  return (
    <GuestForm
      submitLabel="Save guest"
      isPending={create.isPending}
      error={create.error}
      onCancel={onCancel}
      onSubmit={(guest) => {
        create.mutate(guest, { onSuccess: onCreated });
      }}
    />
  );
}
