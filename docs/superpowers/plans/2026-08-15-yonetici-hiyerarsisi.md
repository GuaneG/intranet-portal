# Yönetici Hiyerarşisi Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Mevcut yönetici ilişkisini güvenli tam forest ve iki seviyelik kişisel üst-yönetici zinciri olarak sunmak, döngüleri engellemek/tespit etmek.

**Architecture:** Mevcut paginated ekip dizini değişmez. Yeni DTO’lar ve `HiyerarsiService`, HR/Admin full-tree ile PERSONEL/YONETICI chain endpoint’lerini ayırır. Full-tree tek toplu aktif-personel sorgusuyla kurulur; yazma ve okuma traversal’ları cycle-safe olur.

**Tech Stack:** Java 21, Spring Boot 3.5, Spring Data JPA, MySQL 8 Testcontainers, JUnit 5, MockMvc.

## Global Constraints

- Unvan/CEO modeli eklenmez; mevcut `Personel.yonetici` ve aynı-departman kuralı korunur.
- `/api/ekip-dizini` sözleşmesi değişmez.
- Full tree yalnız HR/Admin; chain yalnız PERSONEL/YONETICI.
- Yalnız güvenli görüntüleme alanları; ID/account/credential yok.
- Legacy cycle kontrollü `409`, yeni cycle assignment `400`.
- Git yok; frontend kapsam dışı.

---

### Task 1: Tam organizasyon forest’ı

**Files:**
- Create: `backend/src/main/java/com/jforce/backend/model/dto/response/HiyerarsiDugumResponse.java`
- Create: `backend/src/main/java/com/jforce/backend/model/dto/response/HiyerarsiResponse.java`
- Create: `backend/src/main/java/com/jforce/backend/service/HiyerarsiService.java`
- Create: `backend/src/test/java/com/jforce/backend/HiyerarsiControllerIntegrationTest.java`
- Modify: `EkipDiziniController.java`, `PersonelRepository.java`

**Interfaces:**

```java
record HiyerarsiDugumResponse(
        String ad, String soyad, String departman,
        String rol, String profilFoto,
        List<HiyerarsiDugumResponse> dogrudanBaglilar) {}
record HiyerarsiResponse(List<HiyerarsiDugumResponse> kokler) {}
```

- [ ] **Step 1:** HR’nin çok köklü nested aktif forest’ı gördüğü RED testi yaz; pasifleri ve hassas alanları dışla.
- [ ] **Step 2:** Eksik endpoint RED’ini çalıştır.

```powershell
.\mvnw.cmd "-Dtest=HiyerarsiControllerIntegrationTest#hrTumAktifOrganizasyonForestiniGorur" "-Djacoco.skip=true" test
```

- [ ] **Step 3:** `findAllAktifHiyerarsi` query’sini ekle; departman/rol/yönetici fetch et.
- [ ] **Step 4:** `@Transactional(readOnly=true)` ve `@PreAuthorize("hasAnyRole('HR','ADMIN')")` full-tree service metodunu yaz. Internal ID map ile parent-child bağla; manager map’te yoksa kök yap; DTO’ya ID koyma.
- [ ] **Step 5:** Controller `GET /hiyerarsi` mapping ve stable recursive sorting’i ekleyip GREEN yap.

---

### Task 2: İki seviyelik kişisel zincir

**Files:**
- Create: `UstYoneticiResponse.java`, `YoneticiZinciriResponse.java`
- Modify: `HiyerarsiService.java`, `EkipDiziniController.java`, `HiyerarsiControllerIntegrationTest.java`

**Interfaces:**

```java
record UstYoneticiResponse(
        int seviye, String ad, String soyad,
        String departman, String rol, String profilFoto) {}
record YoneticiZinciriResponse(List<UstYoneticiResponse> yoneticiler) {}
```

- [ ] **Step 1:** PERSONEL ve YONETICI için 0/1/2 üst testlerini yaz; üçüncü üst, çalışanlar ve başka ekipler response’a girmesin.
- [ ] **Step 2:** `@PreAuthorize("hasAnyRole('PERSONEL','YONETICI')")` chain service ve `GET /yonetici-zincirim` mapping’ini ekle.
- [ ] **Step 3:** Principal aktif/missing kontrolünü DB’den yap; pasif/missing üstte zinciri doğal sonlandır.
- [ ] **Step 4:** Hedefli chain testlerini GREEN yap.

---

### Task 3: Cycle-safe okuma ve yazma

**Files:**
- Modify: `HiyerarsiService.java`
- Modify: `backend/src/main/java/com/jforce/backend/service/PersonelService.java`
- Modify: `HiyerarsiControllerIntegrationTest.java`, `HrPersonelControllerIntegrationTest.java`

- [ ] **Step 1:** DB’ye doğrudan iki kişilik ve uzun cycle kuran full-tree/chain RED testlerini yaz; exact `409 Yönetici hiyerarşisinde tutarsızlık tespit edildi.` ve kısmi body olmamasını bekle.
- [ ] **Step 2:** Full-tree DFS için `VISITING/VISITED`, chain traversal için visited-ID set’i ekle.
- [ ] **Step 3:** A→B varken B→A ve A→B→C varken C→A update RED testlerini yaz; `400`, ilişki/audit değişmemeli.
- [ ] **Step 4:** Mevcut `yoneticiDogrula` içinde aday yöneticiden yukarı traversal ekle; hedefe veya tekrar eden ID’ye ulaşınca `GecersizIstekException` üret.
- [ ] **Step 5:** Cycle suite’ini GREEN yap.

---

### Task 4: Rol, aktiflik, güvenli DTO ve regresyon

- [ ] **Step 1:** Full-tree HR/Admin 200; PERSONEL/YONETICI 403. Chain PERSONEL/YONETICI 200; HR/Admin 403. Her ikisinde no-token 401, missing principal 404, pasif principal 403.
- [ ] **Step 2:** Tam ağaçta HR’ın Admin düğümünü görebildiğini; mevcut `/api/ekip-dizini` HR görünürlüğünün değişmediğini test et.
- [ ] **Step 3:** Response’larda personelId/kullanıcı adı/e-posta/sicil/hash/token/audit olmadığını ve okumaların audit üretmediğini doğrula.
- [ ] **Step 4:** Odaklı regresyonu çalıştır.

```powershell
.\mvnw.cmd "-Dtest=HiyerarsiControllerIntegrationTest,EkipDiziniControllerIntegrationTest,DashboardControllerIntegrationTest,HrPersonelControllerIntegrationTest,PersonelYonetimControllerIntegrationTest,SecurityHttpIntegrationTest" "-Djacoco.skip=true" test
```

- [ ] **Step 5:** Bağımsız review’da veri sızıntısı, cycle termination, stable ordering ve mevcut ekip dizini regresyonunu incele.

---

### Task 5: Belgeler ve full doğrulama

- [ ] **Step 1:** Kullanıcıdan Markdown yazma onayı al; `API_SPEC.md`, `SECURITY.md`, `ROADMAP.md`, `PROGRESS.md` güncelle.
- [ ] **Step 2:** UTF-8/CRLF/U+FFFD/mojibake/duplicate kontrolleri yap.
- [ ] **Step 3:** Kullanıcı ortamında `.\mvnw.cmd test` ile tam backend ve JaCoCo doğrulamasını al.

