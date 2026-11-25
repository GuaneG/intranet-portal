# Progress Log

## Template

    ### Day X — DD.MM.YYYY
    - What I did
    - What I learned (optional)
    - Next: what's planned

## Log

### Day 1 — 06.07.2026
- Created repo structure (backend/frontend monorepo)
- Initialized Spring Boot 3.5 skeleton with [spring initializr](https://start.spring.io/)
- Added dependencies: Spring Web,Spring Data JPA,Spring Security,Validation,SpringDoc OpenAPI,Spring DevTools
- Created MySQL schema (charset: utf8mb4, collation: utf8mb4_turkish_ci)
- Configured database connection in application.properties
- Learned: utf8mb4 is MySQL's real UTF-8, utf8 can only store up to 3-byte chars so 4 byte chars like emojis dont fit; and the collation of turkish_ci handles İ/ı case-insensitive matching correctly
- Added README and CONTRIBUTING docs
- Created the initial ER diagram draft in [db diagram](https://dbdiagram.io/d/intranet_portal-6a4bc92536d348d1207bb9fd).
- Next: Continue refining the ER diagram based on requirement answers.
- Next: create API Specification.
#### Open questions / To ask (resolved)
- Should reservations be only hour-based?
> Example: 09:00-10:00, 10:00-11:00
- Or should minute-based reservations also be supported?
> Example: 09:30-10:30
- For equipment management, should ekipman_durum be limited to only:
>  - depoda
>  - personelde 
- Should returned equipment be represented only by setting the equipment status back to ```depoda```, or should return history/logs be stored separately?
- Should leave types and leave statuses be stored as plain varchar values in ```izin_bilgileri```, or should they be modeled as separate lookup tables?
#### Assumptions made to proceed 
- **Reservations:** assumed hour-based only (spec says "saatlik" + hourly grid mockup). If minute-based is needed, only the column type changes (integer to TIME).
- **ekipman_durum:** assumed only `depoda` / `personelde`. "İade edildi" is not an
  equipment status a return is modeled by filling `iade_tarihi` on the assignment
  record, and the equipment goes back to `depoda`.
- **Return history:** assumed it should be kept. `zimmetleme_bilgileri` rows are never
  deleted; a closed row (iade_tarihi set) *is* the history entry.
- **Leave types vs statuses:** Both became a separate table. `izin_turu` became a lookup
  table for extensible purposes, 'durum_turu' also became a lookup table
  but for consistency purposes only (onay bekliyor/onaylandi/iptal edildi/reddedildi).

### Day 2 - 07.07.2026
- Updated the ER based on answers from mentor and had a few decision points:
1. **Takdir panosu :** its own table, message is addressed to a specific person, not a general feed.
2. **Remaining leave:** `personel.yillik_izin_hakki` added (default 14). Remaining leave is **not** stored as a counter always computed live as `yillik_izin_hakki - SUM(is_gunu_sayisi)` over approved (`Onaylandı`) requests of type **Yıllık izin only**; mazeret/hastalık izni do not count against the quota.
3. **Cancellation / editing / reservation deletion:**
   - İzin & ekipman talep: cancellation sets `durum` to a new value, **İptal Edildi**; `islem_yapan_id` records whoever last acted on the row.
   - Cancellation/editing only permitted while `durum = Onay bekliyor`.
   - Editing an pending request updates the row directly no separate audit log.
   - Reservation deletion is a hard delete .
4. **Manager/Admin leave restriction:** Managers and Admins won't submit leave requests. Enforced at the backend (role check → 403) and frontend (hide the menu/route), not at the DB level.
- **UUID migration:** Changed primary keys from integer to UUID for tables
  where someone could guess another user's ID and see their data, if I ever
  forget to check permissions properly in the backend. It's an extra safety
  layer, not a replacement for real authorization checks.
    - **Changed to UUID:** personel, izin_bilgileri, ekipman_talep, rezervasyon,
      ekipman_bilgileri, zimmetleme_bilgileri
    - **Kept as integer:** duyuru_bilgileri, yorumlar, takdir_panosu (everyone
      can already read these, so guessing the ID doesn't give extra access),
      and all lookup tables like rol, departman, izin_turu, durum_turu,
      ekipman_tipi, oda, yetenek (these are just category values, not
      something you access individually)
    - Learned: UUID doesn't stop someone who isn't logged in at all — login
      already blocks that. It stops someone who IS logged in from reaching
      data they shouldn't, in case I mess up a permission check somewhere.
- Next: create API spec
### Day 3 - 08.07.2026 
- Focused on the other internship that i am having, did nothing.

### Day 4 - 09.07.2026
- Created the base of API Spec.
- And Upgraded the API Spec with examples and business decisions to use in dev.

### Day 5 - 10.07.2026
- Kept working on API.

### Day 6 - 11.07.2026
- Structured file structure for backend.
- Documented which type of classes fit to related packages.
- Started writing Entities from ER Diagram.
- Writed no arg constructor and getters and setters for entities.
- Learned: use **Integer** object for entities because primitive type **int** can not store null values.
- Learned: Annotation's for Entity creation.
- Next: add remaining @Column annotations and relationship mappings
  (@ManyToOne / @JoinColumn), complete all entities.

### Day 7 - 12.07.2026
- Added remaining annotations and relationships and entities.
- Completed all entities.
- Learned: Object references instead of raw Integer or String for FK's is a must. This is how Hibernate works.
- Learned: Use JoinColumn, and ManyToOne,OneToMany,OneToOne annotations to reference PK's with FK's at FK level.
- Learned: These annotations (ManyToOne, ...) at default points to the object that is annotated at.
- Learned: Composite key needs 2 classes one with @Embeddable annotation, one with normal entity class that has the object with Embeddable annotation and EmbeddedId annotation at the top of that object
- Learned: At composite key class equals and hashcode method's must be overridden because of Hibernate's persistence context is Map Collection.
- Learned: What is Serializable?
- Learned: Why JPA needs a no arg constructor.
- Next: Start to write DTO's 

### Day 8 - Day 9 (13.07.2026-14.07.2026)
- Completed all DTO's
- Separated them as Request and Response
- Learned: How to create DTOs and which principles to follow while writing DTOs
- Learned: DTOs derive from endpoints, not entities; request excludes server-set fields (id, timestamps, status) response flattens FKs to names and never exposes the entity
- Next: Start to write Repository classes
 ### Day 10-16 (catch-up) - 15.07.2026-26.07.2026
- Created repositories on demand for the auth module (PersonelRepository, RefreshTokenRepository,
  AuditLogRepository) — I add a repository (and only the methods I need) per module, not all upfront.
- Implemented JWT login flow: SecurityConfig, JWTFilter, JWTService, AuthController/AuthService.
  Access token 15 min, kept in localStorage (JS-readable by design).
- Added refresh token rotation + reuse detection (RefreshTokenService.rotate, 4 branches).
  Refresh token 14 days, httpOnly cookie, stored in DB as a SHA-256 hash.
- Built audit log skeleton: AuditEylem enum, AuditLog entity (UUID), AuditLogRepository, AuditService.
  Login success/fail, logout and token-reuse events are now audited.
- Added tests: unit (Mockito) for AuthService/RefreshTokenService/JWTService/AuditService,
  and integration with Testcontainers (real MySQL 8) via an AbstractIntegrationTest base.
- Frontend: apiFetch in api.js (401 → silent refresh, single-flight pattern), logout, login page,
  ProtectedRoute and dashboard.
- Organized all the above into logical commits and pushed to GitHub.
- Learned: filter chain runs before controllers; 401 (identity) resolves before 404 (routing);
  authenticationEntryPoint turns Spring's 403 into a 401.
- Learned: SHA-256 (fast, deterministic → token hash/lookups) vs BCrypt (slow, salted → passwords).
- Learned: @Transactional — noRollbackFor (throw but still commit), REQUIRES_NEW (audit survives a
  caller rollback); self-invocation bypasses the proxy so the method must be called via another bean.
- Learned: cookie flags — httpOnly / secure / sameSite=Lax / maxAge in seconds (0 = delete).
- Learned: unit tests are isolated (mock, no DB), integration use a real context + DB; ArgumentCaptor
  to test void methods; integration DB isn't auto-cleaned between methods (use @BeforeEach + deleteAll).
- Learned: CRLF vs LF — a whole-file line-ending flip makes Git report dozens of "changed" files with
  zero real code change (git diff -w shows the truth); a stale .git/index.lock blocks all git ops.
- Next: #7 Personel entity extension (mezunOkul/mezunBolum/mezuniyetYili + iseGirisTarihi).
### Day 17 - 27.07.2026
- Did an end-to-end security review of the login module with AI
  (AuthService, AuthController, SecurityConfig, JWTFilter, JWTService, RefreshTokenService, GlobalExceptionHandler).
- Fixes applied:
    - JWTFilter: fail-closed guard — if sub/rol claim is null, don't set authentication (stay anonymous → 401)
      instead of building a "ROLE_null" authority.
    - GlobalExceptionHandler: return fixed messages instead of raw ex.getMessage() (don't leak internal details).
    - RefreshTokenService: added @Scheduled deleteExpiredTokens (+ @EnableScheduling) to purge expired rows,
      so the refresh_token table stops growing unbounded.
    - Frontend: login redirect uses { replace: true } + new PublicRoute guard, so an authenticated user
      can't land on /login (history hygiene + route guard).
- Learned (XSS): attacker runs their JS in your page; root cause is putting user input into HTML unescaped.
  Escape = turn < > & " ' into HTML entities so the browser shows them as text, not code. React auto-escapes
  {value}; only risk is dangerouslySetInnerHTML. Escape (plain text) vs sanitize/DOMPurify (allow safe HTML).
  Stored XSS steals the VIEWER's token, not the attacker's → privilege escalation.
- Learned (CSRF): rides on cookies the browser auto-sends; attacker can't READ the response (CORS/SOP),
  so it's not theft. Safe to disable here because stateless + endpoints auth via Authorization header
  (not auto-sent); the only cookie endpoint (/refresh) is covered by sameSite=Lax.
- Learned: three cookie/threat pairs — httpOnly = XSS, secure = network sniffing (MITM), sameSite = CSRF.
- Learned (timing attack): if BCrypt only runs for existing users, response time leaks whether a username
  exists; fix is to run BCrypt against a dummy hash even when the user is missing (not a fixed sleep).
- Learned (history): browser history is a stack; navigate() pushes a new entry, { replace: true } swaps the
  current one. Route guard = a component wrapping a route to check a condition (ProtectedRoute vs its mirror PublicRoute).
- Learned: stateless JWT can't be revoked mid-life → a valid access token lives its full 15 min even after
  logout / role change (the access-token-leak concern).
- Created a task backlog (16 items) for the remaining security / hardening / feature work.
- Next: #1 access-token 15-min window mitigation; write the deleteExpiredTokens integration test; #7 Personel entity extension.

### Day 18 - 28.07.2026
- Completed the authentication-hardening work in Roadmap Stage 0, including the shared Spring context/Testcontainers lifecycle fix.
- Prevented inactive personnel from receiving new tokens through both login and refresh.
- Inactive refresh attempts now remove all refresh-token rows for that personnel while still returning a controlled 401; the `noRollbackFor` transaction behavior was verified against real MySQL.
- Added a Testcontainers integration test proving scheduled cleanup removes expired refresh tokens and preserves valid ones.
- Added controller/HTTP integration tests for inactive login and refresh, including the absence of a replacement refresh cookie.
- Replaced Spring Security's empty `sendError(401)` response with a JSON `AuthenticationEntryPoint` using the shared `ErrorResponse` shape.
- Added a specific `AccessDeniedException` handler so method-security failures return 403 instead of falling through to the generic 500 handler.
- Added HTTP integration coverage for:
    - unauthenticated request → 401 JSON,
    - authenticated `PERSONEL` request to an `ADMIN` method → 403 JSON.
- Reviewed controlled authentication messages and kept framework, SQL and internal exception details out of client responses.
- Updated `SECURITY.md`, `API_SPEC.md` and `ROADMAP.md` to match the implemented refresh-cookie, server-side logout, inactive-personnel and 401/403 behavior.
- Verification completed for the focused authentication set: 13 unit tests, 4 auth/security HTTP integration tests and 2 refresh-token MySQL integration tests passed.
- Full backend verification passed with Testcontainers: 23 tests, 0 failures, 0 errors and 0 skipped.
- Learned:
    - `AuthenticationEntryPoint` formats failures for users whose identity was not established (401).
    - `@PreAuthorize` runs after authentication and raises an access-denied exception when authority is insufficient (403).
    - A catch-all exception handler can accidentally turn expected authorization failures into 500 unless a more specific handler exists.
    - Request `Cookie` and response `Set-Cookie` represent opposite directions of the HTTP cookie flow.

### Day 19 - 29.07.2026
- Added `HR` to the fixed role model and verified that JWT authority handling supports it.
- Implemented the Admin-only `PATCH /api/admin/personel/{personelId}/rol` flow.
- Added role-change rules: an Admin cannot change their own role or another existing Admin’s role; requesting the current role is idempotent.
- Successful role changes now revoke all refresh tokens belonging to the target personnel.
- Added `ROL_DEGISTIRILDI` audit records with separate actor and target personnel relationships.
- Split audit transaction behavior deliberately:
    - login/logout/token security events remain independent with `REQUIRES_NEW`,
    - successful role-change audit joins the role-change transaction with `REQUIRED`.
- Added Mockito service tests and HTTP/MySQL Testcontainers integration tests covering authorization, validation, successful persistence, refresh-token revocation, audit actor/target data and rollback behavior.
- Verified that role change, refresh-token deletion and audit persistence roll back together when an outer transaction fails.
- Learned:
    - JPA dirty checking persists the managed personnel role change at transaction commit without an explicit second `save`.
    - Test-only transaction helpers must be called through a Spring-managed bean so the transaction proxy is active.
    - A broad `assertThrows(RuntimeException.class)` can hide a `NullPointerException` and produce a false-positive test; expect the narrowest intentional exception.
- Next: implement the HR personnel creation flow.
### Day 20 - 29.07.2026
- Implemented `POST /api/hr/personel` for HR/Admin with required department, optional manager and required birth date.
- New personnel are created with server-controlled `PERSONEL` role, active status, normalized username/e-mail, BCrypt initial password and `parolaDegistirmeliMi=true`.
- Moved personnel registry-number generation out of the request: MySQL now generates unique `sicil_numarasi` values with `AUTO_INCREMENT`.
- Added duplicate protection for normalized username and e-mail, including a controlled `409 Conflict` response for database race conditions.
- Added first-login password enforcement:
    - normal login returns `428` without access/refresh tokens or login audit while the flag is open,
    - `PATCH /api/auth/ilk-parola` verifies credentials, applies the password policy, closes the flag, revokes refresh tokens and writes audit in one transaction.
- Added authenticated `PATCH /api/profil/parola` so personnel can change their password later; personnel identity comes from the JWT principal rather than the request.
- Added `PERSONEL_EKLENDI` and `PAROLA_DEGISTIRILDI` audit events without storing raw passwords or hashes in audit details.
- Added unit and Testcontainers HTTP/integration coverage for HR authorization, normalized duplicates, DB-generated registry numbers, BCrypt storage, mandatory first-password flow, normal login after the change, refresh-token revocation and audit persistence.
- Full backend verification reported by the developer: 41 tests, 0 failures, 0 errors and 0 skipped; build successful.
- Learned:
    - MySQL requires an `AUTO_INCREMENT` column to be indexed when the table is created; declaring inline `UNIQUE` avoided Hibernate trying to add the key only after table creation.
    - A lazy JPA relationship cannot safely be traversed after its repository transaction has closed; tests should reload the related entity or query the needed data instead of changing the mapping to `EAGER` merely for assertions.
    - BCrypt’s 72-byte input limit is separate from user-facing character length, so both constraints are validated while the UI can still explain the ordinary 8–72 character rule.
- Next: implement HR personnel update/deactivation and then the remaining profile fields and team-directory flow.

### Day 21 - 29.07.2026
- Implemented `PATCH /api/hr/personel/{personelId}` for HR/Admin with partial-update semantics.
- Added field-presence tracking so an omitted field preserves its current value while explicit `null` clears supported nullable fields.
- Limited the update request to HR-managed profile and employment fields; personel ID, username, registry number, role, password, active status and exit date remain server-controlled or belong to separate flows.
- Added normalized e-mail conflict checking that excludes the target personnel and converts database race conflicts to controlled `409 Conflict` responses.
- Added department/manager consistency rules:
    - a real department change must include the manager field,
    - `yoneticiId: null` removes the manager,
    - an assigned manager must be active, have the `YONETICI` role and belong to the effective department,
    - personnel cannot be assigned as their own manager.
- Reused the same active-role-department manager validation in the personnel creation flow.
- Added `PERSONEL_GUNCELLENDI` audit records with actor/target relationships and only the changed field names; no-op updates return `204` without persistence or audit side effects.
- Added HTTP/MySQL integration coverage for partial updates, explicit null clearing, validation, authorization, e-mail conflict, department/manager rules, missing resources, no-op behavior, audit persistence and manager validation during creation.
- Full backend verification reported by the developer: 61 tests, 0 failures, 0 errors and 0 skipped; build successful.
- Next: implement the separate HR personnel deactivation flow.

### Day 22 - 29.07.2026
- Implemented bodyless `PATCH /api/hr/personel/{personelId}/pasiflestir` for HR/Admin with persisted target-role and self-target restrictions.
- HR/Admin can deactivate `PERSONEL` and `HR` targets; only Admin can deactivate `YONETICI`, while `ADMIN` targets and self-deactivation are forbidden.
- Successful deactivation sets `calisiyorMu=false` and the exit date, revokes all target refresh tokens and writes one `PERSONEL_PASIFE_ALINDI` audit record with separate actor/target relationships.
- Deactivating a manager clears the manager relationship from every direct employee, including inactive personnel, and records only the affected count in audit details.
- Already inactive authorized targets are idempotent: the existing exit date is preserved and token, relationship and audit side effects are skipped.
- Personnel changes, direct-employee relationships, refresh-token deletion and audit persistence share one transaction and were verified to roll back together.
- Added Mockito service tests and real MySQL Testcontainers HTTP/integration coverage for the role matrix, missing actor/target, idempotency, manager cleanup, refresh-token revocation, audit data and transaction rollback.
- Full backend verification reported by the developer: 79 tests, 0 failures, 0 errors and 0 skipped; build successful.
- Next: implement profile and active team-directory flows, including exclusion of inactive personnel from default directory results.

### Day 23 - 29.07.2026
- Implemented authenticated `GET /api/profil`; personnel identity is taken only from the JWT principal and no request/path personnel ID is accepted.
- Reused `ProfilBilgileriResponse` to return personnel, department, role, optional manager, education/contact fields, profile image, employment date and alphabetically ordered skills.
- Added live remaining-leave calculation for `PERSONEL` and `HR`; only approved leave types marked as deducting from annual balance are included. `ADMIN` and `YONETICI` receive `kalanIzin=null`.
- Added the fixed `TalepDurum` enum, mapped `DurumTuru.durumKodu` with `EnumType.STRING`, and added `IzinTuru.yillikHaktanDuserMi` while keeping leave types dynamically manageable.
- Kept profile reads transactional/read-only and audit-free; missing JWT personnel returns controlled `404`, while unauthenticated access returns `401`.
- Added real MySQL/Testcontainers HTTP coverage for profile mapping, unauthenticated access, missing principal personnel, leave filtering, HR/ADMIN/YONETICI role behavior and alphabetical skill ordering.
- Focused verification passed: `ProfilControllerIntegrationTest` ran 8 tests with 0 failures, 0 errors and 0 skipped.
- The full backend suite was not rerun in this verification round.
- Next: implement permitted self-profile updates or continue with the active team-directory flow.

### Day 24 - 30.07.2026
- Corrected the Admin role-change guard order so self-target and existing-Admin target restrictions are enforced before the same-role idempotency return.
- Added regression tests for same-role requests targeting the acting Admin and another existing Admin.
- Implemented authenticated `PATCH /api/profil`; personnel identity is taken only from the JWT principal and all authenticated roles can update their own permitted fields.
- Limited self-service updates to `telNo`, `mezunOkul`, `mezunBolum` and `mezuniyetYili`; omitted fields are preserved, explicit `null` clears supported values and blank text is rejected.
- Added active-personnel enforcement, positive/non-future graduation-year validation and `204 No Content` success behavior.
- Real changes write one `PROFIL_GUNCELLENDI` audit record with the same actor/target personnel and only changed field names; no-op requests return `204` without audit.
- Profile persistence and audit join the same transaction and were verified to roll back together.
- Added real MySQL/Testcontainers HTTP coverage for all roles, partial updates, explicit null clearing, validation, inactive and missing personnel, unauthenticated access, no-op behavior, audit safety and transaction rollback.
- Focused verification reported by the developer: `ProfilControllerIntegrationTest` ran 23 tests with 0 failures, 0 errors and 0 skipped.
- Full backend verification reported by the developer: 104 tests, 0 failures, 0 errors and 0 skipped; build successful.
- Next: implement the active team-directory flow, including exclusion of inactive personnel from default directory results.

### Day 25 - 30.07.2026

- Implemented authenticated `GET /api/ekip-dizini` for all roles with a framework-independent pagination response containing `icerik`, `sayfa`, `sayfaBoyutu`, `toplamKayit` and `toplamSayfa`.
- Limited directory responses to active personnel and excluded personnel ID, username, e-mail, registry number and other non-contract fields.
- Added stable ascending ordering by name, surname and hidden personnel ID so pagination remains deterministic when names collide.
- Added optional `departmanId` and `yetenekId` filters with `AND` semantics. The current contract returns an empty `200 OK` page for missing positive IDs and controlled `400 Bad Request` for zero/negative IDs.
- Used an `EXISTS` skill filter to avoid duplicate personnel/count corruption, then loaded every skill for only the current personnel page in one bulk query and returned those skills alphabetically.
- Enforced the current JWT principal in the service: a missing personnel record returns `404`, an inactive personnel record returns `403` even while the stateless access token is otherwise valid, and unauthenticated access returns `401`.
- Kept directory reads transactional/read-only and audit-free. Invalid pagination ranges and non-numeric query parameters return controlled `400 Bad Request` responses without exposing framework exception details.
- Added real MySQL/Testcontainers HTTP coverage for active-only results, stable pagination, all-role access, principal checks, department/skill filtering, filter composition, empty results, complete skill mapping, duplicate prevention, audit absence and invalid query parameters.
- Focused verification reported by the developer: `EkipDiziniControllerIntegrationTest` ran 25 tests with 0 failures, 0 errors and 0 skipped.
- Full backend verification reported by the developer: 129 tests, 0 failures, 0 errors and 0 skipped; build successful.
- Learned: `PageRequest` implements Spring Data’s `Pageable`; an IDE auto-import of SpringDoc’s same-named `Pageable` type causes a misleading incompatible-parameter error.
- Next: connect the frontend team-directory screen to the verified endpoint or continue the remaining department/skill management and profile-photo flows.

### Day 26 - 30.07.2026

- Implemented authenticated `GET /api/departments` and `GET /api/skills` lookup endpoints for all roles with the existing `LookUpResponse(id, ad)` contract.
- Returned every department and skill definition, including records not yet assigned to active personnel, in stable ascending name/ID order without pagination.
- Added a shared read-only `LookupService` that resolves personnel identity only from the JWT principal and rejects missing personnel with `404` and inactive personnel with `403`.
- Added a dedicated `YetenekRepository` and extended `DepartmanRepository` with deterministic derived-query ordering.
- Kept both lookup reads audit-free and free of persistence side effects; empty skill data returns `200 OK` with `[]`.
- Added real MySQL/Testcontainers HTTP coverage for alphabetical mapping, unused definitions, empty skills, all-role access, unauthenticated access, missing/inactive principals, response field safety and audit absence.
- TDD RED verification ran 17 test invocations with the expected 15 endpoint-missing failures and 0 errors before production code was added.
- Focused verification reported by the developer: `LookupControllerIntegrationTest` ran 17 tests with 0 failures, 0 errors and 0 skipped.
- Full backend verification reported by the developer: 146 tests, 0 failures, 0 errors and 0 skipped; build successful.
- Implemented authenticated `PUT /api/profil/yetenekler` with full-replacement set semantics for the current JWT principal; an empty list clears all skills and request order has no meaning.
- Rejected null, non-positive and duplicate IDs with `400`; rejected any unknown skill with `404` before writes so the relationship update remains atomic.
- Compared existing and requested ID sets, applied only added/removed `PersonelYetenek` relationships, and kept same-set requests free of persistence and audit side effects.
- Recorded real changes with the existing `PROFIL_GUNCELLENDI` event and the safe detail `Güncellenen alanlar: yetenekler`; relationship changes and audit join the same transaction and roll back together.
- Added MySQL/Testcontainers HTTP coverage for full replacement, clearing, no-op, validation, missing skills, active `PERSONEL`/`HR`/`YONETICI` access, inactive/missing principals, unauthenticated access, safe audit content and outer-transaction rollback. `ADMIN` remains supported by the all-authenticated method rule but was not separately included in the focused role matrix by product decision.
- TDD RED verification first ran 3 tests with the expected 3 endpoint-missing `404` failures; the validation RED checkpoint then ran 12 test invocations with the expected 5 status failures and 0 errors.
- Focused verification reported by the developer: `ProfilYetenekControllerIntegrationTest` ran 20 tests with 0 failures, 0 errors and 0 skipped.
- Full backend verification reported by the developer: 166 tests, 0 failures, 0 errors and 0 skipped; build successful.
- Next: connect the frontend team-directory filters and profile-skill editor to the verified endpoints, or continue the remaining department/skill management and profile-photo flows.

### Day 27 - 30.07.2026

- Implemented HR/Admin-only `POST /api/skills` and `PATCH /api/skills/{yetenekId}` management endpoints without changing the existing lookup GET flow.
- Normalized names with trim while preserving intentional casing, rejected case-insensitive duplicates with controlled `409`, and retained the database unique constraint as a concurrency fallback.
- Renamed skills in place so existing personnel-skill relationships keep the same ID; exact normalized no-op updates remain persistence- and audit-free.
- Added `YETENEK_EKLENDI` and `YETENEK_GUNCELLENDI` audit events with the acting principal, null target personnel and catalog-safe name details in the shared transaction.
- Focused verification reported by the developer: `YetenekYonetimControllerIntegrationTest` ran 28 tests and `YetenekYonetimServiceTest` ran 2 tests, with 0 failures, 0 errors and 0 skipped.
- Existing lookup regression reported by the developer: `LookupControllerIntegrationTest` ran 17 tests with 0 failures, 0 errors and 0 skipped.
- Full backend verification reported by the developer: 196 tests, 0 failures, 0 errors and 0 skipped; build successful.
- Next: connect the frontend to the verified team-directory, profile-skill and skill-management endpoints, or continue department management and profile-photo flows.

## 2026-07-30 — Ekip dizini araması

- Added optional `arama` filtering to `GET /api/ekip-dizini` across name, surname and full name with trim and case-insensitive matching.
- Kept blank search values filter-free and combined search with department and skill filters using `AND` in both the page and count queries.
- Preserved the existing stable ordering, pagination DTO and response fields.
- Focused verification reported by the developer: `EkipDiziniControllerIntegrationTest` ran 32 tests with 0 failures, 0 errors and 0 skipped.
- Full backend verification reported by the developer: 203 tests, 0 failures, 0 errors and 0 skipped; build successful.

## 2026-07-30 — Faz 6 personel yönetimi okuma API'leri

- Added active HR/Admin-only paginated `GET /api/hr/personel` with name/surname/full-name/username search, department, role and active-state filters, stable ordering and matching count-query conditions.
- Added target-matrix-protected `GET /api/hr/personel/{personelId}` with the approved update-form fields and explicit nullable manager ID, without exposing entity or sensitive account fields.
- Added `GET /api/hr/personel/atanabilir-yoneticiler` for active managers in a selected department, including optional target exclusion, stable ordering, controlled validation and empty-result behavior.
- Centralized active actor and target-role checks in `PersonelYonetimYetkiService` and applied them to personnel creation, update, deactivation and Admin role changes while preserving existing no-op, audit, refresh-token and rollback behavior.
- Added controlled `400 Geçersiz istek` handling for missing required query parameters.
- Focused verification reported by the developer: 127 tests, 0 failures, 0 errors and 0 skipped; build successful.
- Full backend verification reported by the developer: 279 tests, 0 failures, 0 errors and 0 skipped; build successful.

## 2026-08-03 — Departman yönetimi

- Implemented active HR/Admin-only `POST /api/departments` and `PATCH /api/departments/{departmanId}` without changing the existing read-only department lookup contract.
- Trimmed department names while preserving intentional casing, enforced the normalized 1–50 character boundary, and returned controlled `400`, `404` and case-insensitive duplicate `409` responses.
- Kept rename operations on the same department ID so existing personnel-department relationships remain intact. Exact normalized equality is persistence- and audit-free; case-only changes are real updates.
- Added database unique-constraint race handling so concurrent duplicate writes become controlled `409 Departman zaten mevcut.` responses instead of leaking persistence errors.
- Added `DEPARTMAN_EKLENDI` and `DEPARTMAN_GUNCELLENDI` audit events with the authenticated principal as actor and null target personnel. Department changes and audit records share one transaction and roll back together.
- Verified active HR/Admin access; rejected `PERSONEL`, `YONETICI`, inactive principals, missing principals and unauthenticated requests with the approved status behavior.
- Focused verification reported by the developer: `DepartmanYonetimControllerIntegrationTest` ran 25 tests and `DepartmanYonetimServiceTest` ran 2 tests, with 0 failures, 0 errors and 0 skipped.
- Department-management plus existing lookup regression verification ran 44 tests with 0 failures, 0 errors and 0 skipped; build successful.
- Corrected the intended JaCoCo Spring Boot entry-point exclusion from the non-matching `Application.class` pattern to the exact `BackendApplication.class`; the package coverage threshold was not lowered.
- Full backend verification reported by the developer: 306 tests, 0 failures, 0 errors and 0 skipped; JaCoCo check and build successful.

## 2026-08-11 — Personel aktifleştirme

- HR ve Admin için body içermeyen `PATCH /api/hr/personel/{personelId}/aktiflestir` endpoint’i eklendi.
- HR’nin yalnız `PERSONEL` ve başka bir `HR`; Admin’in `PERSONEL`, `HR` ve `YONETICI` hedeflerini aktifleştirebildiği hedef matrisi uygulandı. Öz-hedef, `ADMIN` hedefi ve pasif aktör engellendi.
- Gerçek aktifleştirmede `calisiyorMu=true`, `isCikisTarihi=null` yapılıyor; zaten aktif ve izinli hedef auditsiz no-op olarak `204` dönüyor.
- `PERSONEL_AKTIFLESTIRILDI` audit eylemi aynı transaction içinde eklendi ve rollback davranışı doğrulandı.
- Yöneticinin eski doğrudan bağlılarının otomatik geri atanmadığı ve aktifleştirilen personelin ekip dizininde yeniden göründüğü doğrulandı.
- `PersonelAktiflestirmeControllerIntegrationTest`: 19 test başarılı.
- Aktifleştirme, mevcut personel mutation’ları, servis ve ekip dizini regresyon paketi: 108 test başarılı.
- Tam backend doğrulaması: 325 test, 0 failure, 0 error, 0 skipped; JaCoCo coverage kontrolleri ve Maven build başarılı.

## 2026-08-12 — Dashboard aktif ekip üyesi özeti

- Mevcut `GET /api/dashboard/ozet` response’u geriye uyumlu biçimde `aktifEkipUyesiSayisi` ve en fazla 5 kayıt taşıyan `ekipDiziniOzeti` alanlarıyla genişletildi; yeni endpoint oluşturulmadı.
- Ekip dizini görünürlüğü backend sorgusunda role göre sınırlandı: PERSONEL yalnız kendi departmanındaki aktif kişileri; YONETICI ve HR, Adminler dışındaki bütün aktif kişileri; ADMIN bütün aktif kişileri görür.
- Personelin yöneticisinin aktif, `YONETICI` rolünde ve aynı departmanda olma kuralı mevcut oluşturma/güncelleme servisinde korunur; bu nedenle PERSONEL yöneticisini kendi departman sonucunda görür.
- Dashboard sayısı ve ilk 5 kişi ayrı bir global sorgudan değil, aynı role göre filtrelenmiş ekip dizini sayfasının `toplamKayit` ve `icerik` değerlerinden üretilir.
- `ekipDiziniOzeti` mevcut güvenli `{ ad, soyad, departman, profilFoto, yetenekler }` alanlarını taşır; personel ID, kullanıcı adı, e-posta, sicil numarası veya başka hesap bilgisi içermez.
- Ekip dizini pagination, arama, departman/yetenek filtreleri, sabit sıralama, read-only ve auditsiz davranışlarını korur; rol/departman sınırı hem içerik hem count sorgusunda uygulanır.
- TDD RED doğrulamasında 40 testin 7’si eksik liste ve görünürlük kuralları nedeniyle beklenen şekilde başarısız oldu.
- Focused dashboard, ekip dizini ve security doğrulaması: 42 test, 0 failure, 0 error, 0 skipped; build başarılı.
- Tam backend doğrulaması: 334 test, 0 failure, 0 error, 0 skipped; JaCoCo coverage kontrolleri ve Maven build başarılı.

## 2026-08-12 — Ekip dizini yönetici etiketi

- Mevcut güvenli `EkipDiziniResponse` sözleşmesi `yoneticinMi` boolean alanıyla genişletildi; yeni endpoint veya hassas kimlik alanı eklenmedi.
- Değer yalnız listedeki kişi authenticated aktörün veritabanındaki atanmış yöneticisiyle aynı personelse `true`; diğer kişilerde ve yöneticisi olmayan aktörde `false` olarak hesaplanıyor.
- Karar ad, liste sırası veya rol tahminine dayanmaz; gerçek `Personel.yonetici` ilişkisi ve yalnız backend içindeki personel ID karşılaştırması kullanılır.
- Dashboard `ekipDiziniOzeti` aynı service ve response DTO’yu kullandığından `yoneticinMi` alanını ayrı veya çelişebilecek bir hesaplama olmadan taşır.
- PERSONEL’in kendi yöneticisi, HR’ın kendi yöneticisi, başka kullanıcının yöneticisi, yöneticisiz aktör ve mevcut görünürlük regresyonları gerçek MySQL/Spring Security integration testleriyle doğrulandı.
- TDD RED doğrulamasında 41 testin 4’ü alan eksikliği nedeniyle beklenen şekilde başarısız oldu.
- Focused dashboard, ekip dizini ve security doğrulaması: 43 test, 0 failure, 0 error, 0 skipped; build başarılı.
- Tam backend doğrulaması: 335 test, 0 failure, 0 error, 0 skipped; JaCoCo coverage kontrolleri ve Maven build başarılı.

## 2026-08-12 — Güvenli profil fotoğrafı

- Aktif authenticated bütün roller için `PUT /api/profil/fotograf`, `DELETE /api/profil/fotograf` ve `GET /api/profil-fotograflari/{dosyaAnahtari}` endpoint’leri eklendi; yükleme ve silme yalnız JWT principal personelinin kendi kaydında çalışır.
- JPEG/PNG allowlist; normalize uzantı, MIME, magic byte ve gerçek decode uyumu; 2 MB, 2048×2048 ve 4.194.304 toplam piksel sınırları uygulandı. Ham görsel metadata ve ek payload taşımadan yeniden encode ediliyor, uzun kenar en fazla 1024 piksele indiriliyor.
- Fotoğraflar webroot dışında `PROFILE_PHOTO_STORAGE_PATH` ile yapılandırılabilen yerel klasörde, sunucu üretimli UUID `.jpg/.png` anahtarlarıyla tutuluyor. DB yalnız bu anahtarı veya fotoğraf yokluğunu belirten `def_pfp.png` sentinel değerini saklıyor.
- Authenticated binary GET gerçek image media type, `X-Content-Type-Options: nosniff` ve `Cache-Control: private, max-age=3600` döndürüyor; public static mapping, attachment ve read auditi oluşturulmuyor.
- Yükleme/değiştirme rollback’inde yeni dosya telafi işlemiyle siliniyor; commit sonrasında önceki özel dosya temizleniyor. Silmede DB sentinel ve audit commit olmadan fiziksel eski dosya silinmiyor; sentinel/geçersiz değer no-op `204` ve auditsiz kalıyor.
- `PROFIL_FOTOGRAFI_GUNCELLENDI` ve `PROFIL_FOTOGRAFI_SILINDI` auditleri aktör=hedef principal olarak aynı transaction’a katılıyor; audit detayı orijinal ad, UUID veya fiziksel yol içermiyor.
- `Personel` entity’sine doğru yeni-entity semantiğini koruyan nullable wrapper `@Version Long version` eklendi. Deterministik stale-write testi, optimistic conflict ile DB/dosya/audit bütünlüğünün ve kaybeden transaction rollback’inin korunduğunu doğruladı.
- Yüklenen aynı UUID anahtarının `GET /api/profil`, görünür ekip dizini, dashboard özeti ve izin verilen HR/Admin personel yönetimi liste/detay DTO’larına taşındığı; özel fotoğrafı olmayan kayıtlarda `def_pfp.png` kaldığı doğrulandı.
- Profil fotoğrafı integration sınıfının güncel sonucu: 43 test, 0 failure, 0 error, 0 skipped; build başarılı. Docker gerektirmeyen ilgili regresyon: 44 test başarılı.
- Profil, ekip dizini, dashboard, personel yönetimi ve security odaklı regresyon paketi: 191 test, 0 failure, 0 error, 0 skipped; build başarılı.
- Final tam backend doğrulaması: 409 test, 0 failure, 0 error, 0 skipped. JaCoCo 38 sınıfı analiz etti, bütün coverage kontrolleri karşılandı ve Maven build başarılı tamamlandı.
- Açık operasyonel/API işleri: production’da `personel.version` kolonu için kontrollü schema migration; optimistic-lock çatışmasını generic `500` yerine kontrollü `409 Conflict` olarak eşleme; çok instance ortak depolama, malware taraması ve otomatik orphan temizliği için ayrı tasarım.

## 2026-08-13 — Admin hedefli personel yetenek yönetimi

- Aktif Admin’in aktif `PERSONEL`, `HR` ve `YONETICI` hedeflerinin bütün yetenek kümesini yönetebilmesi için `PUT /api/hr/personel/{personelId}/yetenekler` eklendi; mevcut self-service `PUT /api/profil/yetenekler` sözleşmesi ve yetkisi korunuyor.
- Method security’deki `ADMIN` kontrolüne ek olarak aktör DB’den yeniden yüklenerek mevcut rolü ve aktifliği doğrulanıyor. Öz-hedef, başka Admin ve pasif hedef kontrolleri aynı-küme no-op kararından önce service katmanında uygulanıyor.
- Mevcut self-service set karşılaştırma akışı ortaklaştırıldı: bütün ID’ler yazımdan önce doğrulanıyor, yalnız eklenen/kaldırılan `PersonelYetenek` ilişkileri değiştiriliyor, boş liste bütün ilişkileri kaldırıyor ve aynı küme auditsiz/persistence yan etkisiz `204` dönüyor.
- Gerçek değişiklikte `PROFIL_GUNCELLENDI` auditi Admin aktör, path hedefi ve yalnız `Güncellenen alanlar: yetenekler` detayıyla aynı transaction’a katılıyor; dış transaction rollback testi ilişki ve auditin birlikte geri alındığını doğruluyor.
- TDD RED doğrulamasında ilk happy-path testi eksik endpoint nedeniyle beklenen `204` yerine `404` aldı. Minimal implementasyon sonrasında yeni integration suite’i 27 test, 0 failure, 0 error ve 0 skipped ile başarılı oldu.
- Mevcut self-service profil yetenekleri, personel yönetimi, ekip dizini ve HTTP security odaklı regresyon paketi 184 test, 0 failure, 0 error ve 0 skipped ile başarılı oldu. `PersonelYonetimYetkiServiceTest` ayrıca 11/11 GREEN tamamlandı.
- Bağımsız read-only kod incelemesi yetkilendirme sırası, DB aktör doğrulaması, validation, atomiklik, no-op, audit ve rollback akışlarında Critical, Important veya Minor bulgu olmadan onay verdi.
- Final tam backend doğrulaması 436 test, 0 failure, 0 error ve 0 skipped ile tamamlandı. JaCoCo 38 sınıfı analiz etti, bütün coverage kontrolleri karşılandı ve Maven build başarılı oldu.

## 2026-08-15 — Admin parola sıfırlama, katalog silme ve yönetici hiyerarşisi

- Aktif Admin’in `PERSONEL`, `HR` ve `YONETICI` hedeflere geçici parola atayabilmesi için `PATCH /api/admin/personel/{personelId}/parola-sifirla` eklendi. Öz-hedef ve Admin hedefler service katmanında reddediliyor; geçici parola yalnız BCrypt hash’i olarak saklanıyor, ilk parola flag’i açılıyor, bütün refresh tokenlar iptal ediliyor ve parola içermeyen `PAROLA_SIFIRLANDI` auditi aynı transaction’a katılıyor.
- Admin parola sıfırlama integration sınıfı 22/22; parola/personel/security odaklı regresyon paketi 32/32 GREEN tamamlandı.
- Aktif HR/Admin için `DELETE /api/departments/{departmanId}` ve `DELETE /api/skills/{yetenekId}` eklendi. Aktif veya pasif herhangi bir personel ilişkisi bulunan kayıt cascade uygulanmadan kontrollü `409` ile korunuyor; ilişkisiz kayıtlar pessimistic lock, constraint fallback ve aynı-transaction güvenli audit ile siliniyor.
- Departman yönetimi 40/40, yetenek yönetimi 43/43 ve katalog/lookup birleşik regresyon paketi 106/106 GREEN tamamlandı.
- Mevcut `Personel.yonetici` ilişkisini generic aktif forest olarak sunan `GET /api/ekip-dizini/hiyerarsi` ve PERSONEL/YONETICI aktörlere yalnız en yakın iki üstünü gösteren `GET /api/ekip-dizini/yonetici-zincirim` eklendi. DTO’lar yalnız görüntüleme için gerekli güvenli alanları taşıyor; HR/Admin tam forest, PERSONEL/YONETICI yalnız kişisel zincir görebiliyor.
- Legacy yönetici döngüleri okuma sırasında kontrollü `409` ile tespit ediliyor. Yeni yönetici atamalarında iki kişilik ve uzun döngüler değişiklik/audit öncesinde `400` ile engelleniyor. Hedefli hiyerarşi testleri 18/18, legacy döngü testleri 2/2 ve yazma döngüsü testleri 2/2 GREEN tamamlandı.
- Admin hedefli yetenek yönetimi formunun doğru ön seçim yapabilmesi için `GET /api/hr/personel/{personelId}` detay DTO’suna alfabetik `yetenekler: [{ id, ad }]` alanı eklendi; ilişki yoksa boş liste dönüyor. Hedefli testler 2/2 GREEN tamamlandı.
- İlişkili çapraz regresyon paketi 262 test, 0 failure, 0 error ve 0 skipped ile başarılı oldu. Final tam backend doğrulaması 514 test, 0 failure, 0 error ve 0 skipped ile tamamlandı; JaCoCo 40 sınıfı analiz etti, bütün coverage kontrolleri karşılandı ve Maven build başarılı oldu.

## 2026-08-15 — Optimistic-lock production hazırlığı

- `OptimisticLockingFailureException` ailesi ortak HTTP hata katmanında sabit ve güvenli mesajla `409 Conflict` cevabına çevrildi; ham entity, SQL veya version ayrıntısı response’a taşınmıyor.
- Handler testi önce mevcut generic fallback nedeniyle beklenen `409` yerine `500` alarak RED verdi; minimal eşleme sonrasında 1/1 GREEN tamamlandı.
- `backend/db/manual-migrations/2026-08-15-add-personel-version.sql`, mevcut production şemasına `personel.version BIGINT NOT NULL DEFAULT 0` kolonunu kontrollü ve tekrar çalıştırılabilir biçimde eklemek için hazırlandı. Ön/son kontroller, eski null satırların doldurulması ve yalnız uygulama rollback’inden sonra kullanılacak yorumlanmış geri alma komutu dosyada bulunuyor.
- Gerçek MySQL migration testi kolon ekleme, eski satırları `0` ile doldurma, null bırakmama, ikinci çalıştırmada tek kolon tutma ve önceden artmış version değerini koruma davranışlarını 1/1 GREEN doğruladı.
- Bu sınırlı teslim yeni migration framework’ü eklemedi ve mevcut development/test `ddl-auto=update` davranışını değiştirmedi. Bütün şemayı Flyway/Liquibase baseline migration’larına taşıma ayrı production hazırlık işi olarak backlog’da tutuluyor.
- Optimistic-lock handler, migration ve gerçek stale-transaction birleşik paketi 3/3 GREEN tamamlandı. Final tam backend doğrulaması 516 test, 0 failure, 0 error ve 0 skipped ile başarılı oldu; JaCoCo 40 sınıfı analiz etti, bütün coverage kontrolleri karşılandı ve Maven build başarılı tamamlandı.

## 2026-08-16 — Ekip dizini hiyerarşi filtreleme ve Yönetici görünümü

- Mevcut `GET /api/ekip-dizini/hiyerarsi` aktif HR/Admin için opsiyonel `arama`, `departmanId` ve `yetenekId` filtreleriyle genişletildi. Filtreli sonuç yalnız doğrudan eşleşmeleri ve onları aktif köklerine bağlayan zorunlu üst zincirleri tutuyor; eşleşmeyen alt, kardeş ve yan dallar budanıyor.
- Arama ad, soyad ve tam ad üzerinde case-insensitive çalışıyor; yetenek filtresi gerçek `PersonelYetenek` ilişkisini kullanıyor ve üç filtre `AND` ile birleşiyor. Sıfır/negatif ID `400`, bulunmayan pozitif ID güvenli boş `200` sonucu veriyor. Aynı doğrulama mevcut sayfalı ekip dizinine de uygulandı; PERSONEL’in zorunlu kendi-departman kapsamı korunuyor.
- Yalnız aktif ve veritabanında da `YONETICI` olan aktör için `GET /api/ekip-dizini/yonetici-hiyerarsim` eklendi. Response en fazla iki aktif üst → aktör → yalnız aktörün aktif doğrudan/dolaylı ast ağacını ve `aktorDepartman { id, ad }` metadata’sını taşıyor; kardeşler, başka kökler ve başka ekipler sızdırılmıyor.
- Yönetici filtresinde üst zincir ve aktör korunuyor; yalnız eşleşen astlar ile gerekli ara yöneticiler kalıyor. Eşleşme yoksa ast ağacı boşalıyor. Aktörün üstü yoksa aktör kök düğüm oluyor.
- Her recursive hiyerarşi düğümüne `yoneticinMi` eklendi. Değer yalnız principal’ın gerçek yönetici ilişkisiyle hesaplanıyor; gerçek yönetici filtreli forestte yoksa bütün düğümler `false` kalıyor ve ilgisiz ikinci dal eklenmiyor.
- HR/Admin tam graph döngü kontrolü ve Yöneticiye ait üst/erişilebilir ast kapsamındaki kontrollü `409` davranışı korundu. Mevcut `GET /api/ekip-dizini/yonetici-zincirim` rol matrisi ve response’u değiştirilmedi.
- TDD odaklı hiyerarşi suite’i 51/51, ekip dizini+hiyerarşi+security paketi 93/93 ve personel yönetimi dahil geniş regresyon 195/195 GREEN tamamlandı.
- Final tam backend doğrulaması 550 test, 0 failure, 0 error ve 0 skipped ile başarılı oldu. JaCoCo 41 sınıfı analiz etti, bütün coverage kontrolleri karşılandı ve Maven build başarılı tamamlandı.

## 2026-08-23 — Çok günlük tatil grupları

- Mevcut `/api/holidays` sözleşmesi geriye uyumlu biçimde dahil `bitisTarihi` alanıyla genişletildi. Alan gönderilmezse başlangıca eşitleniyor; eski tek günlük kayıt ve request davranışı korunuyor.
- Listeleme, görüntüleme aralığıyla kesişen grupları tek kayıt halinde döndürüyor. İş günü hesabı çok günlük tam/yarım gün gruplarının kapsadığı bütün hafta içlerini dikkate alıyor.
- Oluşturma/güncellemede ters veya 366 günden uzun aralık reddediliyor; hiçbir iki grup bir gün dahi çakışamıyor. Güncelleme ve silme grup kaydının tamamını etkiliyor; aynı bilgilerle update auditsiz no-op kalıyor.
- Şirket genelindeki mutation’lar tek pessimistic takvim kilidiyle sıralanıyor. Gerçek MySQL eşzamanlılık testi, `REPEATABLE READ` altında ikinci transaction’ın eski snapshot görmesi riskini yakaladı; overlap sorgusu güncel kilitli okumaya çevrilerek bir başarı + bir kontrollü `409` sonucu doğrulandı.
- `2026-08-23-add-tatil-group-range.sql` migration’ı mevcut satırları backfill ediyor, tarih aralığı constraint/index’ini ve idempotent global kilit satırını oluşturuyor. Gerçek MySQL migration testi tekrar çalıştırılabilirliği ve eski veriyi korumayı doğruladı.
- Grup oluşturma/güncelleme/silme auditleri aynı transaction’a katılıyor. No-op, overlap ve validation hataları audit üretmiyor; dış transaction hatasında grup aralığı ve audit birlikte rollback oluyor.
- Backend tamamlandı; takvimde aralığı tek görsel olay olarak gösterme ve bir güne tıklayarak grubun tamamını düzenleme frontend entegrasyonunda uygulanacak.

## 2026-08-23 — Canlı belge senkronizasyonu

- Kök `README.md`, eski “bütün modüller planlandı” tablosu yerine mevcut backend/frontend kapsamını, çalışma komutlarını ve manual migration sorumluluğunu anlatacak biçimde yenilendi.
- `frontend/README.md`, TailAdmin tanıtım metninden ürünün gerçek route/API akışlarına çevrildi. Tek günlük tatil frontend’inin hazır, çok günlük grup arayüzünün beklemede olduğu açıkça kaydedildi.
- `ROADMAP.md` içindeki eski demo ekip dizini, gerçek API’ye bağlı olmayan takvim, eksik role-menu ve `RoleRoute` maddeleri mevcut kodla uyumlu hale getirildi.
- `API_SPEC.md` içinde uygulanmış endpoint’ler ile rezervasyon, ekipman, izin ana akışı, onay, duyuru, yorum ve beğeni gibi planlanan endpoint’ler açıkça ayrıldı. Uygulanmış `GET /api/leave-requests/workday-preview` sözleşmesi eklendi.
- Frontend veya backend production kodu değiştirilmedi; çok günlük tatil gruplarının frontend aralık seçimi/gösterimi sonraki entegrasyon işi olarak bırakıldı.

## 2026-08-23 — Çok günlük tatil frontend entegrasyonu ve doğum günü takvimi kararı

- Takvim formu başlangıç/bitiş tarihi seçimini, çok günlük grubun tek FullCalendar olayı olarak gösterilmesini ve grubun herhangi bir gününden bütün grubun düzenlenip silinmesini destekleyecek biçimde frontend’e entegre edildi.
- Backend’in dahil `bitisTarihi` değeri FullCalendar’ın hariç `end` sözleşmesine güvenli biçimde çevriliyor; görünür takvim aralığı da backend sorgusuna dahil bitiş olarak gönderiliyor.
- Tatil frontend yardımcı/API testleri `node --test --test-isolation=none test/tatilTakvimi.test.js test/tatilTakvimiApi.test.js` ile 11/11 başarılı oldu.
- Takvimde doğum günleri bütün aktif şirket çalışanları için görünür olacak. Güvenli backend endpoint’i tamamlandı; frontend event entegrasyonu bekliyor.

## 2026-08-23 — Şirket geneli doğum günü takvimi backend’i

- `GET /api/birthdays` endpoint’i aktif authenticated bütün roller için eklendi. Zorunlu başlangıç/bitiş tarihiyle en fazla iki uç dahil 366 günlük aralık sorgulanabiliyor.
- Response yalnız `{ adSoyad, tarih, departman }` içeriyor. Doğum yılı, gerçek doğum tarihi, personel ID, kullanıcı adı ve iletişim alanları açılmıyor.
- Yıl sınırını geçen aralıklar destekleniyor; 29 Şubat doğum günleri yalnız artık yılda görünüyor ve başka güne taşınmıyor.
- Pasif hedef personeller listelenmiyor; pasif aktör `403`, bulunamayan principal `404`, unauthenticated istek `401` alıyor. Okuma audit oluşturmuyor.
- `DogumGunuTakvimiControllerIntegrationTest` gerçek MySQL üzerinde 14/14, ilgili regresyon paketi 109/109 ve tüm backend paketi JaCoCo kontrolüyle 688/688 başarılı oldu. Frontend takvim event entegrasyonu ayrı görev olarak bırakıldı.

## 2026-08-24 — İzin talebi oluşturma ve kendi taleplerini listeleme

- `GET /api/leave-types`, `GET /api/leave-requests/workday-preview`, `POST /api/leave-requests` ve `GET /api/leave-requests/my` backend akışları tamamlandı. PERSONEL ve HR ile yalnız atanmış aktif `YONETICI` üst yöneticisi bulunan non-root YONETICI talep işlemlerini kullanabiliyor; kök YONETICI ve ADMIN `403` alıyor. ADMIN yalnız izin türü lookup'ını okuyabiliyor.
- Create request'i yalnız izin türü, başlangıç/bitiş ve opsiyonel yönetici notunu kabul ediyor. İş günü backend'de yeniden hesaplanıyor; geçmiş/ters/366 günü aşan veya sıfır iş günü üreten aralıklar ve trim sonrası 1000 karakteri aşan not kontrollü `400` ile reddediliyor.
- Yıllık izin oluştururken onaylanmış tüketim ile bekleyen rezervasyon birlikte hesaba katılıyor. Bekleyen rezervasyon profil `kalanIzin` alanının yalnız onaylanmış tüketim anlamını değiştirmiyor. Aynı kişinin bekleyen/onaylı taleplerinde yalnız gerçek ortak iş günü çakışması `409` üretiyor; başka personelin talepleri bakiye veya çakışma kararına katılmıyor.
- Aktör `Personel` satırı `PESSIMISTIC_WRITE` ile kilitleniyor. Talep, bakiye/çakışma kontrolleri ve güvenli `IZIN_TALEBI_OLUSTURULDU` auditi aynı transaction içinde tamamlanıyor veya birlikte rollback oluyor. Audit aktörü ve hedefi talep sahibi; detay yalnız `İzin talebi oluşturuldu.` metnidir.
- `/my` principal ownership predicate'iyle sayfalı ve opsiyonel durum filtreli çalışıyor; varsayılan `page=0`, `size=10`, maksimum `size=100`, sıralama `talepTarihi DESC` ve `izinId DESC`. Lookup, önizleme ve liste read-only/auditsizdir.
- `2026-08-24-add-leave-request-foundation.sql` migration'ı dört talep durumunu, üç başlangıç izin türünü, gerekli sorgu indekslerini ve audit MySQL ENUM genişlemesini idempotent biçimde sağlar. Endpoint'ler mevcut ortamlarda açılmadan önce script uygulanmalıdır; `ddl-auto=update` ENUM migration'ının yerine geçmez.
- Owner-scope kanıtı başka personele ait bakiye/çakışma fixture'ıyla, profil regresyonu sabit saat ve açık tatil tablosu temizliğiyle güçlendirildi. Task 7 concurrency oracle'ının belirli personel UUID'sini `performance_schema.LOCK_DATA` değerine bağlaması taşınabilir ve küçük bir değişiklik olmadığı için mevcut fail-closed MySQL row-lock kanıtı korundu.
- Gerçek MySQL odaklı regresyon paketi 188/188, test-only ilgili paket 79/79 başarılı oldu. Final backend doğrulaması 738 test, 0 failure, 0 error ve 0 skipped ile tamamlandı; JaCoCo 56 sınıfı analiz etti ve bütün coverage kontrolleri karşılandı.
- Daha önce doğrulanmış şirket-geneli doğum günü frontend entegrasyonu canlı README durumlarına işlendi. İzin frontend ekranları uygulanmış sayılmadı; owner iptali, yönetici/Admin kuyrukları, onay/red ve hizmet yılına göre hak tahakkuku sonraki teslim olarak açık kaldı.

## 2026-08-26 — İzin karar/iptal akışı, hiyerarşi izin rozeti ve belge senkronizasyonu

- Aktif Yönetici için `GET /api/leave-requests/team`, aktif Admin için `GET /api/admin/leave-requests` sayfalı ve opsiyonel durum filtreli karar kuyrukları tamamlandı. Yönetici yalnız işlem anındaki aktif doğrudan çalışanlarını, Admin şirket genelindeki geçmişi görür.
- `PATCH /api/leave-requests/{talepId}/approve` ve `/reject` yalnız bekleyen talepleri işler. Yönetici yetkisi snapshot yerine güncel doğrudan yönetici ilişkisinden belirlenir; Admin bütün şirket taleplerinde karar verebilir. Red notu zorunlu ve trim sonrası en fazla 500 karakterdir.
- Owner’ın kendi bekleyen talebini body olmadan iptal etmesi için `PATCH /api/leave-requests/{talepId}/cancel` tamamlandı. PERSONEL, YONETICI, HR ve ADMIN kendi legacy bekleyen talebini iptal edebilir; Admin başka owner’ın talebini iptal edemez. Onaylanan, reddedilen veya iptal edilen karar geri alınamaz.
- İptal/onay/red aynı talepte yarıştığında pessimistic talep kilidi yalnız bir mutation’ın tamamlanmasını sağlar; kaybeden işlem güncel son durumu görüp `409 Talep artık işlem beklemiyor.` alır. Durum değişikliği ile güvenli audit aynı transaction içinde tamamlanır veya birlikte rollback olur.
- `2026-08-25-add-leave-request-decisions.sql` ve ardından `2026-08-25-add-leave-request-cancellation.sql`, karar alanı/indeksleri ile MySQL audit ENUM değerlerini idempotent biçimde taşır. `ddl-auto=update` mevcut ENUM’u güvenilir biçimde genişletmediğinden production’da migration sırası zorunludur.
- Hiyerarşi ve ekip dizini DTO’larındaki her güvenli personel düğümü backend hesaplı `bugunIzinDurumu: IZINLI | YARIM_GUN_IZINLI | null` taşır. Hesap yalnız response’un güvenlik/filtre kapsamındaki kişiler için toplu yapılır; kapsam dışı izin bilgisi sızdırılmaz. Dashboard ekip özeti aynı sözleşmeyi kullanır.
- Frontend; kendi izin oluşturma/geçmiş/iptal ekranını, Yönetici/Admin karar listelerini, onay/red işlemlerini, hiyerarşi izin rozetlerini ve backend hesaplı kalan izin kartını gerçek API’lere bağladı.
- İzin iptal/hiyerarşi odaklı regresyon paketi 188/188 başarılı oldu. Final backend doğrulaması 797 test, 0 failure, 0 error ve 0 skipped ile tamamlandı; JaCoCo 61 sınıfı analiz etti ve bütün coverage kontrolleri karşılandı. Bağımsız inceleme Critical/Important bulgu vermedi; iki küçük test kapsamı boşluğu sonrasında hedefli 2/2 ve izin talebi sınıfı 43/43 GREEN doğrulandı.
- Canlı `README.md`, `frontend/README.md`, `API_SPEC.md`, `SECURITY.md`, `ROADMAP.md` ve `PROGRESS.md` mevcut kodla eşitlendi. Tarihsel tasarım/rapor kayıtları değiştirilmedi.
- Gelecekte değerlendirilecek fakat bu teslimde uygulanmayacak iki ürün işi açık tutuldu: HR/Admin izin türü yönetimi (geçmiş kayıtları koruyan pasifleştirme yaklaşımı ayrıca kararlaştırılacak) ve hizmet yılına göre 14/20/26 gün tahakkuk ile devreden bakiye modeli (ayrı veri/migration tasarımı gerektirir).

## 2026-08-26 — Admin audit log okuma

- Yalnız aktif ve JWT–DB rolü eşleşen Admin için `GET /api/admin/audit-logs` eklendi. Endpoint tarih aralığı, eylem tipi, aktör ve hedef personel UUID filtrelerini `AND` semantiğiyle; `page=0`, `size=20` varsayılanları ve en fazla 100 kayıtla destekliyor.
- Liste `olusmaZamani DESC, auditId DESC` kararlı sırasıyla dönüyor. DTO yalnız güvenli olay, aktör ve hedef alanlarını taşıyor; aktörü bulunmayan eski giriş kayıtlarında kullanıcı adı snapshot’ı korunuyor. Okuma read-only ve auditsizdir.
- `PERSONEL`, `YONETICI`, `HR`, pasif Admin ve JWT–DB rol uyuşmazlığı `403`; bulunmayan principal `404`; token yoksa `401` alıyor. Ters tarih ve canonical olmayan UUID’ler kontrollü `400`, bilinmeyen geçerli UUID filtreleri boş `200` sonucu veriyor.
- `2026-08-26-add-audit-log-read-index.sql`, `(olusma_zamani, log_id)` indeksini idempotent biçimde ekliyor. Gerçek MySQL migration testi indeks sırasını, tekrar çalıştırılabilirliği ve mevcut audit verisinin korunduğunu doğruluyor.
- Audit endpoint integration sınıfı gerçek MySQL üzerinde 9/9; migration testi 1/1 GREEN tamamlandı. Bu backend teslimi anında frontend audit ekranı kapsam dışındaydı; sonraki frontend entegrasyonu aşağıdaki kayıtta tamamlandı.
- Final backend doğrulaması 807 test, 0 failure, 0 error ve 0 skipped ile tamamlandı; JaCoCo 63 sınıfı analiz etti ve bütün coverage kontrolleri karşılandı.

## 2026-08-26 — Admin audit log frontend entegrasyonu

- Admin audit log ekranı `GET /api/admin/audit-logs` sözleşmesine bağlandı. Tarih aralığı, eylem tipi, aktör UUID ve hedef UUID filtreleri, sayfalama, loading/empty/error durumları ve Türkçe tarih-saat gösterimi tamamlandı.
- `/yonetim/audit-log` route’u ile sidebar bağlantısı yalnız `ADMIN` rolüne açıldı; diğer roller için hem menü görünürlüğü hem route guard sınırı korundu.
- Aktörü veya hedefi bulunmayan eski audit kayıtları güvenli fallback metinleriyle gösteriliyor; bilinmeyen eylem enum değerleri ham güvenli değerle temsil ediliyor.
- Gelecekte aktör/hedef filtrelerinin ad veya kullanıcı adıyla aranabilir seçime dönüştürülmesi açık bırakıldı; kesin backend filtresi UUID üzerinden korunacaktır.
- Hedefli `node:test` doğrulaması 4/4 başarılı, audit kapsamındaki dosyalarda hedefli ESLint temiz ve production Vite build başarılı oldu. Proje genel ESLint kontrolünde audit değişikliğinden bağımsız mevcut `CountryMap.tsx` ve `svg.d.ts` hataları devam ediyor.

## 2026-08-27 — Oda rezervasyonu backend'i ve dashboard doluluğu

- Aktif `PERSONEL`, `YONETICI`, `HR` ve `ADMIN` için oda kataloğu, güvenli rezervasyon takvimi, doğrudan rezervasyon oluşturma ve kendi geçmişini görüntüleme akışları tamamlandı. Admin ayrıca şirket genelindeki sayfalı rezervasyon geçmişini görüntüleyebilir.
- Rezervasyonlar tam saat dilimlerinde, yapılandırılabilir 08:00–18:00 çalışma aralığında, en fazla dört saat ve en fazla 90 gün ileriye oluşturulur. Hafta sonu/tam gün tatil kapalıdır; yarım gün tatilde yalnız 08:00–13:00 kullanılabilir. Bugün için geçmiş saatler ve başlamış aralıklar reddedilir.
- Katılımcı sayısı rezervasyon sahibini içerir ve oda kapasitesini aşamaz. Aynı odadaki çakışmalar ile aynı kişinin farklı odalardaki eşzamanlı rezervasyonları pessimistic kilitlerle engellenir; bitişik rezervasyonlara izin verilir.
- Takvimde rezervasyonu oluşturan kişinin adı görünür; başlık ve katılımcı sayısı yalnız rezervasyon sahibi veya veritabanında aktif `ADMIN` olan aktör için açılır. İptal edilen kayıtlar genel takvimden çıkar, geçmiş listelerinde `IPTAL_EDILDI` durumuyla korunur.
- Owner yalnız kendi başlamamış aktif rezervasyonunu, Admin ise kendi veya başkasına ait başlamamış aktif rezervasyonu iptal edebilir. Admin başkasının rezervasyonunu iptal ederken neden zorunludur; başarılı oluşturma ve iptaller güvenli sabit detaylı audit ile aynı transaction içinde tamamlanır.
- Personel pasifleştirme, hedefin henüz başlamamış aktif rezervasyonlarını aynı transaction içinde iptal eder. Create–pasifleştirme yarışının her iki commit sırası gerçek MySQL satır kilitleriyle doğrulandı; pasif personel adına aktif rezervasyon kalmaması güvence altına alındı.
- `2026-08-26-add-room-reservations.sql` migration'ı oda ve rezervasyon tablolarını, indeks/constraint'leri, `Büyük Salon` (12) ile `Küçük Oda` (6) seed kayıtlarını ve gerekli audit ENUM değerlerini idempotent biçimde taşır. Mevcut MySQL ortamında endpoint'ler açılmadan önce uygulanmalıdır.
- Dashboard `odaDoluluk` alanı placeholder olmaktan çıkarıldı. Her oda için güvenli `{ odaId, odaAd, kapasite, anlikDurum, dolulukYuzdesi, doluAraliklar[] }` özeti dönüyor; başlık, owner ve katılımcı sayısı toplu özete sızdırılmıyor.
- Tekrarlayan rezervasyon, rezervasyon düzenleme ve davetli katılımcı yönetimi MVP sonrası ürün kararları olarak açık bırakıldı. Frontend create/geçmiş/iptal ekranları ve oda doluluk kartı bu backend sözleşmelerini kullanarak ayrıca tamamlanacaktır.
- Final backend doğrulaması gerçek MySQL Testcontainers üzerinde 853 test, 0 failure, 0 error ve 0 skipped ile tamamlandı; JaCoCo 69 sınıfı analiz etti ve bütün coverage kontrolleri karşılandı. Tam koşuda yakalanan tarihsel izin-iptal migration testinin güncel enum'a yanlış bağımlılığı ile `PersonelServiceTest` pasifleştirme fixture'larının eski kilitsiz repository çağrısı düzeltildi; üretim sözleşmesi değiştirilmedi.

## 2026-08-27 — Oda rezervasyonu frontend entegrasyonu ve tatil çakışma koruması

- Oda rezervasyonu frontend’i günlük oda uygunluk grid’i, oluşturma formu, owner geçmiş/iptal akışı ve Admin şirket geneli geçmiş/iptal görünümüyle tamamlandı. Route ve rol bazlı sidebar girişi bütün aktif rollere açıldı; Admin kapsam seçimi aynı sayfada korunuyor.
- Aktif rezervasyonlar şirket takviminde güvenli alanlarla gösteriliyor. Dashboard `odaDoluluk` verisi gerçek oda doluluk kartına bağlandı; loading, error ve boş durumlar mevcut ürün bileşenleriyle işlendi.
- Tatil oluşturma ve gerçek güncelleme artık tatil saatleriyle çakışan aktif rezervasyon varsa atomik `409 Conflict` döndürüyor. Tam gün tatil aralıktaki bütün aktif rezervasyonlarla; yarım gün tatil `13:00` sonrasına taşan rezervasyonlarla çakışıyor. İptal edilen rezervasyonlar engel olmuyor ve kullanıcı rezervasyonları iptal ettikten sonra aynı tatil isteğini yeniden gönderebiliyor.
- Tatil mutation’ı, takvim kilidinden sonra bütün oda satırlarını kimlik sırasıyla kilitleyerek rezervasyon create işlemiyle yarışta güncel durumu koruyor. Çakışmada tatil ve audit yan etkisi oluşmuyor; frontend backend’in güvenli `409` mesajını formu kapatmadan ve alanları temizlemeden gösteriyor.
- Rezervasyon/tatil/dashboard odaklı 26 frontend `node:test` senaryosu başarılı oldu. Frontend production build tamamlandı; mevcut CSS minification ve büyük chunk uyarıları devam ediyor ancak build hatası oluşmadı.
- İlgili backend regresyon paketi 137/137 başarılı oldu. Final backend doğrulaması gerçek MySQL Testcontainers üzerinde 858 test, 0 failure, 0 error ve 0 skipped ile tamamlandı; JaCoCo 69 sınıfı analiz etti ve bütün coverage kontrolleri karşılandı.

## 2026-09-01 — Ekipman talep, envanter ve zimmet backend'i

- Aktif ekipman tipi kataloğu ile yalnız Admin'e açık tip ve fiziksel envanter yönetimi tamamlandı. Seri numarası tekilliği, aktif tip doğrulaması, `DEPODA`, `PERSONELDE` ve `KULLANIM_DISI` geçişleri, no-op davranışı ve güvenli audit aynı service/transaction sınırları içinde uygulanıyor.
- Aktif ve JWT–DB rolü uyuşan `PERSONEL` ile `HR`, yalnız kendi ekipman taleplerini oluşturabiliyor, sayfalı geçmişini görebiliyor ve bekleyen talebini güncelleyip iptal edebiliyor. Owner principal'dan belirleniyor; aynı kişi ve tip için ikinci bekleyen talep atomik olarak engelleniyor.
- Aktif `YONETICI` yalnız aktif doğrudan çalışanlarının taleplerini, aktif `ADMIN` şirket genelindeki talepleri görebiliyor. Onayda yetkili talep kapsamında aynı tipteki `DEPODA` cihaz seçiliyor; owner → talep → cihaz → zimmet kilit sırası ile talep, cihaz, zimmet ve audit birlikte commit/rollback oluyor. Red, iptal ve terminal durum tekrarları kontrollü hata üretiyor.
- Bütün aktif roller kendi zimmet geçmişini güvenli DTO ile görebiliyor. Admin şirket genelindeki açık/geçmiş zimmetleri filtreli ve sayfalı görüntüleyip açık zimmeti iade edebiliyor; iade geçmiş satırı silmeden cihazı tekrar `DEPODA` durumuna getiriyor.
- Personel pasifleştirme ve self-service dışı rol geçişi, bekleyen ekipman taleplerini aynı dış transaction içinde iptal ediyor; açık zimmet ve `PERSONELDE` cihaz geçmişi korunuyor. Create/karar/iptal/iade/pasifleştirme yarışları gerçek MySQL satır kilitleriyle doğrulandı.
- `backend/db/manual-migrations/2026-08-28-add-equipment-workflow.sql`, kolonları, constraint/index'leri, açık-zimmet tekilliğini, Türkçe tip adı ve seri numarası tekillik semantiklerini ve ekipman audit ENUM değerlerini idempotent biçimde taşıyor. Script production ve mevcut ortam veritabanlarında endpoint'ler açılmadan önce manuel uygulanmalıdır; `ddl-auto=update` mevcut MySQL ENUM ve constraint'lerini güvenilir biçimde genişletmez.
- Ekipman akışı `EKIPMAN_TIPI_EKLENDI`, `EKIPMAN_TIPI_GUNCELLENDI`, `EKIPMAN_EKLENDI`, `EKIPMAN_GUNCELLENDI`, `EKIPMAN_TALEBI_OLUSTURULDU`, `EKIPMAN_TALEBI_IPTAL_EDILDI`, `EKIPMAN_TALEBI_ONAYLANDI`, `EKIPMAN_TALEBI_REDDEDILDI` ve `ZIMMET_IADE_EDILDI` güvenli olaylarını kullanıyor; açıklama, karar notu ve envanter ayrıntıları audit detayına taşınmıyor. Talep açıklaması güncellemesi ayrı audit üretmiyor; fiziksel zimmet, talep onayıyla aynı transaction içinde oluşup `EKIPMAN_TALEBI_ONAYLANDI` olayıyla izleniyor.
- Hedefli ekipman/personel/security regresyonu gerçek MySQL üzerinde 228/228 başarılı oldu. Final backend doğrulaması 1045 test, 0 failure, 0 error ve 0 skipped ile tamamlandı; JaCoCo 83 sınıfı analiz etti ve bütün coverage kontrolleri karşılandı.
- İlk tam koşum, tarihsel rezervasyon migration testinin güncel `AuditEylem` enum'una yanlış bağımlı test oracle'ını ortaya çıkardı. Test sabit tarihsel rezervasyon ENUM sözleşmesine bağlandı; production kodu veya rezervasyon migration'ı değiştirilmedi. Odaklı migration testi 1/1 ve yeniden çalıştırılan tam suite 1045/1045 başarılı oldu.
- Backend sözleşmesi `API_SPEC.md`, güvenlik ve migration riskleri `SECURITY.md`, teslim durumu `ROADMAP.md`, genel durum `README.md` ile eşitlendi. Frontend entegrasyonu henüz uygulanmış sayılmıyor. Doğrudan Admin zimmeti, bakım/arıza yaşam döngüsü, çoklu depo, QR/barkod ve tipe özgü dinamik alanlar MVP sonrası kapsamda tutuluyor.

## 2026-09-04 — Geçmiş izin talebinin sonradan onaylanmasını engelleme

- İzin onay service’i karar anında talebin başlangıç tarihini Europe/Istanbul gününe göre yeniden doğruluyor. Başlamış veya geçmiş bekleyen talepler, bitiş tarihi henüz geçmemiş olsa dahi `409 Başlangıç tarihi geçmiş izin talebi onaylanamaz.` ile reddediliyor; bugün başlayan ve gelecekte başlayacak taleplerin mevcut onay davranışı korunuyor.
- Tarih doğrulaması durum/bakiye değişikliği ve audit yazımından önce çalışıyor. Başarısız onay talebi, izin kaydını, personel bakiyesini veya audit kayıtlarını değiştirmiyor. Red ve owner iptal sözleşmeleri değiştirilmedi.
- Yönetici ve Admin geçmiş-onay senaryoları, başlamış fakat sürmekte olan aralık, bugünün sınır değeri ve persistence hata regresyonu gerçek MySQL entegrasyonu ile servis birim testlerinde doğrulandı. Odaklı paket 39 test, 0 failure, 0 error ve 0 skipped ile tamamlandı. Tam backend doğrulaması 1049 test, 0 failure, 0 error ve 0 skipped ile geçti; JaCoCo raporu üretildi ve coverage check başarılı oldu.
