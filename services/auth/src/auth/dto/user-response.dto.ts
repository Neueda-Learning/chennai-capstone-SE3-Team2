export class UserResponseDto {
  /** The value carried in the token's `sub` claim. */
  id: string;
  username: string;
  accountId: number;
  roles: string[];
}
