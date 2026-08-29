import { useMutation } from '@tanstack/react-query';
import { type FormEvent, useId, useRef, useState } from 'react';
import { Link } from 'react-router-dom';

import { Container, Section } from '../components/layout';
import { Alert, Button, FieldError, Input, Label } from '../components/ui';
import {
  authService,
  presentAuthenticationError,
  type RegistrationValues,
  validateLogin,
  validateRegistration,
} from '../features/auth';

type FieldErrors<T> = Partial<Record<keyof T, string>>;

function FormField({
  children,
  error,
  htmlFor,
  label,
}: {
  children: React.ReactNode;
  error?: string;
  htmlFor: string;
  label: string;
}) {
  return (
    <div className="auth-field">
      <Label htmlFor={htmlFor}>{label}</Label>
      {children}
      {error ? <FieldError id={`${htmlFor}-error`}>{error}</FieldError> : null}
    </div>
  );
}

function AuthenticationLayout({
  children,
  description,
  title,
}: {
  children: React.ReactNode;
  description: string;
  title: string;
}) {
  return (
    <Section className="auth-section">
      <Container size="small">
        <div className="auth-layout">
          <header className="auth-heading">
            <p className="auth-eyebrow">EventPass account</p>
            <h1>{title}</h1>
            <p>{description}</p>
          </header>
          <div className="auth-panel">{children}</div>
        </div>
      </Container>
    </Section>
  );
}

export function LoginPage() {
  const emailId = useId();
  const passwordId = useId();
  const submitting = useRef(false);
  const [values, setValues] = useState({ email: '', password: '' });
  const [errors, setErrors] = useState<FieldErrors<typeof values>>({});
  const [formError, setFormError] = useState<string>();
  const mutation = useMutation({
    mutationFn: (request: Parameters<typeof authService.login>[0]) =>
      authService.login(request),
    retry: false,
  });

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (submitting.current || mutation.isPending) return;

    const nextErrors = validateLogin(values);
    setErrors(nextErrors);
    setFormError(undefined);
    if (Object.values(nextErrors).some(Boolean)) return;

    submitting.current = true;
    try {
      await mutation.mutateAsync({
        email: values.email.trim().toLowerCase(),
        password: values.password,
      });
    } catch (error) {
      setFormError(presentAuthenticationError(error).message);
    } finally {
      submitting.current = false;
    }
  }

  return (
    <AuthenticationLayout
      title="Welcome back"
      description="Sign in to continue to your events."
    >
      {mutation.isSuccess ? (
        <Alert title="Sign-in successful" tone="success">
          Your credentials were accepted. Secure session activation will be
          completed in the next authentication milestone.
        </Alert>
      ) : null}
      {formError ? (
        <Alert title="We couldn't sign you in" tone="error">
          {formError}
        </Alert>
      ) : null}
      <form
        className="auth-form"
        noValidate
        onSubmit={(event) => void submit(event)}
      >
        <FormField error={errors.email} htmlFor={emailId} label="Email">
          <Input
            id={emailId}
            name="email"
            type="email"
            autoComplete="email"
            inputMode="email"
            value={values.email}
            disabled={mutation.isPending}
            aria-invalid={Boolean(errors.email)}
            aria-describedby={errors.email ? `${emailId}-error` : undefined}
            onChange={(event) =>
              setValues((current) => ({
                ...current,
                email: event.target.value,
              }))
            }
          />
        </FormField>
        <FormField
          error={errors.password}
          htmlFor={passwordId}
          label="Password"
        >
          <Input
            id={passwordId}
            name="password"
            type="password"
            autoComplete="current-password"
            value={values.password}
            disabled={mutation.isPending}
            aria-invalid={Boolean(errors.password)}
            aria-describedby={
              errors.password ? `${passwordId}-error` : undefined
            }
            onChange={(event) =>
              setValues((current) => ({
                ...current,
                password: event.target.value,
              }))
            }
          />
        </FormField>
        <Button
          className="auth-submit"
          type="submit"
          size="large"
          loading={mutation.isPending}
          loadingLabel="Signing in..."
        >
          Sign in
        </Button>
      </form>
      <p className="auth-alternative">
        Don't have an account? <Link to="/register">Create account</Link>
      </p>
    </AuthenticationLayout>
  );
}

export function RegistrationPage() {
  const ids = {
    firstName: useId(),
    lastName: useId(),
    email: useId(),
    password: useId(),
    confirmPassword: useId(),
  };
  const submitting = useRef(false);
  const [values, setValues] = useState<RegistrationValues>({
    firstName: '',
    lastName: '',
    email: '',
    password: '',
    confirmPassword: '',
  });
  const [errors, setErrors] = useState<FieldErrors<RegistrationValues>>({});
  const [formError, setFormError] = useState<string>();
  const mutation = useMutation({
    mutationFn: (request: Parameters<typeof authService.register>[0]) =>
      authService.register(request),
    retry: false,
  });

  function update(field: keyof RegistrationValues, value: string) {
    setValues((current) => ({ ...current, [field]: value }));
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (submitting.current || mutation.isPending) return;

    const nextErrors = validateRegistration(values);
    setErrors(nextErrors);
    setFormError(undefined);
    if (Object.values(nextErrors).some(Boolean)) return;

    submitting.current = true;
    try {
      await mutation.mutateAsync({
        firstName: values.firstName.trim(),
        lastName: values.lastName.trim(),
        email: values.email.trim().toLowerCase(),
        password: values.password,
      });
    } catch (error) {
      const presentation = presentAuthenticationError(error);
      if (presentation.field === 'email') {
        setErrors((current) => ({
          ...current,
          email: presentation.message,
        }));
      } else {
        setFormError(presentation.message);
      }
    } finally {
      submitting.current = false;
    }
  }

  if (mutation.isSuccess) {
    return (
      <AuthenticationLayout
        title="Your account is ready"
        description="EventPass accepted your registration and issued your initial credentials."
      >
        <div className="auth-success" role="status">
          <h2>Account created successfully</h2>
          <p>
            Continue to sign in. Complete session restoration and refresh will
            be connected in the next authentication milestone.
          </p>
          <Link
            className="button link-button"
            data-size="large"
            data-variant="primary"
            to="/login"
          >
            Sign in
          </Link>
        </div>
      </AuthenticationLayout>
    );
  }

  return (
    <AuthenticationLayout
      title="Create your account"
      description="Book confidently and keep every EventPass ticket in one place."
    >
      {formError ? (
        <Alert title="We couldn't create your account" tone="error">
          {formError}
        </Alert>
      ) : null}
      <form
        className="auth-form"
        noValidate
        onSubmit={(event) => void submit(event)}
      >
        <div className="auth-name-grid">
          {(['firstName', 'lastName'] as const).map((field) => (
            <FormField
              key={field}
              error={errors[field]}
              htmlFor={ids[field]}
              label={field === 'firstName' ? 'First name' : 'Last name'}
            >
              <Input
                id={ids[field]}
                name={field}
                type="text"
                autoComplete={
                  field === 'firstName' ? 'given-name' : 'family-name'
                }
                maxLength={100}
                value={values[field]}
                disabled={mutation.isPending}
                aria-invalid={Boolean(errors[field])}
                aria-describedby={
                  errors[field] ? `${ids[field]}-error` : undefined
                }
                onChange={(event) => update(field, event.target.value)}
              />
            </FormField>
          ))}
        </div>
        <FormField error={errors.email} htmlFor={ids.email} label="Email">
          <Input
            id={ids.email}
            name="email"
            type="email"
            autoComplete="email"
            inputMode="email"
            value={values.email}
            disabled={mutation.isPending}
            aria-invalid={Boolean(errors.email)}
            aria-describedby={errors.email ? `${ids.email}-error` : undefined}
            onChange={(event) => update('email', event.target.value)}
          />
        </FormField>
        <FormField
          error={errors.password}
          htmlFor={ids.password}
          label="Password"
        >
          <Input
            id={ids.password}
            name="password"
            type="password"
            autoComplete="new-password"
            minLength={10}
            maxLength={72}
            value={values.password}
            disabled={mutation.isPending}
            aria-invalid={Boolean(errors.password)}
            aria-describedby={`${ids.password}-guidance${errors.password ? ` ${ids.password}-error` : ''}`}
            onChange={(event) => update('password', event.target.value)}
          />
          <p className="auth-guidance" id={`${ids.password}-guidance`}>
            Use 10–72 characters.
          </p>
        </FormField>
        <FormField
          error={errors.confirmPassword}
          htmlFor={ids.confirmPassword}
          label="Confirm password"
        >
          <Input
            id={ids.confirmPassword}
            name="confirmPassword"
            type="password"
            autoComplete="new-password"
            value={values.confirmPassword}
            disabled={mutation.isPending}
            aria-invalid={Boolean(errors.confirmPassword)}
            aria-describedby={
              errors.confirmPassword
                ? `${ids.confirmPassword}-error`
                : undefined
            }
            onChange={(event) => update('confirmPassword', event.target.value)}
          />
        </FormField>
        <Button
          className="auth-submit"
          type="submit"
          size="large"
          loading={mutation.isPending}
          loadingLabel="Creating account..."
        >
          Create account
        </Button>
      </form>
      <p className="auth-alternative">
        Already have an account? <Link to="/login">Sign in</Link>
      </p>
    </AuthenticationLayout>
  );
}
