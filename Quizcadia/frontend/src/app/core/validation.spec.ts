import { FormControl, FormGroup } from '@angular/forms';
import { describe, expect, it } from 'vitest';
import { emailValidator, fieldError, matches, MESSAGES, nameValidator, passwordValidator } from './validation';

const check = (v: (c: FormControl) => unknown, value: string) => v(new FormControl(value));

describe('input rules (same as the login service)', () => {
  it('names: 1 to 60 characters of letters in any language, numbers, spaces and . \' _ -', () => {
    for (const ok of ['D', 'Ada', 'Ada Lovelace', 'José Álvarez', "O'Brien", 'anne-marie', 'player_1', 'Dr. Who', 'Zoë',
      '李小龍', 'محمد', '2fast', '  Visitor  ', 'a'.repeat(60)]) {
      expect(check(nameValidator, ok), ok).toBeNull();
    }
    for (const bad of ['<script>', 'ada@home', '-ada', '.', 'a/b', 'tab\tname', '🦊 Fox']) {
      expect(check(nameValidator, bad), bad).toEqual({ pattern: MESSAGES.nameChars });
    }
    expect(check(nameValidator, 'a'.repeat(61))).toEqual({ maxlength: MESSAGES.nameSize });
    expect(check(nameValidator, '   ')).toEqual({ required: MESSAGES.nameRequired });
  });

  it('emails: sign-up wants name@domain.tld; signing in only the shape, so older accounts still work', () => {
    const signUp = emailValidator(true);
    const signIn = emailValidator(false);
    expect(check(signUp, ' ada@example.test ')).toBeNull();
    for (const bad of ['not-an-email', 'ada@localhost', 'ada example@x.test', '@x.test', 'ada@']) {
      expect(check(signUp, bad), bad).toEqual({ email: MESSAGES.emailInvalid });
    }
    expect(check(signIn, 'admin@localhost')).toBeNull();
    expect(check(signIn, 'short')).toEqual({ email: MESSAGES.emailInvalid });
    expect(check(signUp, '')).toEqual({ required: MESSAGES.emailRequired });
    expect(check(signUp, 'a'.repeat(250) + '@x.test')).toEqual({ maxlength: MESSAGES.emailSize });
  });

  it('passwords: a new one needs 8 to 72 characters; the current one only has to be there', () => {
    const fresh = passwordValidator('new');
    const current = passwordValidator('current');
    expect(check(fresh, '12345678')).toBeNull();
    expect(check(fresh, 'x'.repeat(72))).toBeNull();
    expect(check(fresh, '1234567')).toEqual({ length: MESSAGES.passwordSize });
    expect(check(fresh, 'x'.repeat(73))).toEqual({ length: MESSAGES.passwordSize });
    expect(check(fresh, '        ')).toEqual({ required: MESSAGES.passwordRequired });
    expect(check(current, 'x')).toBeNull();
    expect(check(current, '')).toEqual({ required: MESSAGES.currentPasswordRequired });
    expect(check(current, 'x'.repeat(73))).toEqual({ maxlength: MESSAGES.passwordTooLong });
  });

  it('confirm: required, then equal to the password', () => {
    const form = new FormGroup({ password: new FormControl('secret-1'), confirm: new FormControl('', matches('password')) });
    const confirm = form.controls.confirm;
    expect(confirm.errors).toEqual({ required: MESSAGES.confirmRequired });
    confirm.setValue('secret-2');
    expect(confirm.errors).toEqual({ mismatch: MESSAGES.mismatch });
    confirm.setValue('secret-1');
    expect(confirm.errors).toBeNull();
  });

  it('shows a message only once the field was left or the form submitted, and never for a hidden field', () => {
    const c = new FormControl('', nameValidator);
    expect(fieldError(c, false)).toBeNull();
    expect(fieldError(c, true)).toBe(MESSAGES.nameRequired);
    c.markAsTouched();
    expect(fieldError(c, false)).toBe(MESSAGES.nameRequired);
    c.disable();
    expect(fieldError(c, true)).toBeNull();
  });
});
