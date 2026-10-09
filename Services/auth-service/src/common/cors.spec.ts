import { Controller, Get, INestApplication } from '@nestjs/common';
import { Test } from '@nestjs/testing';
import { corsOptions } from './cors';

@Controller('auth')
class ProbeController {
  @Get('me')
  me(): { ok: boolean } {
    return { ok: true };
  }
}

const UI = 'http://localhost:4200';

describe('CORS for the trading UI', () => {
  let app: INestApplication;
  let base: string;

  beforeAll(async () => {
    const module = await Test.createTestingModule({ controllers: [ProbeController] }).compile();
    app = module.createNestApplication({ logger: false });
    app.enableCors(corsOptions([UI]));
    await app.listen(0, '127.0.0.1');
    base = await app.getUrl();
  });

  afterAll(() => app.close());

  const preflight = (origin: string) =>
    fetch(`${base}/auth/me`, {
      method: 'OPTIONS',
      headers: {
        Origin: origin,
        'Access-Control-Request-Method': 'GET',
        'Access-Control-Request-Headers': 'authorization',
      },
    });

  it('answers a preflight from the UI origin, allowing the Authorization header', async () => {
    const res = await preflight(UI);

    expect(res.status).toBe(204);
    expect(res.headers.get('access-control-allow-origin')).toBe(UI);
    expect(res.headers.get('access-control-allow-headers')).toContain('Authorization');
  });

  it('gives any other origin no Access-Control-Allow-Origin, so its browser cannot read the response', async () => {
    const res = await preflight('https://evil.example');

    expect(res.headers.get('access-control-allow-origin')).toBeNull();
  });

  it('marks a real response for the UI origin, and sends no credentials header', async () => {
    const res = await fetch(`${base}/auth/me`, { headers: { Origin: UI } });

    expect(res.headers.get('access-control-allow-origin')).toBe(UI);
    expect(res.headers.get('access-control-allow-credentials')).toBeNull();
  });
});
