const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export function validateEmail(email: string) {
  if (!email.trim()) return 'Enter your email address.';
  if (!EMAIL_PATTERN.test(email.trim())) return 'Enter a valid email address.';
  return undefined;
}

export function validateLogin(values: { email: string; password: string }) {
  return {
    email: validateEmail(values.email),
    password: values.password ? undefined : 'Enter your password.',
  };
}

export interface RegistrationValues {
  firstName: string;
  lastName: string;
  email: string;
  password: string;
  confirmPassword: string;
}

export function validateRegistration(values: RegistrationValues) {
  return {
    firstName: values.firstName.trim()
      ? values.firstName.trim().length <= 100
        ? undefined
        : 'First name must be 100 characters or fewer.'
      : 'Enter your first name.',
    lastName: values.lastName.trim()
      ? values.lastName.trim().length <= 100
        ? undefined
        : 'Last name must be 100 characters or fewer.'
      : 'Enter your last name.',
    email: validateEmail(values.email),
    password:
      values.password.length < 10
        ? 'Use at least 10 characters.'
        : values.password.length > 72
          ? 'Use no more than 72 characters.'
          : undefined,
    confirmPassword:
      values.password === values.confirmPassword
        ? undefined
        : 'Passwords do not match.',
  };
}
