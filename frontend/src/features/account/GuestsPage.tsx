import { useState } from 'react';

import { describeRequestError } from '@/shared/api/errors';
import { GuestForm, NewGuestForm } from '@/shared/guests/GuestForm';
import { type SavedGuest, useDeleteGuest, useGuests, useUpdateGuest } from '@/shared/guests/useGuests';
import { formatDate } from '@/shared/lib/format';
import { Button } from '@/shared/ui/Button';
import { Card } from '@/shared/ui/Card';
import { EmptyState } from '@/shared/ui/EmptyState';
import { Skeleton } from '@/shared/ui/Skeleton';

const genderLabels = { MALE: 'Male', FEMALE: 'Female', OTHER: 'Other' } as const;

/** The people the user books for. Saved once, picked from when booking. */
export function GuestsPage() {
  const guests = useGuests();
  const [adding, setAdding] = useState(false);

  return (
    <section aria-label="Saved guests" className="flex flex-col gap-4">
      <div className="flex items-center justify-between gap-4">
        <p className="text-ink-soft">The people you book rooms for. You pick from this list when booking.</p>
        {!adding ? (
          <Button
            onClick={() => {
              setAdding(true);
            }}
          >
            Add a guest
          </Button>
        ) : null}
      </div>

      {adding ? (
        <Card>
          <NewGuestForm
            onCreated={() => {
              setAdding(false);
            }}
            onCancel={() => {
              setAdding(false);
            }}
          />
        </Card>
      ) : null}

      {guests.isPending ? (
        <div role="status" aria-label="Loading your guests" className="flex flex-col gap-3">
          <Skeleton className="h-16" />
          <Skeleton className="h-16" />
        </div>
      ) : guests.isError ? (
        <EmptyState title="We could not load your guests" description={guests.error.message}>
          <Button
            onClick={() => {
              void guests.refetch();
            }}
          >
            Try again
          </Button>
        </EmptyState>
      ) : guests.data.length === 0 && !adding ? (
        <EmptyState title="No saved guests" description="Add the people you travel with to book faster." />
      ) : (
        <ul className="flex flex-col gap-3">
          {guests.data.map((guest) => (
            <li key={guest.id}>
              <GuestRow guest={guest} />
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}

function GuestRow({ guest }: { guest: SavedGuest }) {
  const [mode, setMode] = useState<'view' | 'edit' | 'delete'>('view');
  const update = useUpdateGuest(guest.id);
  const remove = useDeleteGuest();

  if (mode === 'edit') {
    return (
      <Card>
        <GuestForm
          initial={guest}
          submitLabel="Save changes"
          isPending={update.isPending}
          error={update.error}
          onCancel={() => {
            setMode('view');
          }}
          onSubmit={(values) => {
            update.mutate(values, {
              onSuccess: () => {
                setMode('view');
              },
            });
          }}
        />
      </Card>
    );
  }

  return (
    <Card className="flex flex-col gap-3">
      <div className="flex items-center justify-between gap-4">
        <div>
          <p className="font-medium text-ink">{guest.name}</p>
          <p className="text-sm text-ink-muted">
            {[
              guest.gender ? genderLabels[guest.gender] : null,
              guest.dateOfBirth ? `born ${formatDate(guest.dateOfBirth)}` : null,
            ]
              .filter(Boolean)
              .join(' · ') || 'No other details'}
          </p>
        </div>
        {mode === 'view' ? (
          <div className="flex gap-2">
            <Button
              variant="secondary"
              aria-label={`Edit ${guest.name}`}
              onClick={() => {
                setMode('edit');
              }}
            >
              Edit
            </Button>
            <Button
              variant="ghost"
              aria-label={`Delete ${guest.name}`}
              onClick={() => {
                setMode('delete');
              }}
            >
              Delete
            </Button>
          </div>
        ) : null}
      </div>

      {mode === 'delete' ? (
        <div
          role="group"
          aria-label={`Confirm deleting ${guest.name}`}
          className="rounded-lg border border-line bg-canvas p-4"
        >
          <p className="text-sm text-ink">Delete {guest.name} from your saved guests?</p>
          {remove.isError ? (
            <p role="alert" className="mt-2 text-sm text-danger">
              {describeRequestError(remove.error)}
            </p>
          ) : null}
          <div className="mt-3 flex gap-3">
            <Button
              variant="danger"
              loading={remove.isPending}
              onClick={() => {
                remove.mutate(guest.id);
              }}
            >
              Yes, delete
            </Button>
            <Button
              variant="secondary"
              onClick={() => {
                setMode('view');
              }}
            >
              Keep
            </Button>
          </div>
        </div>
      ) : null}
    </Card>
  );
}
