# Departman ve Yetenek Silme Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Kullanılmayan departman/yetenek kayıtlarını güvenle silmek ve ilişkili kayıtları kontrollü `409` ile korumak.

**Architecture:** Mevcut controller/service’ler DELETE ile genişletilir. Repository parent satırını pessimistic lock ile yükler; ilişki precheck’i, delete+flush ve audit aynı transaction’da çalışır; FK yarışı güvenli `409` fallback’ine çevrilir.

**Tech Stack:** Java 21, Spring Boot 3.5, Spring Data JPA locks, MySQL/InnoDB, Testcontainers, JUnit 5, MockMvc.

## Global Constraints

- DELETE yetkisi mevcut create/update matrisinden genişletilmez: aktif HR/Admin.
- Herhangi bir personele bağlı departman ve herhangi bir join’e bağlı yetenek silinmez.
- Cascade veya sessiz yeniden atama yoktur.
- Başarı `204`, kullanım `409`, missing `404`, invalid ID `400`.
- Git yok; frontend kapsam dışı.

---

### Task 1: Departman silme happy-path

**Files:**
- Modify: `backend/src/main/java/com/jforce/backend/controller/DepartmanYonetimController.java`
- Modify: `backend/src/main/java/com/jforce/backend/service/DepartmanYonetimService.java`
- Modify: `backend/src/main/java/com/jforce/backend/repository/DepartmanRepository.java`
- Modify: `backend/src/test/java/com/jforce/backend/DepartmanYonetimControllerIntegrationTest.java`

**Interfaces:** `void departmanSil(String aktorId, Integer departmanId)`.

- [ ] **Step 1:** Aktif HR’nin ilişkisiz departmanı silip `204` aldığı RED testini yaz.
- [ ] **Step 2:** Eksik DELETE mapping nedeniyle beklenen RED’i çalıştır.

```powershell
.\mvnw.cmd "-Dtest=DepartmanYonetimControllerIntegrationTest#hrIliskisizDepartmaniSiler" "-Djacoco.skip=true" test
```

- [ ] **Step 3:** Controller DELETE mapping ve locked lookup ekle.

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT d FROM Departman d WHERE d.departmanId = :departmanId")
Optional<Departman> findByIdForUpdate(@Param("departmanId") Integer departmanId);
```

Service `@Transactional`, `@PreAuthorize("hasAnyRole('HR','ADMIN')")`, aktif aktör ve pozitif ID doğrulamasından sonra locked entity’yi silip `flush` etsin.

- [ ] **Step 4:** Happy-path GREEN’i doğrula.

---

### Task 2: Departman kullanım ve yarış güvenliği

**Files:**
- Modify: `PersonelRepository.java`, `DepartmanYonetimService.java`
- Modify: `DepartmanYonetimControllerIntegrationTest.java`, `DepartmanYonetimServiceTest.java`
- Modify: `AuditEylem.java`

**Interfaces:** `boolean existsByDepartman_DepartmanId(Integer departmanId)`, enum `DEPARTMAN_SILINDI`.

- [ ] **Step 1:** Aktif ve pasif personel ilişkisi için ayrı `409` testleri yaz; personel/departman/audit korunmalı.
- [ ] **Step 2:** İlişki precheck’i ekle ve exact güvenli mesajı kullan: `Departman kullanımda olduğu için silinemez.`
- [ ] **Step 3:** `deleteAndFlush`/`flush` sırasında `DataIntegrityViolationException` için service unit RED testi yaz; aynı `409` exception’a çevir ve audit çağırma.
- [ ] **Step 4:** Başarılı silmede `DEPARTMAN_SILINDI`, actor HR/Admin, target null ve güvenli ad detayını aynı transaction’da yaz.
- [ ] **Step 5:** Dış transaction rollback testinde departman ve auditin geri geldiğini doğrula.

---

### Task 3: Yetenek silme

**Files:**
- Modify: `YetenekYonetimController.java`, `YetenekYonetimService.java`
- Modify: `YetenekRepository.java`, `PersonelYetenekRepository.java`
- Modify: `YetenekYonetimControllerIntegrationTest.java`, `YetenekYonetimServiceTest.java`
- Modify: `AuditEylem.java`

**Interfaces:** `void yetenekSil(String aktorId, Integer yetenekId)`, `boolean existsByYetenek_YetenekId(Integer yetenekId)`, enum `YETENEK_SILINDI`.

- [ ] **Step 1:** Aktif Admin’in ilişkisiz yeteneği `204` ile sildiği RED testi yaz.
- [ ] **Step 2:** Locked lookup, delete+flush ve controller mapping’i ekleyip GREEN yap.
- [ ] **Step 3:** Aktif/pasif personel join ilişkilerinde `409 Yetenek kullanımda olduğu için silinemez.` ve no-cascade testlerini ekle.
- [ ] **Step 4:** FK yarışını kontrollü `409` yapan unit testi ve production fallback’ini ekle.
- [ ] **Step 5:** `YETENEK_SILINDI` audit ve dış rollback testlerini GREEN yap.

---

### Task 4: Ortak rol/hata matrisi ve regresyon

- [ ] **Step 1:** Her iki DELETE için HR/Admin başarı; PERSONEL/YONETICI `403`; pasif aktör `403`; missing principal `404`; no-token `401` testlerini ekle.
- [ ] **Step 2:** Pozitif olmayan ID `400`, missing katalog `404`, başarısızlıklarda audit/mutation yok testlerini ekle.
- [ ] **Step 3:** Odaklı suite’i çalıştır.

```powershell
.\mvnw.cmd "-Dtest=DepartmanYonetimControllerIntegrationTest,DepartmanYonetimServiceTest,YetenekYonetimControllerIntegrationTest,YetenekYonetimServiceTest,LookupControllerIntegrationTest" "-Djacoco.skip=true" test
```

- [ ] **Step 4:** Bağımsız review’da lock sırası, FK fallback, no-cascade ve audit transaction’ını incele.

---

### Task 5: Belgeler ve full doğrulama

- [ ] **Step 1:** Kullanıcıdan Markdown yazma onayı al; `API_SPEC.md`, `SECURITY.md`, `ROADMAP.md`, `PROGRESS.md` güncelle.
- [ ] **Step 2:** UTF-8/CRLF/U+FFFD/mojibake/duplicate kontrolleri yap.
- [ ] **Step 3:** Kullanıcı ortamında `.\mvnw.cmd test` ile tam backend ve JaCoCo doğrulamasını al.

