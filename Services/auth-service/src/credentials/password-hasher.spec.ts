import { PasswordHasher, HASH_OPTIONS } from './password-hasher';

describe('PasswordHasher', () => {
  const hasher = new PasswordHasher();
  const password = 'correct horse battery staple';

  it('verifies a correct password', async () => {
    const hash = await hasher.hash(password);
    await expect(hasher.verify(hash, password)).resolves.toBe(true);
  });

  it('fails an incorrect password', async () => {
    const hash = await hasher.hash(password);
    await expect(hasher.verify(hash, 'not the password')).resolves.toBe(false);
  });

  it('does not use a general-purpose digest', async () => {
    const hash = await hasher.hash(password);

    // argon2id, not MD5 or any SHA. The encoded form names the algorithm.
    expect(hash.startsWith('$argon2id$')).toBe(true);
    expect(hash).not.toMatch(/^[a-f0-9]{32}$/);  // MD5
    expect(hash).not.toMatch(/^[a-f0-9]{64}$/);  // SHA-256
  });

  it('salts, so the same password hashes differently every time', async () => {
    expect(await hasher.hash(password)).not.toBe(await hasher.hash(password));
  });

  it('uses the cost parameters we measured, not the library defaults', async () => {
    const hash = await hasher.hash(password);
    expect(hash).toContain(`m=${HASH_OPTIONS.memoryCost}`);
    expect(hash).toContain(`t=${HASH_OPTIONS.timeCost}`);
    expect(hash).toContain(`p=${HASH_OPTIONS.parallelism}`);
  });

  it('returns false for a malformed stored hash rather than throwing', async () => {
    await expect(hasher.verify('not-a-hash', password)).resolves.toBe(false);
  });
});
