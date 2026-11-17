import { Route, Routes } from 'react-router-dom';

import { AccountPage } from '@/features/account/AccountPage';
import { LoginPage } from '@/features/auth/LoginPage';
import { SignupPage } from '@/features/auth/SignupPage';
import { DesignPage } from '@/features/design/DesignPage';
import { HotelPage } from '@/features/hotel/HotelPage';
import { HomePage } from '@/features/search/HomePage';
import { SearchResultsPage } from '@/features/search/SearchResultsPage';
import { GuestOnly } from '@/shared/session/GuestOnly';
import { RequireAuth } from '@/shared/session/RequireAuth';
import { SessionExpiredWatcher } from '@/shared/session/SessionExpiredWatcher';

import { Header } from './Header';
import { NotFoundPage } from './NotFoundPage';

export function App() {
  return (
    <div className="flex min-h-screen flex-col">
      <SessionExpiredWatcher />
      <a
        href="#main"
        className="sr-only focus:not-sr-only focus:absolute focus:left-4 focus:top-4 focus:rounded-lg focus:bg-white focus:px-3 focus:py-2"
      >
        Skip to content
      </a>
      <Header />
      <main id="main" className="mx-auto w-full max-w-6xl flex-1 px-4 py-10 sm:px-6">
        <Routes>
          <Route path="/" element={<HomePage />} />
          <Route path="/search" element={<SearchResultsPage />} />
          <Route path="/hotels/:hotelId" element={<HotelPage />} />
          <Route element={<GuestOnly />}>
            <Route path="/login" element={<LoginPage />} />
            <Route path="/signup" element={<SignupPage />} />
          </Route>
          <Route element={<RequireAuth />}>
            <Route path="/account" element={<AccountPage />} />
          </Route>
          <Route path="/design" element={<DesignPage />} />
          <Route path="*" element={<NotFoundPage />} />
        </Routes>
      </main>
      <footer className="border-t border-line py-6 text-center text-sm text-ink-muted">
        AirStay, a portfolio project.
      </footer>
    </div>
  );
}
