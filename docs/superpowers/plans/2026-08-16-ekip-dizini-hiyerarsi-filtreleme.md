# Ekip Dizini Hiyerarşi Filtreleme Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** HR/Admin tam hiyerarşisine güvenli filtreleme eklemek, aktif Yöneticiye yalnız kendi üst zinciri ve ast ağacını veren yeni endpoint'i sağlamak ve bütün hiyerarşi düğümlerinde gerçek ilişkiye dayalı `yoneticinMi` üretmek.

**Architecture:** Aktif personel graph'ı repository'den tek seferde okunur; doğrudan filtre eşleşmeleri ikinci bir ID sorgusuyla belirlenir. Service rol bazlı güvenli kapsamı, döngü kontrolünü, ancestor closure'ı ve budanmış recursive DTO'yu oluşturur; controller yalnız HTTP parametrelerini aktarır.

**Tech Stack:** Java 21, Spring Boot 3.5, Spring MVC, Spring Security method authorization, Spring Data JPA/JPQL, MySQL 8 Testcontainers, JUnit 5, MockMvc.

**Spec:** `docs/superpowers/specs/2026-08-15-ekip-dizini-hiyerarsi-filtreleme-design.md`

## Global Constraints

- Frontend dosyalarına ve ilgisiz backend modüllerine dokunma.
- Hiçbir Git komutu çalıştırma; bu planda commit adımı yoktur.
- `GET /api/ekip-dizini/yonetici-zincirim` sözleşmesini ve rol matrisini değiştirme: PERSONEL/YONETICI `200`, HR/ADMIN `403`.
- Entity veya personel/yönetici ID'si response'a açma.
- Bütün yeni GET akışları read-only ve auditsiz olmalıdır.
- Tüm production değişikliklerinden önce ilgili test gerçek ve beklenen nedenle RED görülmelidir.
- `API_SPEC.md`, `SECURITY.md`, `PROGRESS.md` ve `ROADMAP.md` bu uygulama planında değiştirilmez; uygulama bittikten sonra ayrıca kullanıcı onayı istenir.
- Testcontainers Docker erişimi Codex ortamında engellenirse test komutunu kullanıcı çalıştırır; RED/GREEN çıktısı görülmeden sonraki production adımına geçilmez.

---

## File Structure

**Create:**

- `backend/src/main/java/com/jforce/backend/service/EkipDiziniFiltreleri.java`: arama normalizasyonu, pozitif ID doğrulaması ve filtre-var-yok bilgisini taşıyan immutable değer nesnesi.
- `backend/src/main/java/com/jforce/backend/model/dto/response/YoneticiHiyerarsisiResponse.java`: yalnız Yönetici endpoint'i için `aktorDepartman` ve `kokler` response'u.

**Modify:**

- `backend/src/main/java/com/jforce/backend/controller/YoneticiHiyerarsisiController.java`: tam hiyerarşi query parametreleri ve yeni `/yonetici-hiyerarsim` route'u.
- `backend/src/main/java/com/jforce/backend/service/YoneticiHiyerarsisiService.java`: filtreli retained graph, `yoneticinMi`, Yönetici güvenli kapsamı ve recursive DTO üretimi.
- `backend/src/main/java/com/jforce/backend/service/EkipDiziniService.java`: ortak filtre değer nesnesiyle pozitif ID doğrulaması; mevcut Personel departman AND sınırı korunur.
- `backend/src/main/java/com/jforce/backend/repository/PersonelRepository.java`: aktif doğrudan filtre eşleşmelerini ID olarak getiren JPQL sorgusu.
- `backend/src/main/java/com/jforce/backend/model/dto/response/HiyerarsiDugumResponse.java`: her düğüme zorunlu `boolean yoneticinMi`.
- `backend/src/test/java/com/jforce/backend/YoneticiHiyerarsisiControllerIntegrationTest.java`: tam forest ve Yönetici endpoint'i davranış testleri.
- `backend/src/test/java/com/jforce/backend/EkipDiziniControllerIntegrationTest.java`: pozitif ID doğrulaması ve Personel departman sınırı regresyonları.

---

### Task 1: Ortak filtre doğrulaması ve Personel departman sınırı

**Files:**

- Create: `backend/src/main/java/com/jforce/backend/service/EkipDiziniFiltreleri.java`
- Modify: `backend/src/main/java/com/jforce/backend/service/EkipDiziniService.java`
- Test: `backend/src/test/java/com/jforce/backend/EkipDiziniControllerIntegrationTest.java`

**Interfaces:**

- Produces: `EkipDiziniFiltreleri.olustur(String arama, Integer departmanId, Integer yetenekId)`.
- Produces accessors: `arama()`, `departmanId()`, `yetenekId()`, `aktifMi()`.
- Later tasks consume the same value object in `YoneticiHiyerarsisiService`.

- [ ] **Step 1: Split the existing mixed ID test and add the missing scope regression**

Replace `olmayanVeyaNegatifFiltreIdsiBosSayfaDoner` with these observable contracts:

```java
@ParameterizedTest
@CsvSource({"departmanId, 999999", "yetenekId, 999999"})
void bulunmayanPozitifFiltreIdsiBosSayfaDoner(String parametre, String deger)
        throws Exception {
    mockMvc.perform(get("/api/ekip-dizini")
                    .param(parametre, deger)
                    .header(HttpHeaders.AUTHORIZATION,
                            "Bearer " + token(aktor, RolAdi.PERSONEL)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.icerik").isEmpty())
            .andExpect(jsonPath("$.toplamKayit").value(0));
}

@ParameterizedTest
@CsvSource({"departmanId, 0", "departmanId, -1", "yetenekId, 0", "yetenekId, -1"})
void pozitifOlmayanFiltreIdsi400Doner(String parametre, String deger)
        throws Exception {
    mockMvc.perform(get("/api/ekip-dizini")
                    .param(parametre, deger)
                    .header(HttpHeaders.AUTHORIZATION,
                            "Bearer " + token(aktor, RolAdi.PERSONEL)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400));
}
```

Add `personelFarkliDepartmanFiltresiyleKendiDepartmanSiniriniAsamaz`: create one active person in actor's department and one in another department; request the other department ID as PERSONEL; assert `200`, empty `icerik`, and `toplamKayit=0`.

Mutation caught: removing positive-ID validation or removing forced-personnel-department AND scope makes these tests fail.

- [ ] **Step 2: Run the focused RED tests**

Run:

```powershell
.\mvnw.cmd "-Dtest=EkipDiziniControllerIntegrationTest#pozitifOlmayanFiltreIdsi400Doner+personelFarkliDepartmanFiltresiyleKendiDepartmanSiniriniAsamaz" "-Djacoco.skip=true" test
```

Expected: the non-positive ID invocations fail because current code returns `200`; the department-boundary test should already pass and acts as a characterization guard.

- [ ] **Step 3: Add the immutable shared filter value object**

```java
package com.jforce.backend.service;

import com.jforce.backend.exception.GecersizIstekException;

public record EkipDiziniFiltreleri(
        String arama,
        Integer departmanId,
        Integer yetenekId
) {
    public static EkipDiziniFiltreleri olustur(
            String arama,
            Integer departmanId,
            Integer yetenekId
    ) {
        if (departmanId != null && departmanId <= 0) {
            throw new GecersizIstekException("Departman ID pozitif olmalıdır.");
        }
        if (yetenekId != null && yetenekId <= 0) {
            throw new GecersizIstekException("Yetenek ID pozitif olmalıdır.");
        }

        String normalizeArama = arama == null ? null : arama.trim();
        if (normalizeArama != null && normalizeArama.isEmpty()) {
            normalizeArama = null;
        }

        return new EkipDiziniFiltreleri(
                normalizeArama,
                departmanId,
                yetenekId
        );
    }

    public boolean aktifMi() {
        return arama != null || departmanId != null || yetenekId != null;
    }
}
```

Update `EkipDiziniService.ekipDizininiGetir` to create this value after pagination validation and pass its three normalized accessors to `findAktifEkipDizini`. Remove the now-duplicated private `aramayiNormalizeEt` method. Do not change `gorunurDepartmanId`; it remains actor's department for PERSONEL.

- [ ] **Step 4: Run the full directory integration class**

```powershell
.\mvnw.cmd "-Dtest=EkipDiziniControllerIntegrationTest" "-Djacoco.skip=true" test
```

Expected: all tests pass; non-positive IDs are `400`, missing positive IDs remain empty `200`, and Personel cannot escape its department.

---

### Task 2: Shared hierarchy node contract and unfiltered compatibility

**Files:**

- Modify: `backend/src/main/java/com/jforce/backend/model/dto/response/HiyerarsiDugumResponse.java`
- Modify: `backend/src/main/java/com/jforce/backend/service/YoneticiHiyerarsisiService.java`
- Test: `backend/src/test/java/com/jforce/backend/YoneticiHiyerarsisiControllerIntegrationTest.java`

**Interfaces:**

- Produces record shape: `(ad, soyad, departman, rol, profilFoto, boolean yoneticinMi, dogrudanBaglilar)`.
- Produces service conversion accepting `String aktorYoneticiId` so every later tree uses the same relationship rule.

- [ ] **Step 1: Add failing `yoneticinMi` assertions to existing unfiltered forest tests**

In `hrTumAktifOrganizasyonForestiniGuvenliAlanlarlaGorur`, give `hrAktor` a real manager contained in the forest. Assert that exact node has `yoneticinMi=true`, every other inspected node is `false`, and no `personelId`/`yoneticiId` field exists.

Also add `yoneticisiOlmayanAdminTumDugumlerdeYoneticinMiFalseGorur`, asserting the recursive nodes expose the boolean and none is true.

Mutation caught: hardcoded true, name/role inference, or missing boolean fails.

- [ ] **Step 2: Run RED**

```powershell
.\mvnw.cmd "-Dtest=YoneticiHiyerarsisiControllerIntegrationTest#hrTumAktifOrganizasyonForestiniGuvenliAlanlarlaGorur+yoneticisiOlmayanAdminTumDugumlerdeYoneticinMiFalseGorur" "-Djacoco.skip=true" test
```

Expected: JSON path for `yoneticinMi` is absent.

- [ ] **Step 3: Extend the node DTO and central conversion**

Add the boolean before the child list:

```java
public record HiyerarsiDugumResponse(
        String ad,
        String soyad,
        String departman,
        RolAdi rol,
        String profilFoto,
        boolean yoneticinMi,
        List<HiyerarsiDugumResponse> dogrudanBaglilar
) { }
```

Make `tumHiyerarsiyiGetir` retain the validated actor returned by `aktifAktoruGetir`. Compute:

```java
String aktorYoneticiId = aktor.getYonetici() == null
        ? null
        : aktor.getYonetici().getPersonelId();
```

Pass it through recursive conversion and set:

```java
personel.getPersonelId().equals(aktorYoneticiId)
```

Do not add IDs to the response.

- [ ] **Step 4: Run the complete existing hierarchy class**

```powershell
.\mvnw.cmd "-Dtest=YoneticiHiyerarsisiControllerIntegrationTest" "-Djacoco.skip=true" test
```

Expected: all existing full-forest, personal-chain, authorization and cycle tests pass with the added boolean.

---

### Task 3: HR/Admin filtered forest and ancestor closure

**Files:**

- Modify: `backend/src/main/java/com/jforce/backend/controller/YoneticiHiyerarsisiController.java`
- Modify: `backend/src/main/java/com/jforce/backend/repository/PersonelRepository.java`
- Modify: `backend/src/main/java/com/jforce/backend/service/YoneticiHiyerarsisiService.java`
- Test: `backend/src/test/java/com/jforce/backend/YoneticiHiyerarsisiControllerIntegrationTest.java`

**Interfaces:**

- Produces repository method `List<String> findAktifHiyerarsiEslesenPersonelIds(String arama, Integer departmanId, Integer yetenekId)`.
- Changes service signature to `tumHiyerarsiyiGetir(String aktorId, Integer departmanId, Integer yetenekId, String arama)`.
- Controller consumes optional `Integer departmanId`, `Integer yetenekId`, `String arama`.

- [ ] **Step 1: Add one focused pruning RED test**

Create a forest with one matching leaf, its two ancestors, a sibling branch and a separate root. Call `/hiyerarsi?arama=<leaf>`. Assert response contains only one root and the exact ancestor → ancestor → leaf chain; sibling and separate root names must not occur. Assert the real manager is `true` only if retained.

Name the test `hrAramaFiltresindeYalnizEslesenKisiVeKokeKadarZinciriKalir`.

- [ ] **Step 2: Run RED**

```powershell
.\mvnw.cmd "-Dtest=YoneticiHiyerarsisiControllerIntegrationTest#hrAramaFiltresindeYalnizEslesenKisiVeKokeKadarZinciriKalir" "-Djacoco.skip=true" test
```

Expected: current endpoint ignores `arama` and returns sibling/unrelated branches.

- [ ] **Step 3: Add the direct-match JPQL query**

```java
@Query("""
        SELECT p.personelId
        FROM Personel p
        WHERE p.calisiyorMu = true
          AND (:departmanId IS NULL OR p.departman.departmanId = :departmanId)
          AND (:yetenekId IS NULL OR EXISTS (
                SELECT 1 FROM PersonelYetenek py
                WHERE py.personel = p
                  AND py.yetenek.yetenekId = :yetenekId
          ))
          AND (:arama IS NULL
               OR LOWER(p.adi) LIKE CONCAT('%', CONCAT(LOWER(:arama), '%'))
               OR LOWER(p.soyadi) LIKE CONCAT('%', CONCAT(LOWER(:arama), '%'))
               OR LOWER(CONCAT(p.adi, CONCAT(' ', p.soyadi)))
                    LIKE CONCAT('%', CONCAT(LOWER(:arama), '%')))
        """)
List<String> findAktifHiyerarsiEslesenPersonelIds(
        @Param("arama") String arama,
        @Param("departmanId") Integer departmanId,
        @Param("yetenekId") Integer yetenekId
);
```

- [ ] **Step 4: Add controller parameters and retained-set logic**

Controller passes the three optional params to the new service signature. Service must:

1. call `EkipDiziniFiltreleri.olustur`,
2. load and globally cycle-check all active persons before pruning,
3. if no filter, retain all active IDs,
4. if filtered, start from repository match IDs and walk each active manager link to the root,
5. build roots/children only from retained IDs,
6. compute `yoneticinMi` only among returned nodes.

Use concrete helper signatures:

```java
private Set<String> retainedPersonelIds(
        EkipDiziniFiltreleri filtreler,
        Map<String, Personel> aktifPersonelHaritasi
)

private HiyerarsiResponse forestOlustur(
        Map<String, Personel> aktifPersonelHaritasi,
        Set<String> retainedIds,
        String aktorYoneticiId
)
```

If filtered direct matches are empty, return `new HiyerarsiResponse(List.of())`; do not add the actor's manager as a second unrelated branch.

- [ ] **Step 5: Add remaining filter and validation tests**

Add tests proving:

- department + skill + trimmed case-insensitive search combine with AND,
- skill uses a real `personel_yetenek` row,
- blank search preserves full forest,
- missing positive ID returns `200` and empty `kokler`,
- `0`/negative department and skill IDs return `400`,
- text IDs return existing controlled `400`,
- actor's real manager pruned from filtered forest makes every `yoneticinMi=false`.

- [ ] **Step 6: Run the HR/Admin hierarchy class**

```powershell
.\mvnw.cmd "-Dtest=YoneticiHiyerarsisiControllerIntegrationTest" "-Djacoco.skip=true" test
```

Expected: filtered and unfiltered HR/Admin tests, authorization tests and legacy-cycle tests all pass.

---

### Task 4: Yönetici-only unfiltered hierarchy endpoint

**Files:**

- Create: `backend/src/main/java/com/jforce/backend/model/dto/response/YoneticiHiyerarsisiResponse.java`
- Modify: `backend/src/main/java/com/jforce/backend/controller/YoneticiHiyerarsisiController.java`
- Modify: `backend/src/main/java/com/jforce/backend/service/YoneticiHiyerarsisiService.java`
- Test: `backend/src/test/java/com/jforce/backend/YoneticiHiyerarsisiControllerIntegrationTest.java`

**Interfaces:**

- Produces `YoneticiHiyerarsisiResponse(LookUpResponse aktorDepartman, List<HiyerarsiDugumResponse> kokler)`.
- Produces service method `yoneticiHiyerarsisiniGetir(String aktorId, Integer departmanId, Integer yetenekId, String arama)`.
- Produces route `GET /api/ekip-dizini/yonetici-hiyerarsim`.

- [ ] **Step 1: Add one focused route RED test**

Create two upper managers, actor manager, direct report, indirect report, actor sibling, sibling's report and another root. Call the new endpoint as actor. Assert exactly:

```text
second upper → direct upper → actor → direct report → indirect report
```

Assert sibling, sibling report and other root are absent; assert `aktorDepartman.id/ad`; assert only direct upper has `yoneticinMi=true`.

Name: `yoneticiEnFazlaIkiUstunuKendisiniVeYalnizKendiAktifAstAgaciniGorur`.

- [ ] **Step 2: Run RED**

```powershell
.\mvnw.cmd "-Dtest=YoneticiHiyerarsisiControllerIntegrationTest#yoneticiEnFazlaIkiUstunuKendisiniVeYalnizKendiAktifAstAgaciniGorur" "-Djacoco.skip=true" test
```

Expected: `404` because the route does not exist.

- [ ] **Step 3: Add response DTO, route and role boundary**

```java
public record YoneticiHiyerarsisiResponse(
        LookUpResponse aktorDepartman,
        List<HiyerarsiDugumResponse> kokler
) { }
```

Controller adds `@GetMapping("/yonetici-hiyerarsim")` with the same three optional params. Service method is annotated:

```java
@Transactional(readOnly = true)
@PreAuthorize("hasRole('YONETICI')")
```

Service also verifies the database actor is active and exactly `RolAdi.YONETICI`; JWT role alone is not trusted.

- [ ] **Step 4: Build the unfiltered safe scope**

From the active graph:

- walk actor's active managers upward and retain at most two,
- traverse child adjacency downward starting only at actor and retain all active descendants,
- retain actor,
- never traverse children of the retained upper managers except the next chain node,
- run full active upper-chain cycle validation for actor and DFS cycle validation only on actor's reachable descendant scope,
- build one pruned forest from retained IDs.

If actor has no active upper, actor is the root. `aktorDepartman` comes from actor's real department ID and name.

- [ ] **Step 5: Add security and shape tests**

Add parameterized HTTP tests:

- PERSONEL/HR/ADMIN receive `403`,
- passive YONETICI receives `403`, missing principal `404`, no token `401`,
- passive report is absent,
- a third upper is absent,
- actor without an upper is `kokler[0]` and reports are under its `dogrudanBaglilar`,
- safe fields only; no personel ID, manager ID, username, email or phone.

- [ ] **Step 6: Run the hierarchy integration class**

```powershell
.\mvnw.cmd "-Dtest=YoneticiHiyerarsisiControllerIntegrationTest" "-Djacoco.skip=true" test
```

Expected: all full-tree, manager-tree, personal-chain, role and cycle tests pass.

---

### Task 5: Yönetici filtered pruning and scoped cycle safety

**Files:**

- Modify: `backend/src/main/java/com/jforce/backend/service/YoneticiHiyerarsisiService.java`
- Test: `backend/src/test/java/com/jforce/backend/YoneticiHiyerarsisiControllerIntegrationTest.java`

**Interfaces:**

- Consumes `findAktifHiyerarsiEslesenPersonelIds` and intersects results with actor descendant IDs.
- Preserves upper IDs + actor ID independently of filter match.

- [ ] **Step 1: Add focused filtered-manager RED test**

Create actor with two uppers, a matching indirect report below an intermediate manager, a non-matching sibling report and a matching person in another manager's team. Filter by search/department/skill so only the indirect report is a valid in-scope match.

Assert returned chain is:

```text
second upper → direct upper → actor → intermediate manager → matching report
```

Assert non-matching sibling and out-of-scope match are absent.

Name: `yoneticiFiltreliGorunumdeUstZincirAktorVeYalnizEslesenAstBaglantisiniGorur`.

- [ ] **Step 2: Run RED**

```powershell
.\mvnw.cmd "-Dtest=YoneticiHiyerarsisiControllerIntegrationTest#yoneticiFiltreliGorunumdeUstZincirAktorVeYalnizEslesenAstBaglantisiniGorur" "-Djacoco.skip=true" test
```

Expected: current manager endpoint returns all actor descendants because filter pruning is not implemented.

- [ ] **Step 3: Implement in-scope intersection and connector closure**

Use these sets:

```java
Set<String> astIds = aktifAstIds(aktorId, dogrudanBaglilar);
Set<String> eslesenAstIds = new HashSet<>(
        personelRepository.findAktifHiyerarsiEslesenPersonelIds(
                filtreler.arama(),
                filtreler.departmanId(),
                filtreler.yetenekId()
        )
);
eslesenAstIds.retainAll(astIds);
```

For every matching descendant, walk managers only until actor and retain each connector. Always retain actor and at most two upper managers. Never retain an out-of-scope match or upper-manager sibling branch.

- [ ] **Step 4: Add no-match, validation and cycle tests**

Add tests proving:

- no matching descendant still returns up to two uppers + actor with empty children,
- missing positive IDs produce the same safe no-match tree,
- zero/negative IDs return `400`, text IDs return controlled `400`,
- blank search equals unfiltered manager tree,
- manager's direct real manager is the only `yoneticinMi=true`, or all false when no active manager,
- a legacy cycle in actor's upper/reachable descendant scope returns `409`,
- an unrelated branch is never serialized or exposed.

- [ ] **Step 5: Run all hierarchy tests**

```powershell
.\mvnw.cmd "-Dtest=YoneticiHiyerarsisiControllerIntegrationTest" "-Djacoco.skip=true" test
```

Expected: all tests pass with zero failures/errors.

---

### Task 6: Relevant regressions and full verification

**Files:**

- Test only: all files changed in Tasks 1-5.
- No product Markdown changes in this task.

**Interfaces:**

- Verifies the completed HTTP contracts and protects existing directory/security behavior.

- [ ] **Step 1: Run focused hierarchy + directory + security regression**

```powershell
.\mvnw.cmd "-Dtest=YoneticiHiyerarsisiControllerIntegrationTest,EkipDiziniControllerIntegrationTest,SecurityHttpIntegrationTest" "-Djacoco.skip=true" test
```

Expected: zero failures/errors; personal chain remains PERSONEL/YONETICI `200` and HR/ADMIN `403`.

- [ ] **Step 2: Run broader related regression**

```powershell
.\mvnw.cmd "-Dtest=YoneticiHiyerarsisiControllerIntegrationTest,EkipDiziniControllerIntegrationTest,HrPersonelControllerIntegrationTest,PersonelYonetimControllerIntegrationTest,SecurityHttpIntegrationTest" "-Djacoco.skip=true" test
```

Expected: zero failures/errors; manager assignment/cycle, person management and directory visibility remain intact.

- [ ] **Step 3: Run the full backend suite with coverage**

```powershell
.\mvnw.cmd test
```

Expected: all backend tests and JaCoCo checks pass. If Docker/Testcontainers is unavailable, report that operational blocker and preserve the focused GREEN evidence.

- [ ] **Step 4: Perform final safety review**

Verify from serialized HTTP responses that:

- `yoneticinMi` exists on every hierarchy node and is true at most once,
- no personel/manager ID, username, email, phone, hash, token or audit data is exposed,
- manager endpoint never includes siblings, other roots or out-of-scope matches,
- GET requests create no audit or persistence mutation,
- no frontend or unrelated backend file changed.

- [ ] **Step 5: Request documentation approval**

After implementation and tests are complete, ask the user before changing `API_SPEC.md`, `SECURITY.md`, `PROGRESS.md` or `ROADMAP.md`. Do not silently update them.
