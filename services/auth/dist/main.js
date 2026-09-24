"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const common_1 = require("@nestjs/common");
const core_1 = require("@nestjs/core");
const app_module_1 = require("./app.module");
const all_exceptions_filter_1 = require("./common/all-exceptions.filter");
const platform_error_1 = require("./common/platform-error");
const env_1 = require("./config/env");
async function bootstrap() {
    const app = await core_1.NestFactory.create(app_module_1.AppModule);
    app.useGlobalFilters(new all_exceptions_filter_1.AllExceptionsFilter());
    app.useGlobalPipes(new common_1.ValidationPipe({
        whitelist: true,
        forbidNonWhitelisted: true,
        transform: true,
        exceptionFactory: (errors) => platform_error_1.PlatformError.invalidInput(errors.map((e) => Object.values(e.constraints ?? {}).join(', ')).join('; ')),
    }));
    const env = app.get(env_1.Env);
    await app.listen(env.port);
}
void bootstrap();
//# sourceMappingURL=main.js.map