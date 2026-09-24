"use strict";
var __decorate = (this && this.__decorate) || function (decorators, target, key, desc) {
    var c = arguments.length, r = c < 3 ? target : desc === null ? desc = Object.getOwnPropertyDescriptor(target, key) : desc, d;
    if (typeof Reflect === "object" && typeof Reflect.decorate === "function") r = Reflect.decorate(decorators, target, key, desc);
    else for (var i = decorators.length - 1; i >= 0; i--) if (d = decorators[i]) r = (c < 3 ? d(r) : c > 3 ? d(target, key, r) : d(target, key)) || r;
    return c > 3 && r && Object.defineProperty(target, key, r), r;
};
Object.defineProperty(exports, "__esModule", { value: true });
exports.DatabaseModule = exports.AUTH_POOL = void 0;
const common_1 = require("@nestjs/common");
const pg_1 = require("pg");
const env_1 = require("../config/env");
exports.AUTH_POOL = 'AUTH_POOL';
let DatabaseModule = class DatabaseModule {
};
exports.DatabaseModule = DatabaseModule;
exports.DatabaseModule = DatabaseModule = __decorate([
    (0, common_1.Global)(),
    (0, common_1.Module)({
        providers: [
            env_1.Env,
            {
                provide: exports.AUTH_POOL,
                inject: [env_1.Env],
                useFactory: (env) => new pg_1.Pool({ connectionString: env.databaseUrl, max: 5 }),
            },
        ],
        exports: [exports.AUTH_POOL, env_1.Env],
    })
], DatabaseModule);
//# sourceMappingURL=database.module.js.map