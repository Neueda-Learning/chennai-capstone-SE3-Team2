import { ConfigService } from '@nestjs/config';
export declare class Env {
    private readonly config;
    constructor(config: ConfigService);
    private required;
    get jwtSecret(): string;
    get jwtIssuer(): string;
    get port(): number;
    get databaseUrl(): string;
}
