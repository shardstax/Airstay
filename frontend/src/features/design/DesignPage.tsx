import { Badge } from '@/shared/ui/Badge';
import { Button } from '@/shared/ui/Button';
import { Card } from '@/shared/ui/Card';
import { Input } from '@/shared/ui/Input';

const swatches = ['50', '100', '200', '300', '400', '500', '600', '700', '800', '900'] as const;
const swatchClass: Record<(typeof swatches)[number], string> = {
  '50': 'bg-brand-50',
  '100': 'bg-brand-100',
  '200': 'bg-brand-200',
  '300': 'bg-brand-300',
  '400': 'bg-brand-400',
  '500': 'bg-brand-500',
  '600': 'bg-brand-600',
  '700': 'bg-brand-700',
  '800': 'bg-brand-800',
  '900': 'bg-brand-900',
};

/** The building blocks in one place, to review the look and to check changes against. */
export function DesignPage() {
  return (
    <div className="flex flex-col gap-10">
      <div>
        <h1 className="text-3xl font-semibold tracking-tight">Design basics</h1>
        <p className="mt-2 text-ink-soft">One calm blue, deep navy text, quiet borders.</p>
      </div>

      <section aria-labelledby="colour">
        <h2 id="colour" className="mb-3 text-sm font-medium">
          Brand blue
        </h2>
        <div className="grid grid-cols-5 gap-2 sm:grid-cols-10">
          {swatches.map((s) => (
            <div key={s}>
              <div className={`h-12 rounded-lg border border-line ${swatchClass[s]}`} />
              <p className="mt-1 text-xs text-ink-muted">{s}</p>
            </div>
          ))}
        </div>
      </section>

      <section aria-labelledby="type" className="flex flex-col gap-2">
        <h2 id="type" className="mb-1 text-sm font-medium">
          Type
        </h2>
        <p className="text-4xl font-semibold tracking-tight">Heading, 36</p>
        <p className="text-2xl font-semibold tracking-tight">Heading, 24</p>
        <p className="text-base text-ink-soft">Body text in soft ink, 16. Used for most reading.</p>
        <p className="text-sm text-ink-muted">Secondary text, 14.</p>
      </section>

      <section aria-labelledby="buttons">
        <h2 id="buttons" className="mb-3 text-sm font-medium">
          Buttons
        </h2>
        <div className="flex flex-wrap gap-3">
          <Button>Primary</Button>
          <Button variant="secondary">Secondary</Button>
          <Button variant="ghost">Ghost</Button>
          <Button variant="danger">Danger</Button>
          <Button loading>Loading</Button>
          <Button disabled>Disabled</Button>
        </div>
      </section>

      <section aria-labelledby="badges">
        <h2 id="badges" className="mb-3 text-sm font-medium">
          Badges
        </h2>
        <div className="flex flex-wrap gap-2">
          <Badge>Neutral</Badge>
          <Badge tone="brand">Reserved</Badge>
          <Badge tone="success">Confirmed</Badge>
          <Badge tone="warning">Payment pending</Badge>
          <Badge tone="danger">Cancelled</Badge>
        </div>
      </section>

      <section aria-labelledby="fields" className="max-w-sm">
        <h2 id="fields" className="mb-3 text-sm font-medium">
          Fields
        </h2>
        <Card className="flex flex-col gap-4">
          <Input label="Email" type="email" placeholder="you@example.com" hint="We never share it." />
          <Input label="Password" type="password" error="Use at least 8 characters." defaultValue="short" />
        </Card>
      </section>
    </div>
  );
}
