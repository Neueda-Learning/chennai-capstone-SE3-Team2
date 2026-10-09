import { Injectable, inject } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { RouterStateSnapshot, TitleStrategy } from '@angular/router';

/** The browser tab: the screen's own title, then the product, so a tab among many says whose it is. */
@Injectable({ providedIn: 'root' })
export class BrandTitle extends TitleStrategy {
  private readonly title = inject(Title);

  override updateTitle(snapshot: RouterStateSnapshot): void {
    const screen = this.buildTitle(snapshot);
    this.title.setTitle(screen ? `${screen} · Yellow Trade` : 'Yellow Trade');
  }
}
