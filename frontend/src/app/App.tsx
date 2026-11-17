import { Link, Route, Routes } from 'react-router-dom';

import { DesignPage } from '@/features/design/DesignPage';
import { HomePage } from '@/features/home/HomePage';

import { NotFoundPage } from './NotFoundPage';

export function App() {
  return (
    <div className="flex min-h-screen flex-col">
      <a
        href="#main"
        className="sr-only focus:not-sr-only focus:absolute focus:left-4 focus:top-4 focus:rounded-lg focus:bg-white focus:px-3 focus:py-2"
      >
        Skip to content
      </a>
      <header className="border-b border-line bg-white">
        <div className="mx-auto flex h-16 max-w-6xl items-center justify-between px-4 sm:px-6">
          <Link to="/" className="flex items-center gap-2 text-lg font-semibold tracking-tight text-ink">
            <img src="/favicon.svg" alt="" className="size-7" />
            AirStay
          </Link>
        </div>
      </header>
      <main id="main" className="mx-auto w-full max-w-6xl flex-1 px-4 py-10 sm:px-6">
        <Routes>
          <Route path="/" element={<HomePage />} />
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
