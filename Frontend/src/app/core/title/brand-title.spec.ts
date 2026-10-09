import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Title } from '@angular/platform-browser';
import { Router, TitleStrategy, provideRouter } from '@angular/router';
import { BrandTitle } from './brand-title';

@Component({ template: '' })
class Blank {}

describe('BrandTitle', () => {
  it("names the screen, then the product, in the browser's tab", async () => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([
          { path: 'holdings', title: 'Holdings', component: Blank },
          { path: 'untitled', component: Blank },
        ]),
        { provide: TitleStrategy, useClass: BrandTitle },
      ],
    });
    const router = TestBed.inject(Router);
    const title = TestBed.inject(Title);

    await router.navigateByUrl('/holdings');
    expect(title.getTitle()).toBe('Holdings · Yellow Trade');

    await router.navigateByUrl('/untitled');
    expect(title.getTitle()).toBe('Yellow Trade');
  });
});
