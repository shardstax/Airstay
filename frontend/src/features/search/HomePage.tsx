import { defaultStay } from '@/shared/lib/stay';

import { SearchForm } from './SearchForm';

export function HomePage() {
  return (
    <div className="mx-auto max-w-5xl">
      <h1 className="text-3xl font-semibold tracking-tight text-ink sm:text-4xl">Find a place to stay.</h1>
      <p className="mt-3 max-w-xl text-lg text-ink-soft">
        Search hotels with rooms free on every night of your stay, hold one, and pay securely.
      </p>
      <div className="mt-8">
        <SearchForm defaultValues={{ city: '', ...defaultStay() }} />
      </div>
    </div>
  );
}
