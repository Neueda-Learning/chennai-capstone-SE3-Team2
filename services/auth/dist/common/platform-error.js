"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.PlatformError = void 0;
const common_1 = require("@nestjs/common");
const error_codes_1 = require("./error-codes");
class PlatformError extends common_1.HttpException {
    errorCode;
    constructor(errorCode, message, status) {
        super({ errorCode, message }, status);
        this.errorCode = errorCode;
    }
    static unauthorised() {
        return new PlatformError(error_codes_1.ErrorCode.AUTH_401, error_codes_1.UNAUTHORISED_MESSAGE, common_1.HttpStatus.UNAUTHORIZED);
    }
    static usernameTaken() {
        return new PlatformError(error_codes_1.ErrorCode.AUTH_409, 'Username already registered', common_1.HttpStatus.CONFLICT);
    }
    static invalidInput(message) {
        return new PlatformError(error_codes_1.ErrorCode.VAL_422, message, common_1.HttpStatus.UNPROCESSABLE_ENTITY);
    }
}
exports.PlatformError = PlatformError;
//# sourceMappingURL=platform-error.js.map