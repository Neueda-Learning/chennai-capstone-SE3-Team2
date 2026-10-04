import { Component } from '@angular/core';
import { Dashboard } from '../dashboard/dashboard';

@Component({
  selector: 'app-stocks-dashboard',
  imports: [Dashboard],
  template: '<app-dashboard segment="stocks"></app-dashboard>',
})
export class StocksDashboard {}
