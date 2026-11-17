import { useState } from 'react';

import { describeRequestError } from '@/shared/api/errors';
import { NewGuestForm } from '@/shared/guests/GuestForm';
import { useGuests } from '@/shared/guests/useGuests';
import { pluralize } from '@/shared/lib/format';
import { Button } from '@/shared/ui/Button';
import { Card } from '@/shared/ui/Card';
import { Skeleton } from '@/shared/ui/Skeleton';

import type { Booking } from './bookingStatus';
import { useAddGuests } from './useAddGuests';

/** Step 1: who is staying. Pick from the saved guests or add someone new, up to what the room allows. */
export function GuestsStep({ booking }: { booking: Booking }) {
  const guests = useGuests();
  const addGuests = useAddGuests(booking.id);
  const [selected, setSelected] = useState<ReadonlySet<number>>(new Set());
  const [adding, setAdding] = useState(false);

  const full = selected.size >= booking.maxGuests;
  const toggle = (id: number, checked: boolean) => {
    setSelected((current) => {
      const next = new Set(current);
      if (checked) {
        next.add(id);
      } else {
        next.delete(id);
      }
      return next;
    });
  };

  const saved = guests.data ?? [];
  const showForm = adding || (guests.isSuccess && saved.length === 0);

  return (
    <Card>
      <h2 className="text-lg font-semibold text-ink">Who is staying?</h2>
      <p className="mt-1 text-sm text-ink-soft">
        Choose up to {pluralize(booking.maxGuests, 'guest')} for this booking.
      </p>

      {guests.isPending ? (
        <div role="status" aria-label="Loading your guests" className="mt-4 flex flex-col gap-2">
          <Skeleton className="h-10" />
          <Skeleton className="h-10" />
        </div>
      ) : guests.isError ? (
        <p role="alert" className="mt-4 text-sm text-danger">
          {describeRequestError(guests.error)}
        </p>
      ) : saved.length > 0 ? (
        <fieldset className="mt-4">
          <legend className="sr-only">Saved guests</legend>
          <ul className="flex flex-col gap-2">
            {saved.map((guest) => {
              const checked = selected.has(guest.id);
              return (
                <li key={guest.id}>
                  <label className="flex cursor-pointer items-center gap-3 rounded-lg border border-line px-3 py-2.5 has-[:checked]:border-brand-600 has-[:checked]:bg-brand-50 has-[:disabled]:cursor-not-allowed has-[:disabled]:opacity-60">
                    <input
                      type="checkbox"
                      className="size-4 accent-brand-600"
                      checked={checked}
                      disabled={!checked && full}
                      onChange={(event) => {
                        toggle(guest.id, event.target.checked);
                      }}
                    />
                    <span className="text-ink">{guest.name}</span>
                  </label>
                </li>
              );
            })}
          </ul>
        </fieldset>
      ) : null}

      {showForm ? (
        <div className="mt-4 border-t border-line pt-4">
          <NewGuestForm
            onCreated={(guest) => {
              if (!full) {
                toggle(guest.id, true);
              }
              setAdding(false);
            }}
            onCancel={
              saved.length > 0
                ? () => {
                    setAdding(false);
                  }
                : undefined
            }
          />
        </div>
      ) : (
        <Button
          variant="ghost"
          className="mt-3"
          onClick={() => {
            setAdding(true);
          }}
        >
          Add a new guest
        </Button>
      )}

      {addGuests.isError ? (
        <p role="alert" className="mt-4 rounded-lg bg-danger-bg px-3 py-2 text-sm text-danger">
          {describeRequestError(addGuests.error)}
        </p>
      ) : null}

      <div className="mt-6 flex items-center justify-between gap-4">
        <p className="text-sm text-ink-soft" role="status">
          {selected.size} of {booking.maxGuests} selected
        </p>
        <Button
          disabled={selected.size === 0}
          loading={addGuests.isPending}
          onClick={() => {
            addGuests.mutate([...selected]);
          }}
        >
          Continue
        </Button>
      </div>
    </Card>
  );
}
