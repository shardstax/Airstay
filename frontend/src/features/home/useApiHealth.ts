import { useQuery } from '@tanstack/react-query';

type Health = 'UP' | 'DOWN';

function isUp(body: unknown): boolean {
  return typeof body === 'object' && body !== null && 'status' in body && body.status === 'UP';
}

/** 'UP' when the backend's health endpoint answers UP, 'DOWN' for anything else (including no answer). */
export function useApiHealth() {
  return useQuery({
    queryKey: ['api-health'],
    queryFn: async (): Promise<Health> => {
      try {
        const response = await fetch('/api/v1/actuator/health');
        const body: unknown = await response.json();
        return response.ok && isUp(body) ? 'UP' : 'DOWN';
      } catch {
        return 'DOWN';
      }
    },
    retry: false,
  });
}
