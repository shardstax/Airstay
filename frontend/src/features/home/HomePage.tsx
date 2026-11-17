import { Badge } from '@/shared/ui/Badge';
import { Card } from '@/shared/ui/Card';

import { useApiHealth } from './useApiHealth';

export function HomePage() {
  const health = useApiHealth();

  return (
    <div className="mx-auto max-w-2xl">
      <h1 className="text-3xl font-semibold tracking-tight text-ink sm:text-4xl">Find a place to stay.</h1>
      <p className="mt-3 text-lg text-ink-soft">
        Search is coming in the next step. This page checks that the frontend can reach the AirStay API.
      </p>

      <Card className="mt-8">
        <div className="flex items-center justify-between gap-4">
          <div>
            <h2 className="text-sm font-medium text-ink">API status</h2>
            <p className="mt-1 text-sm text-ink-muted">
              Checked through the same origin as the page, the way real requests will go.
            </p>
          </div>
          <span role="status">
            {health.isPending ? (
              <Badge>Checking…</Badge>
            ) : health.data === 'UP' ? (
              <Badge tone="success">Connected</Badge>
            ) : (
              <Badge tone="danger">Unreachable</Badge>
            )}
          </span>
        </div>
      </Card>
    </div>
  );
}
