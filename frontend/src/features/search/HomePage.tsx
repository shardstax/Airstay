import { defaultStay } from '@/shared/lib/stay';

import { SearchForm } from './SearchForm';

export function HomePage() {
  return (
    <div className="mx-auto max-w-6xl">
      <section className="relative isolate flex min-h-80 items-center overflow-hidden rounded-3xl bg-ink px-6 text-white sm:min-h-104 sm:px-12">
        <img
          src="/jaipur.jpg"
          alt=""
          fetchPriority="high"
          className="absolute inset-0 -z-20 size-full object-cover"
        />
        <div className="absolute inset-0 -z-10 bg-linear-to-r from-black/80 via-black/55 to-black/15" />
        <div className="max-w-xl pb-10">
          <h1 className="font-serif text-4xl leading-tight tracking-tight sm:text-6xl">
            Find a place to stay.
          </h1>
          <p className="mt-4 max-w-md text-base text-white/90 sm:text-lg">
            Find hotels across India with rooms available for every night of your trip.
          </p>
        </div>
      </section>
      <div className="relative z-10 mx-3 -mt-9 sm:mx-8">
        <SearchForm defaultValues={{ city: '', ...defaultStay() }} />
      </div>
    </div>
  );
}
