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