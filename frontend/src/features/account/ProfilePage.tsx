import { zodResolver } from '@hookform/resolvers/zod';
import { useMemo } from 'react';
import { useForm } from 'react-hook-form';

import { describeRequestError } from '@/shared/api/errors';
import { today } from '@/shared/lib/stay';
import { type SessionUser, useSession } from '@/shared/session/session';
import { Badge } from '@/shared/ui/Badge';
import { Button } from '@/shared/ui/Button';
import { Card } from '@/shared/ui/Card';
import { Input } from '@/shared/ui/Input';
import { Select } from '@/shared/ui/Select';

import { profileSchema, type ProfileValues } from './profileSchema';
import { useUpdateProfile } from './useUpdateProfile';

const roleLabels: Record<string, string> = { GUEST: 'Guest', HOTEL_MANAGER: 'Hotel manager' };

export function ProfilePage() {
  const { user } = useSession();
  // The form takes its starting values when it first appears, so it is only shown once the user is known.
  return user ? <Profile user={user} /> : null;
}

function Profile({ user }: { user: SessionUser }) {
  const update = useUpdateProfile();
  const schema = useMemo(() => profileSchema(), []);
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<ProfileValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      name: user.name,
      gender: user.gender ?? '',
      dateOfBirth: user.dateOfBirth ?? '',
    },
  });

  return (
    <div className="flex flex-col gap-6">
      <Card>
        <dl className="grid gap-3 sm:grid-cols-[9rem_1fr]">
          <dt className="text-sm text-ink-muted">Email</dt>
          <dd className="text-ink">{user.email}</dd>
          <dt className="text-sm text-ink-muted">Account type</dt>
          <dd className="flex flex-wrap gap-2">
            {user.roles.map((role) => (
              <Badge key={role} tone="brand">
                {roleLabels[role] ?? role}
              </Badge>
            ))}
          </dd>
        </dl>
      </Card>

      <Card>
        <h2 className="text-lg font-semibold text-ink">Your details</h2>
        <form
          noValidate
          aria-label="Your details"
          className="mt-4 grid gap-4 sm:grid-cols-3"
          onSubmit={(event) => {
            void handleSubmit((values) => {
              update.mutate(values);
            })(event);
          }}
        >
          <Input label="Full name" autoComplete="name" error={errors.name?.message} {...register('name')} />
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
            autoComplete="bday"
            error={errors.dateOfBirth?.message}
            {...register('dateOfBirth')}
          />

          {update.isError ? (
            <p role="alert" className="text-sm text-danger sm:col-span-3">
              {describeRequestError(update.error)}
            </p>
          ) : null}
          {update.isSuccess ? (
            <p role="status" className="text-sm text-success sm:col-span-3">
              Your details were saved.
            </p>
          ) : null}

          <div className="sm:col-span-3">
            <Button type="submit" loading={update.isPending}>
              Save changes
            </Button>
          </div>
        </form>
      </Card>
    </div>
  );
}
