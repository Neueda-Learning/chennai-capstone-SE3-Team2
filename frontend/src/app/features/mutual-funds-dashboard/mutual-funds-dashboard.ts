import { Component } from '@angular/core';
import { Dashboard } from '../dashboard/dashboard';

@Component({
  selector: 'app-mutual-funds-dashboard',
  imports: [Dashboard],
  template: '<app-dashboard segment="mutual-funds"></app-dashboard>',
})
export class MutualFundsDashboard {}
