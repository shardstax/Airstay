import { Link } from 'react-router-dom';

import { cn } from '@/shared/lib/cn';

interface PaginationProps {
  page: number;
  totalPages: number;
  /** The link for a given page number. */
  hrefFor: (page: number) => string;
}

const linkClass =
  'inline-flex h-10 items-center rounded-lg border border-line bg-white px-4 text-sm font-medium text-ink hover:bg-canvas';

/** Previous / next links with the current position. Links (not buttons) so each page has its own address. */
export function Pagination({ page, totalPages, hrefFor }: PaginationProps) {
  if (totalPages <= 1) {
    return null;
  }
  return (
    <nav aria-label="Results pages" className="flex items-center justify-between gap-4">
      {page > 1 ? (
        <Link to={hrefFor(page - 1)} className={linkClass}>
          Previous
        </Link>
      ) : (
        <span aria-hidden className={cn(linkClass, 'invisible')}>
          Previous
        </span>
      )}
      <p className="text-sm text-ink-soft">
        Page {page} of {totalPages}
      </p>
      {page < totalPages ? (
        <Link to={hrefFor(page + 1)} className={linkClass}>
          Next
        </Link>
      ) : (
        <span aria-hidden className={cn(linkClass, 'invisible')}>
          Next
        </span>
      )}
    </nav>
  );
}
