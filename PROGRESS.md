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