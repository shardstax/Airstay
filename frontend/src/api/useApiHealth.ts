import { useQuery } from '@tanstack/react-query';

/** 'UP' when the backend's health endpoint answers UP, 'DOWN' for anything else (including no answer). */
export function useApiHealth() {
  return useQuery({
    queryKey: ['api-health'],
    queryFn: async (): Promise<'UP' | 'DOWN'> => {
      try {
        const response = await fetch('/api/v1/actuator/health');
        const body: { status?: string } = await response.json();
        return response.ok && body.status === 'UP' ? 'UP' : 'DOWN';
      } catch {
        return 'DOWN';
      }
    },
    retry: false,
  });
}
