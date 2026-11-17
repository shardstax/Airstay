import { zodResolver } from '@hookform/resolvers/zod';
import { useForm } from 'react-hook-form';
import { Link, useLocation } from 'react-router-dom';

import { Button } from '@/shared/ui/Button';
import { Input } from '@/shared/ui/Input';

import { AuthLayout } from './AuthLayout';
import { FormError } from './FormError';
import { describeAuthError } from './messages';
import { loginSchema, type LoginValues } from './schemas';
import { useLogin } from './useAuthActions';

interface LoginState {
  reason?: unknown;
}

export function LoginPage() {
  const location = useLocation();
  // Carried to the other form so "where the user was heading" survives a switch between sign in and sign up.
  const carriedState: unknown = location.state;
  const login = useLogin();
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<LoginValues>({ resolver: zodResolver(loginSchema) });

  const expired = (location.state as LoginState | null)?.reason === 'expired';

  return (
    <AuthLayout
      title="Sign in"
      subtitle="Welcome back. Sign in to see and manage your bookings."
      footer={
        <>
          New to AirStay?{' '}
          <Link
            to="/signup"
            state={carriedState}
            className="font-medium text-brand-700 underline underline-offset-4"
          >
            Create an account
          </Link>
        </>
      }
    >
      <form
        noValidate
        className="flex flex-col gap-4"
        onSubmit={(event) => {
          void handleSubmit((values) => {
            login.mutate(values);
          })(event);
        }}
      >
        {expired && !login.isError ? (
          <p role="status" className="text-sm text-ink-soft">
            Your session has expired. Please sign in again.
          </p>
        ) : null}
        {login.isError ? <FormError message={describeAuthError(login.error, 'login')} /> : null}

        <Input
          label="Email"
          type="email"
          autoComplete="email"
          error={errors.email?.message}
          {...register('email')}
        />
        <Input
          label="Password"
          type="password"
          autoComplete="current-password"
          error={errors.password?.message}
          {...register('password')}
        />

        <Button type="submit" loading={login.isPending}>
          Sign in
        </Button>
      </form>
    </AuthLayout>
  );
}
