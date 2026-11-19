import * as yup from 'yup';

// Password policy shared by the registration and the password reset forms
const UPPERCASE = /[A-Z]/;
const LOWERCASE = /[a-z]/;
const DIGIT = /\d/;
const SPECIAL_CHARACTER = /[!@#$%^&*()_+\-=[\]{}|;:,.<>?]/;

export const passwordSchema = yup
  .string()
  .required('Password is required')
  .min(8, 'Password must be at least 8 characters')
  .max(128, 'Password must not exceed 128 characters')
  .matches(UPPERCASE, 'Password must contain at least one uppercase letter')
  .matches(LOWERCASE, 'Password must contain at least one lowercase letter')
  .matches(DIGIT, 'Password must contain at least one number')
  .matches(SPECIAL_CHARACTER, 'Password must contain at least one special character');

export const confirmPasswordSchema = yup
  .string()
  .required('Please confirm your password')
  .oneOf([yup.ref('password')], 'Passwords must match');

export interface PasswordRequirement {
  label: string;
  test: (password: string) => boolean;
}

export const passwordRequirements: PasswordRequirement[] = [
  { label: 'At least 8 characters', test: (pwd) => pwd.length >= 8 },
  { label: 'One uppercase letter', test: (pwd) => UPPERCASE.test(pwd) },
  { label: 'One lowercase letter', test: (pwd) => LOWERCASE.test(pwd) },
  { label: 'One number', test: (pwd) => DIGIT.test(pwd) },
  { label: 'One special character', test: (pwd) => SPECIAL_CHARACTER.test(pwd) },
];
