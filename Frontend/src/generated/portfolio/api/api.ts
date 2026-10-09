export * from './operations.service';
import { OperationsService } from './operations.service';
export * from './pl.service';
import { PLService } from './pl.service';
export * from './portfolio.service';
import { PortfolioService } from './portfolio.service';
export const APIS = [OperationsService, PLService, PortfolioService];
