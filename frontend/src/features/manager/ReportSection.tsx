import { useMemo } from 'react';

import { formatPrice } from '@/shared/lib/format';
import { addDays, today } from '@/shared/lib/stay';
import { Card } from '@/shared/ui/Card';
import { Skeleton } from '@/shared/ui/Skeleton';

import { useHotelReport } from './useHotelActivity';

const REPORT_DAYS = 30;

/** Confirmed bookings and revenue over the last 30 days, as plain numbers (charts come with the monitoring work). */
export function ReportSection({ hotelId }: { hotelId: number }) {
  const { startDate, endDate } = useMemo(() => {
    const end = today();
    return { startDate: addDays(end, -REPORT_DAYS), endDate: end };
  }, []);
  const report = useHotelReport(hotelId, startDate, endDate);

  return (
    <section aria-labelledby="report-heading" className="flex flex-col gap-4">
      <h2 id="report-heading" className="text-xl font-semibold text-ink">
        Last {REPORT_DAYS} days
      </h2>

      {report.isPending ? (
        <div role="status" aria-label="Loading the report">
          <Skeleton className="h-20" />
        </div>
      ) : report.isError ? (
        <p role="alert" className="text-sm text-danger">
          {report.error.message}
        </p>
      ) : (
        <dl className="grid gap-4 sm:grid-cols-3">
          <Stat label="Confirmed bookings" value={String(report.data.bookingCount)} />
          <Stat label="Revenue" value={formatPrice(report.data.totalRevenue)} />
          <Stat label="Average per booking" value={formatPrice(report.data.avgRevenue)} />
        </dl>
      )}
    </section>
  );
}

function Stat({ label, value }: { label: string; value: string }) {
  return (
    <Card>
      <dt className="text-sm text-ink-muted">{label}</dt>
      <dd className="mt-1 text-2xl font-semibold text-ink">{value}</dd>
    </Card>
  );
}
