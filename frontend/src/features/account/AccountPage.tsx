import { useSession } from '@/shared/session/session';
import { Badge } from '@/shared/ui/Badge';
import { Card } from '@/shared/ui/Card';

const roleLabels: Record<string, string> = { GUEST: 'Guest', HOTEL_MANAGER: 'Hotel manager' };

export function AccountPage() {
  const { user } = useSession();

  return (
    <div className="mx-auto max-w-2xl">
      <h1 className="text-2xl font-semibold tracking-tight text-ink">My account</h1>
      <Card className="mt-6">
        <dl className="grid gap-4 sm:grid-cols-[8rem_1fr]">
          <dt className="text-sm text-ink-muted">Name</dt>
          <dd className="text-ink">{user?.name}</dd>
          <dt className="text-sm text-ink-muted">Email</dt>
          <dd className="text-ink">{user?.email}</dd>
          <dt className="text-sm text-ink-muted">Account type</dt>
          <dd className="flex flex-wrap gap-2">
            {user?.roles?.map((role) => (
              <Badge key={role} tone="brand">
                {roleLabels[role] ?? role}
              </Badge>
            ))}
          </dd>
        </dl>
      </Card>
    </div>
  );
}
