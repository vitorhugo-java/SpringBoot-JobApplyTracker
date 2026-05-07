# Story: Role-based Authorization (USER, BETA, ADMIN)

## Checklist

- [x] Add Role entity/model and `User` ↔ `Role` many-to-many mapping
- [x] Add `RoleRepository`
- [x] Add Flyway migration for `roles`, `user_roles`, role seed, and `USER` backfill for existing users
- [x] Include roles in JWT generation/parsing and authentication authorities
- [x] Enforce baseline authorization (`ROLE_USER`) for protected API endpoints
- [x] Add unit tests for JWT role claims and `UserDetailsService` role mapping
- [x] Add integration test proving authenticated token without `ROLE_USER` is forbidden
- [x] Update README with role-based auth and `ROLE_USER` protected endpoint annotations

## File List

- `src/main/java/com/jobtracker/entity/enums/RoleName.java`
- `src/main/java/com/jobtracker/entity/Role.java`
- `src/main/java/com/jobtracker/entity/User.java`
- `src/main/java/com/jobtracker/repository/RoleRepository.java`
- `src/main/java/com/jobtracker/config/ApplicationConfig.java`
- `src/main/java/com/jobtracker/config/JwtService.java`
- `src/main/java/com/jobtracker/config/JwtAuthenticationFilter.java`
- `src/main/java/com/jobtracker/config/SecurityConfig.java`
- `src/main/java/com/jobtracker/service/AuthService.java`
- `src/main/resources/db/migration/V11__add_roles_and_user_authorization.sql`
- `src/test/java/com/jobtracker/unit/ApplicationConfigTest.java`
- `src/test/java/com/jobtracker/unit/JwtServiceTest.java`
- `src/test/java/com/jobtracker/unit/AuthServiceTest.java`
- `src/test/java/com/jobtracker/integration/AuthControllerIT.java`
- `README.md`
- `docs/stories/role-based-authorization.md`
