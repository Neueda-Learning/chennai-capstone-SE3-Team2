"use strict";
var __decorate = (this && this.__decorate) || function (decorators, target, key, desc) {
    var c = arguments.length, r = c < 3 ? target : desc === null ? desc = Object.getOwnPropertyDescriptor(target, key) : desc, d;
    if (typeof Reflect === "object" && typeof Reflect.decorate === "function") r = Reflect.decorate(decorators, target, key, desc);
    else for (var i = decorators.length - 1; i >= 0; i--) if (d = decorators[i]) r = (c < 3 ? d(r) : c > 3 ? d(target, key, r) : d(target, key)) || r;
    return c > 3 && r && Object.defineProperty(target, key, r), r;
};
var __metadata = (this && this.__metadata) || function (k, v) {
    if (typeof Reflect === "object" && typeof Reflect.metadata === "function") return Reflect.metadata(k, v);
};
Object.defineProperty(exports, "__esModule", { value: true });
exports.Env = void 0;
const common_1 = require("@nestjs/common");
const config_1 = require("@nestjs/config");
let Env = class Env {
    config;
    constructor(config) {
        this.config = config;
    }
    required(key) {
        const value = this.config.get(key);
        if (!value) {
            throw new Error(`${key} is not set`);
        }
        return value;
    }
    get jwtSecret() { return this.required('JWT_SECRET'); }
    get jwtIssuer() { return this.config.get('JWT_ISSUER') ?? 'auth-service'; }
    get port() { return Number(this.config.get('PORT') ?? 3000); }
    get databaseUrl() { return this.required('AUTH_DATABASE_URL'); }
};
exports.Env = Env;
exports.Env = Env = __decorate([
    (0, common_1.Injectable)(),
    __metadata("design:paramtypes", [config_1.ConfigService])
], Env);
//# sourceMappingURL=env.js.map