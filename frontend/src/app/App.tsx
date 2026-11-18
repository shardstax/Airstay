import { lazy, Suspense } from 'react';
import { Route, Routes } from 'react-router-dom';

import { LoginPage } from '@/features/auth/LoginPage';
import { SignupPage } from '@/features/auth/SignupPage';
import { BookingPage } from '@/features/booking/BookingPage';
import { PaymentStatusPage } from '@/features/booking/PaymentStatusPage';
import { DesignPage } from '@/features/design/DesignPage';
import { HotelPage } from '@/features/hotel/HotelPage';
import { HomePage } from '@/features/search/HomePage';
import { SearchResultsPage } from '@/features/search/SearchResultsPage';
import { StayAssistant } from '@/features/search/StayAssistant';
import { GuestOnly } from '@/shared/session/GuestOnly';
import { RequireAuth } from '@/shared/session/RequireAuth';
import { RequireManager } from '@/shared/session/RequireManager';
import { SessionExpiredWatcher } from '@/shared/session/SessionExpiredWatcher';
import { ErrorBoundary } from '@/shared/ui/ErrorBoundary';
import { Spinner } from '@/shared/ui/Spinner';

import { Header } from './Header';
import { NotFoundPage } from './NotFoundPage';
import { RouteEffects } from './RouteEffects';

// The account and manager areas are used by a minority of visitors, so they load on demand instead of weighing down every
// first visit. (Lazy loading needs a default export; these small adapters keep the code itself on named exports.)
const AccountLayout = lazy(() =>
  import('@/features/account/AccountLayout').then((m) => ({ default: m.AccountLayout })),
);
const ProfilePage = lazy(() =>
  import('@/features/account/ProfilePage').then((m) => ({ default: m.ProfilePage })),
);
const BookingsPage = lazy(() =>
  import('@/features/account/BookingsPage').then((m) => ({ default: m.BookingsPage })),
);
const GuestsPage = lazy(() =>
  import('@/features/account/GuestsPage').then((m) => ({ default: m.GuestsPage })),
);
const ManageHotelsPage = lazy(() =>
  import('@/features/manager/ManageHotelsPage').then((m) => ({ default: m.ManageHotelsPage })),
);
const HotelFormPage = lazy(() =>
  import('@/features/manager/HotelFormPage').then((m) => ({ default: m.HotelFormPage })),
);
const HotelDashboardPage = lazy(() =>
  import('@/features/manager/HotelDashboardPage').then((m) => ({ default: m.HotelDashboardPage })),
);
const InventoryPage = lazy(() =>
  import('@/features/manager/InventoryPage').then((m) => ({ default: m.InventoryPage })),
);

export function App() {
  return (
    <div className="flex min-h-screen flex-col">
      <SessionExpiredWatcher />
      <RouteEffects />
      <a
        href="#main"
        className="sr-only focus:not-sr-only focus:absolute focus:left-4 focus:top-4 focus:rounded-lg focus:bg-white focus:px-3 focus:py-2"
      >
        Skip to content
      </a>
      <Header />
      <main
        id="main"
        tabIndex={-1}
        className="mx-auto w-full max-w-6xl flex-1 px-4 py-10 outline-none sm:px-6"
      >
        <ErrorBoundary>
          <Suspense fallback={<Spinner label="Loading the page" />}>
            <Routes>
              <Route path="/" element={<HomePage />} />
              <Route path="/search" element={<SearchResultsPage />} />
              <Route path="/hotels/:hotelId" element={<HotelPage />} />
              <Route element={<GuestOnly />}>
                <Route path="/login" element={<LoginPage />} />
                <Route path="/signup" element={<SignupPage />} />
              </Route>
              <Route element={<RequireAuth />}>
                <Route path="/account" element={<AccountLayout />}>
                  <Route index element={<ProfilePage />} />
                  <Route path="bookings" element={<BookingsPage />} />
                  <Route path="guests" element={<GuestsPage />} />
                </Route>
                <Route path="/bookings/:bookingId" element={<BookingPage />} />
                <Route path="/payments/:bookingId/status" element={<PaymentStatusPage />} />
                <Route element={<RequireManager />}>
                  <Route path="/manage" element={<ManageHotelsPage />} />
                  <Route path="/manage/hotels/new" element={<HotelFormPage />} />
                  <Route path="/manage/hotels/:hotelId" element={<HotelDashboardPage />} />
                  <Route path="/manage/hotels/:hotelId/edit" element={<HotelFormPage />} />
                  <Route path="/manage/hotels/:hotelId/rooms/:roomId/inventory" element={<InventoryPage />} />
                </Route>
              </Route>
              <Route path="/design" element={<DesignPage />} />
              <Route path="*" element={<NotFoundPage />} />
            </Routes>
          </Suspense>
        </ErrorBoundary>
      </main>
      <footer className="border-t border-line px-4 py-6 text-center text-sm text-ink-muted">
        <p>AirStay, a portfolio project.</p>
        <p className="mt-1">
          Place data from{' '}
          <a
            href="https://www.geonames.org/"
            className="underline underline-offset-2"
            target="_blank"
            rel="noreferrer noopener"
          >
            GeoNames
          </a>{' '}
          (CC BY 4.0) via{' '}
          <a
            href="https://open-meteo.com/en/docs/geocoding-api"
            className="underline underline-offset-2"
            target="_blank"
            rel="noreferrer noopener"
          >
            Open-Meteo
          </a>
          .
        </p>
      </footer>
      <StayAssistant />
    </div>
  );
}
