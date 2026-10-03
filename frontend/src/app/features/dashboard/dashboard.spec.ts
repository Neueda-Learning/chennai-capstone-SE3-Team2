import { TestBed } from '@angular/core/testing';
import { Dashboard } from './dashboard';

describe('Dashboard', () => {
  it('renders its heading', async () => {
    const fixture = TestBed.createComponent(Dashboard);
    await fixture.whenStable();

    expect((fixture.nativeElement as HTMLElement).querySelector('h1')?.textContent).toBe('Dashboard');
  });
});
