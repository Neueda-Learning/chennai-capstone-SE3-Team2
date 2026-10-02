import { Body, Controller, Get, HttpStatus, Logger, Post, Query, Res } from '@nestjs/common';
import { ApiExcludeController } from '@nestjs/swagger';
import { plainToInstance } from 'class-transformer';
import { validate } from 'class-validator';
import { Response } from 'express';
import { AuthService } from '../auth/auth.service';
import { RegisterDto } from '../auth/dto/register.dto';
import { ErrorCode } from '../common/error-codes';
import { PlatformError } from '../common/platform-error';
import { Env } from '../config/env';
import { ActivationTokenService } from './activation-token.service';
import { errorPage, invalidLinkPage, PAGE_HEADERS, registrationForm } from './activation-pages';

const TOKEN_FORMAT = /^[0-9a-f]{64}$/;

/**
 * Where the activation email's link lands. A browser page, not an API route,
 * so it is kept out of the OpenAPI document. Registration itself goes through
 * the same AuthService.register as POST /auth/register.
 */
@ApiExcludeController()
@Controller('activate')
export class ActivationPageController {
  private readonly log = new Logger(ActivationPageController.name);

  constructor(
    private readonly auth: AuthService,
    private readonly tokens: ActivationTokenService,
    private readonly env: Env,
  ) {}

  /** Checks the link without using it up, then shows the form or the one refusal page. */
  @Get()
  async show(@Query('token') token: string | undefined, @Res() res: Response): Promise<void> {
    if (typeof token !== 'string' || !TOKEN_FORMAT.test(token) || !(await this.tokens.isUsable(token))) {
      this.send(res, HttpStatus.OK, invalidLinkPage());
      return;
    }
    this.send(res, HttpStatus.OK, registrationForm(token));
  }

  /** Registers, then sends the customer to the home page to log in. */
  @Post()
  async submit(@Body() body: Record<string, unknown>, @Res() res: Response): Promise<void> {
    const token = typeof body?.token === 'string' ? body.token : '';
    const username = typeof body?.username === 'string' ? body.username : '';

    const dto = plainToInstance(RegisterDto, {
      username,
      password: typeof body?.password === 'string' ? body.password : '',
      activationToken: token,
    });

    const errors = await validate(dto);
    if (errors.length) {
      if (errors.some((e) => e.property === 'activationToken')) {
        this.send(res, HttpStatus.OK, invalidLinkPage());
        return;
      }
      const message = errors.flatMap((e) => Object.values(e.constraints ?? {})).join('. ');
      this.send(res, HttpStatus.UNPROCESSABLE_ENTITY, registrationForm(token, message, username));
      return;
    }

    try {
      await this.auth.register(dto);
    } catch (error) {
      if (error instanceof PlatformError && error.errorCode === ErrorCode.AUTH_409) {
        this.send(res, HttpStatus.CONFLICT, registrationForm(token, 'That username is taken. Choose another.', username));
        return;
      }
      if (error instanceof PlatformError && error.errorCode === ErrorCode.AUTH_401) {
        this.send(res, HttpStatus.OK, invalidLinkPage());
        return;
      }
      this.log.error(`activation registration failed: ${error instanceof Error ? error.message : String(error)}`);
      this.send(res, HttpStatus.INTERNAL_SERVER_ERROR, errorPage());
      return;
    }

    // 303 so the browser follows with a GET and a refresh cannot resubmit the form.
    res.set({ 'Cache-Control': 'no-store', 'Referrer-Policy': 'no-referrer' });
    res.redirect(HttpStatus.SEE_OTHER, this.env.activationHomeUrl);
  }

  private send(res: Response, status: number, html: string): void {
    res.status(status).set(PAGE_HEADERS).send(html);
  }
}
