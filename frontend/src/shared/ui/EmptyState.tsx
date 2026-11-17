import type { ReactNode } from 'react';

interface EmptyStateProps {
  title: string;
  description?: string;
  children?: ReactNode;
}

/** A centred message for "nothing here" and "something went wrong", with an optional action. */
export function EmptyState({ title, description, children }: EmptyStateProps) {
  return (
    <div className="rounded-card border border-dashed border-line bg-white px-6 py-12 text-center">
      <h2 className="text-lg font-semibold text-ink">{title}</h2>
      {description ? <p className="mx-auto mt-2 max-w-md text-ink-soft">{description}</p> : null}
      {children ? <div className="mt-6 flex justify-center gap-3">{children}</div> : null}
    </div>
  );
}
