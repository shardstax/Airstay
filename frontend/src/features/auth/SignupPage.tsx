import { zodResolver } from '@hookform/resolvers/zod';
import { useForm } from 'react-hook-form';
import { Link, useLocation } from 'react-router-dom';

import { Button } from '@/shared/ui/Button';
import { Input } from '@/shared/ui/Input';

import { AuthLayout } from './AuthLayout';
import { FormError } from './FormError';
import { describeAuthError } from './messages';
import { signupSchema, type SignupValues } from './schemas';
import { useSignup } from './useAuthActions';

export function SignupPage() {
  const location = useLocation();
  // Carried to the other form so "where the user was heading" survives a switch between sign in and sign up.
  const carriedState: unknown = location.state;
  const signup = useSignup();
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<SignupValues>({ resolver: zodResolver(signupSchema) });

  return (
    <AuthLayout
      title="Create your account"
      subtitle="Book rooms in a few steps and keep your trips in one place."
      footer={
        <>
          Already have an account?{' '}
          <Link
            to="/login"
            state={carriedState}
            className="font-medium text-brand-700 underline underline-offset-4"
          >
            Sign in
          </Link>
        </>
      }
    >
      <form
        noValidate
        className="flex flex-col gap-4"
        onSubmit={(event) => {
          void handleSubmit((values) => {
            signup.mutate(values);
          })(event);
        }}
      >
        {signup.isError ? <FormError message={describeAuthError(signup.error, 'signup')} /> : null}

        <Input label="Full name" autoComplete="name" error={errors.name?.message} {...register('name')} />
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
          autoComplete="new-password"
          hint="8 to 72 characters."
          error={errors.password?.message}
          {...register('password')}
        />

        <Button type="submit" loading={signup.isPending}>
          Create account
        </Button>
      </form>
    </AuthLayout>
  );
}
