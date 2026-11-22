import { AbstractControl, ValidatorFn } from '@angular/forms';

/*
 * What players may type when they sign up, sign in or reset a password. The login service checks the same
 * rules with the same messages (login/.../api/InputRules.java; UserService ProfileChanges for profile names),
 * so keep the two identical. Each validator returns at most one error, whose value is the message to show.
 */

/** Longest player name, as the login and user services allow. */
export const NAME_MAX = 60;
/** Letters (any language), numbers, spaces and . ' _ -, starting with a letter or number. */
export const NAME_PATTERN = /^[\p{L}\p{M}\p{N}][\p{L}\p{M}\p{N} .'_-]*$/u;
/** Longest email address. */
export const EMAIL_MAX = 254;
/** New accounts: something@domain.tld. */
export const EMAIL_PATTERN = /^[^@\s]+@[^@\s]+\.[^@\s]+$/;
/** Signing in and "forgot password" only check the shape, so older accounts still work. */
export const EMAIL_LOOSE_PATTERN = /^[^@\s]+@[^@\s]+$/;
/** Shortest password for a new account. */
export const PASSWORD_MIN = 8;
/** Longest password: BCrypt only reads the first 72 bytes. */
export const PASSWORD_MAX = 72;

/** The form error messages, the same wording as the login service's. */
export const MESSAGES = {
  nameRequired: 'Enter a name.',
  nameSize: 'The name can be at most 60 characters.',
  nameChars: "The name can use letters, numbers, spaces and . ' _ - and must start with a letter or number.",
  emailRequired: 'Enter your email address.',
  emailInvalid: 'Enter a valid email address, like name@example.com.',
  emailSize: 'The email address can be at most 254 characters.',
  passwordRequired: 'Enter a password.',
  currentPasswordRequired: 'Enter your password.',
  passwordSize: 'The password needs 8 to 72 characters.',
  passwordTooLong: 'The password can be at most 72 characters.',
  confirmRequired: 'Type the password again.',
  mismatch: "The two passwords don't match.",
} as const;

const text = (c: AbstractControl): string => (typeof c.value === 'string' ? c.value : '');

/** Display name, trimmed: 1 to 60 characters from {@link NAME_PATTERN}. */
export const nameValidator: ValidatorFn = (c) => {
  const v = text(c).trim();
  if (!v) return { required: MESSAGES.nameRequired };
  if (v.length > NAME_MAX) return { maxlength: MESSAGES.nameSize };
  if (!NAME_PATTERN.test(v)) return { pattern: MESSAGES.nameChars };
  return null;
};

/** Email, trimmed. {@code strict} (sign-up) also wants a dot in the domain. */
export function emailValidator(strict: boolean): ValidatorFn {
  return (c) => {
    const v = text(c).trim();
    if (!v) return { required: MESSAGES.emailRequired };
    if (!(strict ? EMAIL_PATTERN : EMAIL_LOOSE_PATTERN).test(v)) return { email: MESSAGES.emailInvalid };
    if (v.length > EMAIL_MAX) return { maxlength: MESSAGES.emailSize };
    return null;
  };
}

/** A password being chosen (8 to 72 characters), or the current one when signing in (any length up to 72). */
export function passwordValidator(kind: 'new' | 'current'): ValidatorFn {
  return (c) => {
    const v = text(c);
    if (!v.trim()) return { required: kind === 'new' ? MESSAGES.passwordRequired : MESSAGES.currentPasswordRequired };
    if (kind === 'new' && (v.length < PASSWORD_MIN || v.length > PASSWORD_MAX)) return { length: MESSAGES.passwordSize };
    if (kind === 'current' && v.length > PASSWORD_MAX) return { maxlength: MESSAGES.passwordTooLong };
    return null;
  };
}

/** On the confirm control: required, then equal to the sibling {@code otherName} (re-checked when that one changes). */
export function matches(otherName: string): ValidatorFn {
  return (c) => {
    const v = text(c);
    if (!v) return { required: MESSAGES.confirmRequired };
    const other = c.parent?.get(otherName);
    return other && other.value !== v ? { mismatch: MESSAGES.mismatch } : null;
  };
}

/** The message to show under a control: once the player has left the field or tried to submit. */
export function fieldError(c: AbstractControl | null, submitted: boolean): string | null {
  if (!c || c.disabled || !c.errors || !(c.touched || submitted)) return null;
  const first = Object.values(c.errors)[0];
  return typeof first === 'string' ? first : null;
}
