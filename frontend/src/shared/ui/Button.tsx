import type { ButtonHTMLAttributes } from 'react';

import { cn } from '@/shared/lib/cn';

type Variant = 'primary' | 'secondary' | 'ghost' | 'danger';

const variants: Record<Variant, string> = {
  primary: 'bg-brand-600 text-white hover:bg-brand-700 disabled:bg-brand-300',
  secondary: 'bg-white text-ink border border-line hover:bg-canvas disabled:text-ink-muted',
  ghost: 'text-brand-700 hover:bg-brand-50 disabled:text-ink-muted',
  danger: 'bg-danger text-white hover:opacity-90 disabled:opacity-50',
};

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: Variant;
  loading?: boolean;
}

export function Button({
  variant = 'primary',
  loading = false,
  className,
  children,
  disabled,
  type = 'button',
  ...rest
}: ButtonProps) {
  return (
    <button
      type={type}
      disabled={loading || disabled === true}
      aria-busy={loading || undefined}
      className={cn(
        'inline-flex h-10 items-center justify-center gap-2 rounded-lg px-4 text-sm font-medium transition-colors disabled:cursor-not-allowed',
        variants[variant],
        className,
      )}
      {...rest}
    >
      {loading && (
        <span
          aria-hidden
          className="size-4 animate-spin rounded-full border-2 border-current border-t-transparent"
        />
      )}
      {children}
    </button>
  );
}
