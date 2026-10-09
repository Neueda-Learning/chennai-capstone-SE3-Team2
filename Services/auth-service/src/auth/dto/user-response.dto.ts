import { ApiProperty } from '@nestjs/swagger';

export class UserResponseDto {
  @ApiProperty({ example: '8f14e45f-ceea-4c1b-9d3b-1a2b3c4d5e6f', description: "The value carried in the token's sub claim." })
  id: string;

  @ApiProperty({ example: 'priya.menon' })
  username: string;

  @ApiProperty({ example: 1 })
  accountId: number;

  @ApiProperty({ example: ['CUSTOMER'], isArray: true })
  roles: string[];
}
