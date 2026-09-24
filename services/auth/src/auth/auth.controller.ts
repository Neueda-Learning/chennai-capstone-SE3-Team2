import { Body, Controller, Get, HttpCode, HttpStatus, Post, Req, UnauthorizedException } from '@nestjs/common';
import { Request } from 'express';
import { JwtService } from '@nestjs/jwt';
import { AuthService } from './auth.service';
import { PlatformError } from '../common/platform-error';
import { Env } from '../config/env';
import { LoginDto } from './dto/login.dto';
import { RefreshDto } from './dto/refresh.dto';
import { RegisterDto } from './dto/register.dto';
import { TokenResponseDto } from './dto/token-response.dto';
import { UserResponseDto } from './dto/user-response.dto';

@Controller('auth')
export class AuthController {
  constructor(
    private readonly auth: AuthService,
    private readonly jwt: JwtService,
    private readonly env: Env,
  ) {}

  @Post('register')
  @HttpCode(HttpStatus.CREATED)
  register(@Body() dto: RegisterDto): Promise<UserResponseDto> {
    return this.auth.register(dto);
  }

  @Post('login')
  @HttpCode(HttpStatus.OK)
  login(@Body() dto: LoginDto): Promise<TokenResponseDto> {
    return this.auth.login(dto);
  }

  @Post('refresh')
  @HttpCode(HttpStatus.OK)
  refresh(@Body() dto: RefreshDto): Promise<TokenResponseDto> {
    return this.auth.refresh(dto);
  }

  @Get('me')
  async me(@Req() request: Request): Promise<UserResponseDto> {
    const header = request.headers.authorization;
    if (!header?.startsWith('Bearer ')) {
      throw PlatformError.unauthorised();
    }
    try {
      const claims = await this.jwt.verifyAsync(header.slice('Bearer '.length), {
        secret: this.env.jwtSecret,
      });
      return this.auth.me(claims.sub);
    } catch {
      throw PlatformError.unauthorised();
    }
  }
}
