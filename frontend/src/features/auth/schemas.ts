import { z } from 'zod';

// These mirror the backend's Bean Validation rules (LoginDto, SignUpRequestDto), so most mistakes are caught before a request.
// The server stays the authority: it validates again and its answer is shown if it disagrees.
const email = z.email('Enter a valid email address.').max(255, 'Use at most 255 characters.');

export const loginSchema = z.object({
  email,
  password: z.string().min(1, 'Enter your password.'),
});

export const signupSchema = z.object({
  name: z.string().trim().min(1, 'Enter your name.').max(100, 'Use at most 100 characters.'),
  email,
  password: z.string().min(8, 'Use at least 8 characters.').max(72, 'Use at most 72 characters.'),
  // Ticked by someone who wants to list hotels: the account is created as a hotel manager.
  manager: z.boolean(),
});

export type LoginValues = z.infer<typeof loginSchema>;
export type SignupValues = z.infer<typeof signupSchema>;
