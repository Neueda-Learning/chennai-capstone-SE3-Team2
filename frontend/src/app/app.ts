import { Component } from '@angular/core';
import { RouterLink, RouterOutlet } from '@angular/router';

/** The shell: a header, and the routed feature below it. */
@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink],
  templateUrl: './app.html',
  styleUrl: './app.css',
})
export class App {}
