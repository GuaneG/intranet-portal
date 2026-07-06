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
#### Open questions / To ask
- Should reservations be only hour-based?
> Example: 09:00-10:00, 10:00-11:00
- Or should minute-based reservations also be supported?
> Example: 09:30-10:30
- For equipment management, should ekipman_durum be limited to only:
>  - depoda
>  - personelde 
- Should returned equipment be represented only by setting the equipment status back to ```depoda```, or should return history/logs be stored separately?
- Should leave types and leave statuses be stored as plain varchar values in ```izin_bilgileri```, or should they be modeled as separate lookup tables?
#### Assumptions made to proceed (to be confirmed with mentor)
- **Reservations:** assumed hour-based only (spec says "saatlik" + hourly grid mockup).
  ER: `baslangic_saat` / `bitis_saat` stored as integer hours. If minute-based is
  needed, only the column type changes (integer → TIME).
- **ekipman_durum:** assumed only `depoda` / `personelde`. "İade edildi" is not an
  equipment status — a return is modeled by filling `iade_tarihi` on the assignment
  record, and the equipment goes back to `depoda`.
- **Return history:** assumed it should be kept. `zimmetleme_bilgileri` rows are never
  deleted; a closed row (iade_tarihi set) *is* the history entry.
- **Leave types vs statuses:** assumed mixed approach. `izin_turu` became a lookup
  table (HR-extensible, consistent with departman/ekipman_tipi), statuses stayed
  varchar (fixed workflow: pending/approved/rejected).

