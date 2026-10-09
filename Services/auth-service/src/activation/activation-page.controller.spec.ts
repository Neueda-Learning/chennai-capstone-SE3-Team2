import { INestApplication } from '@nestjs/common';
import { Test } from '@nestjs/testing';
import { configureApp } from '../app-setup';
import { AuthService } from '../auth/auth.service';
import { PlatformError } from '../common/platform-error';
import { Env } from '../config/env';
import { ActivationPageController } from './activation-page.controller';
import { ActivationTokenService } from './activation-token.service';

const TOKEN = 'cd'.repeat(32);
const HOME = 'http://localhost:4200/';

describe('the activation link pages', () => {
  let app: INestApplication;
  let base: string;
  let register: jest.Mock;
  let isUsable: jest.Mock;

  beforeEach(async () => {
    register = jest.fn().mockResolvedValue({ id: 'x', username: 'priya.menon', accountId: 7, roles: ['CUSTOMER'] });
    isUsable = jest.fn().mockResolvedValue(true);

    const module = await Test.createTestingModule({
      controllers: [ActivationPageController],
      providers: [
        { provide: AuthService, useValue: { register } },
        { provide: ActivationTokenService, useValue: { isUsable } },
        { provide: Env, useValue: { activationHomeUrl: HOME } },
      ],
    }).compile();

    app = module.createNestApplication({ logger: false });
    configureApp(app);
    await app.listen(0, '127.0.0.1');
    base = await app.getUrl();
  });

  afterEach(() => app.close());

  const submit = (fields: Record<string, string>) =>
    fetch(`${base}/activate`, { method: 'POST', body: new URLSearchParams(fields), redirect: 'manual' });

  describe('GET /activate', () => {
    it('shows the form for a usable token, without using it up', async () => {
      const res = await fetch(`${base}/activate?token=${TOKEN}`);
      const html = await res.text();

      expect(res.status).toBe(200);
      expect(html).toContain('<form method="post" action="/activate">');
      expect(html).toContain(`name="token" value="${TOKEN}"`);
      expect(isUsable).toHaveBeenCalledWith(TOKEN);
      expect(register).not.toHaveBeenCalled();
    });

    it('does not leak the token onward: no referrer, no cache, no framing', async () => {
      const res = await fetch(`${base}/activate?token=${TOKEN}`);

      expect(res.headers.get('referrer-policy')).toBe('no-referrer');
      expect(res.headers.get('cache-control')).toBe('no-store');
      expect(res.headers.get('content-security-policy')).toContain("frame-ancestors 'none'");
    });

    it('shows one identical page for an unusable, a malformed and a missing token', async () => {
      isUsable.mockResolvedValue(false);

      const pages = await Promise.all(
        [`?token=${TOKEN}`, '?token=not-a-token', ''].map(async (q) => (await fetch(`${base}/activate${q}`)).text()),
      );

      expect(pages[0]).toContain('This link is not valid');
      expect(new Set(pages).size).toBe(1);
    });
  });

  describe('GET /activate/done', () => {
    it('confirms the login and links on to the home page', async () => {
      const res = await fetch(`${base}/activate/done`);
      const html = await res.text();

      expect(res.status).toBe(200);
      expect(html).toContain('Your login is ready');
      expect(html).toContain(`<a href="${HOME}">`);
      expect(res.headers.get('content-security-policy')).toContain("frame-ancestors 'none'");
      expect(register).not.toHaveBeenCalled();
    });
  });

  describe('POST /activate', () => {
    const fields = { token: TOKEN, username: 'priya.menon', password: 'correct horse battery staple' };

    it('registers, then redirects on this origin to the confirmation page', async () => {
      const res = await submit(fields);

      expect(res.status).toBe(303);
      // Not HOME: form-action 'self' covers the redirect, and a browser
      // silently drops one to another origin.
      expect(res.headers.get('location')).toBe('/activate/done');
      expect(register).toHaveBeenCalledWith(
        expect.objectContaining({ username: 'priya.menon', password: 'correct horse battery staple', activationToken: TOKEN }),
      );
    });

    it('shows the invalid-link page when the token is refused', async () => {
      register.mockRejectedValue(PlatformError.unauthorised());

      const res = await submit(fields);

      expect(res.status).toBe(200);
      expect(await res.text()).toContain('This link is not valid');
    });

    it('keeps the form, and the token, when the username is taken', async () => {
      register.mockRejectedValue(PlatformError.usernameTaken());

      const html = await (await submit(fields)).text();

      expect(html).toContain('That username is taken');
      expect(html).toContain(`name="token" value="${TOKEN}"`);
    });

    it('refuses a short password without calling register', async () => {
      const res = await submit({ ...fields, password: 'short' });

      expect(res.status).toBe(422);
      expect(register).not.toHaveBeenCalled();
    });

    it('escapes what it echoes back', async () => {
      const html = await (await submit({ ...fields, username: '<script>alert(1)</script>' })).text();

      expect(html).not.toContain('<script>alert(1)</script>');
      expect(html).toContain('&lt;script&gt;alert(1)&lt;/script&gt;');
    });
  });
});
