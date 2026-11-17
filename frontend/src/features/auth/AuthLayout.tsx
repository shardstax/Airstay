import type { ReactNode } from 'react';

import { Card } from '@/shared/ui/Card';

interface AuthLayoutProps {
  title: string;
  subtitle: string;
  children: ReactNode;
  footer: ReactNode;
}

/** The shared frame of the sign-in and sign-up pages. */
export function AuthLayout({ title, subtitle, children, footer }: AuthLayoutProps) {
  return (
    <div className="mx-auto max-w-md">
      <h1 className="text-2xl font-semibold tracking-tight text-ink">{title}</h1>
      <p className="mt-2 text-ink-soft">{subtitle}</p>
      <Card className="mt-6">{children}</Card>
      <p className="mt-6 text-center text-sm text-ink-soft">{footer}</p>
    </div>
  );
}
