"use strict";
var __decorate = (this && this.__decorate) || function (decorators, target, key, desc) {
    var c = arguments.length, r = c < 3 ? target : desc === null ? desc = Object.getOwnPropertyDescriptor(target, key) : desc, d;
    if (typeof Reflect === "object" && typeof Reflect.decorate === "function") r = Reflect.decorate(decorators, target, key, desc);
    else for (var i = decorators.length - 1; i >= 0; i--) if (d = decorators[i]) r = (c < 3 ? d(r) : c > 3 ? d(target, key, r) : d(target, key)) || r;
    return c > 3 && r && Object.defineProperty(target, key, r), r;
};
var AllExceptionsFilter_1;
Object.defineProperty(exports, "__esModule", { value: true });
exports.AllExceptionsFilter = void 0;
const common_1 = require("@nestjs/common");
const error_codes_1 = require("./error-codes");
let AllExceptionsFilter = AllExceptionsFilter_1 = class AllExceptionsFilter {
    log = new common_1.Logger(AllExceptionsFilter_1.name);
    catch(exception, host) {
        const response = host.switchToHttp().getResponse();
        if (exception instanceof common_1.HttpException) {
            const status = exception.getStatus();
            const body = exception.getResponse();
            if (typeof body === 'object' && body !== null && 'errorCode' in body) {
                response.status(status).json(body);
                return;
            }
            response.status(status).json({
                errorCode: status === common_1.HttpStatus.UNPROCESSABLE_ENTITY ? error_codes_1.ErrorCode.VAL_422 : error_codes_1.ErrorCode.AUTH_401,
                message: this.messageOf(body),
            });
            return;
        }
        this.log.error('unhandled exception', exception instanceof Error ? exception.stack : String(exception));
        response.status(common_1.HttpStatus.INTERNAL_SERVER_ERROR).json({
            errorCode: 'SRV-500',
            message: 'Internal server error',
        });
    }
    messageOf(body) {
        if (typeof body === 'string')
            return body;
        if (typeof body === 'object' && body !== null && 'message' in body) {
            const message = body.message;
            return Array.isArray(message) ? message.join('; ') : String(message);
        }
        return 'Request failed';
    }
};
exports.AllExceptionsFilter = AllExceptionsFilter;
exports.AllExceptionsFilter = AllExceptionsFilter = AllExceptionsFilter_1 = __decorate([
    (0, common_1.Catch)()
], AllExceptionsFilter);
//# sourceMappingURL=all-exceptions.filter.js.map