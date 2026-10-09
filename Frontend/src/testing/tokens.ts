/**
 * Test-only: a JWT-shaped token carrying the claims the Auth service issues.
 * The signature is a placeholder -- nothing in the browser verifies it.
 */
export function testToken(
  claims: Partial<{ sub: string; accountId: number; roles: string[]; exp: number }> = {},
): string {
  const payload = {
    sub: 'b1946ac9-2b1f-4a4f-9c0d-2f7a1b3c4d5e',
    accountId: 3,
    roles: ['CUSTOMER'],
    iat: Math.floor(Date.now() / 1000),
    exp: Math.floor(Date.now() / 1000) + 900,
    iss: 'auth-service',
    ...claims,
  };
  const encode = (value: object) =>
    btoa(JSON.stringify(value)).replace(/=+$/, '').replace(/\+/g, '-').replace(/\//g, '_');
  return `${encode({ alg: 'HS256', typ: 'JWT' })}.${encode(payload)}.test-signature`;
}
