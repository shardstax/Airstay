import { useQuery } from '@tanstack/react-query';

import { api } from '@/shared/api/client';
import { toRequestError } from '@/shared/api/errors';
import type { components } from '@/shared/api/schema';

export type Catalog = components['schemas']['CatalogDto'];

/** The fixed lists of amenities and room types managers choose from. They only change with a release, so they are kept. */
export function useCatalog() {
  return useQuery({
    queryKey: ['catalog'],
    staleTime: Infinity,
    queryFn: async (): Promise<Catalog> => {
      const { data, error, response } = await api.GET('/catalog');
      if (!data) {
        throw toRequestError(response, error);
      }
      return data;
    },
  });
}
