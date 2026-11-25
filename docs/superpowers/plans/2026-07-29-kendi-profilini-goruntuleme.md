# Kendi Profilini Görüntüleme Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Authenticated kullanıcının mevcut `ProfilBilgileriResponse` ile temel profilini, alfabetik yeteneklerini ve role uygun canlı kalan izin bilgisini görüntülemesini sağlamak.

**Uygulama durumu:** Üretim akışı tamamlandı; 8 odaklı HTTP/MySQL entegrasyon testi geçti. Ayrı service unit testi ve tam backend paketinin bu turdaki yeniden çalıştırması yapılmadı.

**Architecture:** `GET /api/profil` aktör kimliğini JWT principal’dan alır ve yeni `ProfilService` katmanına aktarır. Service personel, personel-yetenek ve izin repository’lerinden okuduğu verileri tek response’a eşler; bütün akış read-only ve audit yan etkisizdir.

**Tech Stack:** Java 21, Spring Boot, Spring Security Method Security, Spring Data JPA/Hibernate, MySQL 8, JUnit 5, Mockito, MockMvc, Testcontainers

## Global Constraints

- Kullanıcının açık tercihiyle bu feature’da TDD uygulanmaz; production kodu önce, testler toplu olarak sonra yazılır.
- Backend kodunu kullanıcı yazar; agent her anlamlı grubu inceler.
- Agent git komutu çalıştırmaz.
- Endpoint `GET /api/profil`; request body ve personel ID parametresi yoktur.
- Mevcut `ProfilBilgileriResponse` kullanılır; yeni response DTO oluşturulmaz.
- `mezuniyetYili` response’a eklenmez.
- `PERSONEL` ve `HR` için kalan izin canlı hesaplanır; `YONETICI` ve `ADMIN` için `null` döner.
- Profil görüntüleme audit üretmez ve veri değiştirmez.

---

### Task 1: İzin türü ve durum modelini tip güvenli yap

**Files:**
- Create: `backend/src/main/java/com/jforce/backend/model/enums/TalepDurum.java`
- Modify: `backend/src/main/java/com/jforce/backend/model/entity/DurumTuru.java`
- Modify: `backend/src/main/java/com/jforce/backend/model/entity/IzinTuru.java`

**Interfaces:**
- Produces: `TalepDurum`, `DurumTuru.getDurumKodu()` ve `IzinTuru.isYillikHaktanDuserMi()`

- [x] **Step 1: TalepDurum enum’unu oluştur**

```java
package com.jforce.backend.model.enums;

public enum TalepDurum {
    ONAY_BEKLIYOR("Onay bekliyor"),
    ONAYLANDI("Onaylandı"),
    REDDEDILDI("Reddedildi"),
    IPTAL_EDILDI("İptal edildi");

    private final String gorunenAd;

    TalepDurum(String gorunenAd) {
        this.gorunenAd = gorunenAd;
    }

    public String getGorunenAd() {
        return gorunenAd;
    }
}
```

- [x] **Step 2: DurumTuru alanını enum koduna dönüştür**

`String durumAdi` yerine:

```java
@Enumerated(EnumType.STRING)
@Column(name = "durum_adi", length = 30, unique = true, nullable = false)
private TalepDurum durumKodu;

public TalepDurum getDurumKodu() {
    return durumKodu;
}

public void setDurumKodu(TalepDurum durumKodu) {
    this.durumKodu = durumKodu;
}
```

Eski `getDurumAdi` ve `setDurumAdi` metotlarını kaldır; `TalepDurum` importunu ekle.

- [x] **Step 3: IzinTuru’ne bakiye davranışı ekle**

```java
@Column(name = "yillik_haktan_duser_mi", nullable = false)
@ColumnDefault("false")
private boolean yillikHaktanDuserMi = false;

public boolean isYillikHaktanDuserMi() {
    return yillikHaktanDuserMi;
}

public void setYillikHaktanDuserMi(boolean yillikHaktanDuserMi) {
    this.yillikHaktanDuserMi = yillikHaktanDuserMi;
}
```

`org.hibernate.annotations.ColumnDefault` importunu ekle.

- [x] **Step 4: Lookup tablolarının boş olduğunu doğrula; veri migration’ına gerek olmadığını kaydet**

Uygulama açılmadan önce mevcut veritabanında bulunan satırlar için bir defa çalıştır:

```sql
UPDATE durum_turu SET durum_adi = 'ONAY_BEKLIYOR' WHERE durum_adi = 'Onay bekliyor';
UPDATE durum_turu SET durum_adi = 'ONAYLANDI' WHERE durum_adi = 'Onaylandı';
UPDATE durum_turu SET durum_adi = 'REDDEDILDI' WHERE durum_adi = 'Reddedildi';
UPDATE durum_turu SET durum_adi = 'IPTAL_EDILDI' WHERE durum_adi = 'İptal edildi';
UPDATE izin_turu SET yillik_haktan_duser_mi = TRUE WHERE izin_turu_adi = 'Yıllık izin';
```

Tablolar boşsa update’ler sıfır satır etkiler ve hata oluşturmaz. Yeni lookup kayıtları enum kodlarıyla ve doğru boolean değerle oluşturulur.

- [x] **Step 5: Derlemeyi kontrol et**

```powershell
cd backend
.\mvnw.cmd -DskipTests test
```

Beklenen: `BUILD SUCCESS`.

---

### Task 2: Profil okuma repository’leri

**Files:**
- Create: `backend/src/main/java/com/jforce/backend/repository/PersonelYetenekRepository.java`
- Create: `backend/src/main/java/com/jforce/backend/repository/IzinBilgileriRepository.java`

**Interfaces:**
- Produces: `findAllByPersonelIdWithYetenek(String)` ve `onaylanmisIzinIsGunuToplami(String, TalepDurum)`

- [x] **Step 1: PersonelYetenekRepository oluştur**

```java
package com.jforce.backend.repository;

import com.jforce.backend.model.entity.PersonelYetenek;
import com.jforce.backend.model.entity.PersonelYetenekId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PersonelYetenekRepository
        extends JpaRepository<PersonelYetenek, PersonelYetenekId> {

    @Query("""
            SELECT py
            FROM PersonelYetenek py
            JOIN FETCH py.yetenek y
            WHERE py.personel.personelId = :personelId
            ORDER BY y.yetenekAdi ASC
            """)
    List<PersonelYetenek> findAllByPersonelIdWithYetenek(
            @Param("personelId") String personelId
    );
}
```

- [x] **Step 2: IzinBilgileriRepository oluştur**

```java
package com.jforce.backend.repository;

import com.jforce.backend.model.entity.IzinBilgileri;
import com.jforce.backend.model.enums.TalepDurum;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IzinBilgileriRepository
        extends JpaRepository<IzinBilgileri, String> {

    @Query("""
            SELECT COALESCE(SUM(i.isGunuSayisi), 0)
            FROM IzinBilgileri i
            WHERE i.personelId.personelId = :personelId
              AND i.izinTuruId.yillikHaktanDuserMi = true
              AND i.izinDurumId.durumKodu = :durumKodu
            """)
    Long onaylanmisIzinIsGunuToplami(
            @Param("personelId") String personelId,
            @Param("durumKodu") TalepDurum durumKodu
    );
}
```

- [x] **Step 3: Test derlemesi yap**

```powershell
cd backend
.\mvnw.cmd -DskipTests test
```

Beklenen: `BUILD SUCCESS`.

---

### Task 3: ProfilService

**Files:**
- Create: `backend/src/main/java/com/jforce/backend/service/ProfilService.java`

**Interfaces:**
- Consumes: Task 2 repository metotları ve mevcut `PersonelRepository.findById`
- Produces: `ProfilBilgileriResponse profilimiGetir(String personelId)`

- [x] **Step 1: ProfilService oluştur**

```java
package com.jforce.backend.service;

import com.jforce.backend.exception.KaynakBulunamadiException;
import com.jforce.backend.model.dto.response.LookUpResponse;
import com.jforce.backend.model.dto.response.ProfilBilgileriResponse;
import com.jforce.backend.model.entity.Personel;
import com.jforce.backend.model.enums.RolAdi;
import com.jforce.backend.model.enums.TalepDurum;
import com.jforce.backend.repository.IzinBilgileriRepository;
import com.jforce.backend.repository.PersonelRepository;
import com.jforce.backend.repository.PersonelYetenekRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ProfilService {

    private final PersonelRepository personelRepository;
    private final PersonelYetenekRepository personelYetenekRepository;
    private final IzinBilgileriRepository izinBilgileriRepository;

    public ProfilService(
            PersonelRepository personelRepository,
            PersonelYetenekRepository personelYetenekRepository,
            IzinBilgileriRepository izinBilgileriRepository
    ) {
        this.personelRepository = personelRepository;
        this.personelYetenekRepository = personelYetenekRepository;
        this.izinBilgileriRepository = izinBilgileriRepository;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("isAuthenticated()")
    public ProfilBilgileriResponse profilimiGetir(String personelId) {
        Personel personel = personelRepository.findById(personelId)
                .orElseThrow(() -> new KaynakBulunamadiException("Personel bulunamadı."));

        List<LookUpResponse> yetenekler = personelYetenekRepository
                .findAllByPersonelIdWithYetenek(personelId)
                .stream()
                .map(personelYetenek -> new LookUpResponse(
                        personelYetenek.getYetenek().getYetenekId(),
                        personelYetenek.getYetenek().getYetenekAdi()
                ))
                .toList();

        RolAdi rolAdi = personel.getRol().getRolAdi();
        Integer kalanIzin = null;

        if (rolAdi == RolAdi.PERSONEL || rolAdi == RolAdi.HR) {
            Long kullanilanIzin = izinBilgileriRepository
                    .onaylanmisIzinIsGunuToplami(
                            personelId,
                            TalepDurum.ONAYLANDI
                    );

            kalanIzin = personel.getYillikIzinHakki()
                    - Math.toIntExact(kullanilanIzin);
        }

        String yonetici = personel.getYonetici() == null
                ? null
                : personel.getYonetici().getAdi()
                        + " "
                        + personel.getYonetici().getSoyadi();

        return new ProfilBilgileriResponse(
                personel.getKullaniciAdi(),
                personel.getAdi(),
                personel.getSoyadi(),
                personel.getDogumTarihi(),
                personel.getePosta(),
                personel.getSicilNumarasi(),
                personel.getDepartman().getDepartmanAdi(),
                rolAdi.name(),
                yonetici,
                personel.getYillikIzinHakki(),
                personel.getProfilFoto(),
                kalanIzin,
                personel.getMezunOkul(),
                personel.getMezunBolum(),
                personel.getTelNo(),
                personel.getIsGirisTarihi(),
                yetenekler
        );
    }
}
```

- [x] **Step 2: Production derlemesini kontrol et**

```powershell
.\mvnw.cmd -DskipTests package
```

Beklenen: `BUILD SUCCESS`.

---

### Task 4: GET /api/profil controller akışı

**Files:**
- Modify: `backend/src/main/java/com/jforce/backend/controller/ProfilController.java`

**Interfaces:**
- Consumes: `ProfilService.profilimiGetir(String)`
- Produces: `GET /api/profil -> 200 ProfilBilgileriResponse`

- [x] **Step 1: Controller bağımlılığı ve endpoint’i ekle**

Yeni importlar:

```java
import com.jforce.backend.model.dto.response.ProfilBilgileriResponse;
import com.jforce.backend.service.ProfilService;
import org.springframework.web.bind.annotation.GetMapping;
```

Field ve constructor:

```java
private final ParolaService parolaService;
private final ProfilService profilService;

public ProfilController(
        ParolaService parolaService,
        ProfilService profilService
) {
    this.parolaService = parolaService;
    this.profilService = profilService;
}
```

Endpoint:

```java
@GetMapping
public ResponseEntity<ProfilBilgileriResponse> profilimiGetir(
        Authentication authentication
) {
    return ResponseEntity.ok(
            profilService.profilimiGetir(authentication.getName())
    );
}
```

- [x] **Step 2: Backend derlemesini kontrol et**

```powershell
.\mvnw.cmd -DskipTests test
```

Beklenen: `BUILD SUCCESS`.

---

### Task 5: Toplu test ve doğrulama

**Files:**
- Create: `backend/src/test/java/com/jforce/backend/ProfilServiceTest.java`
- Create: `backend/src/test/java/com/jforce/backend/ProfilControllerIntegrationTest.java`
- Modify after success: `API_SPEC.md`, `ROADMAP.md`, `PROGRESS.md`

**Interfaces:**
- Consumes: Tamamlanmış profil endpoint’i
- Produces: Mapping, sorgu, security ve MySQL davranışı kanıtı

- [-] **Step 1: Service unit testlerini ekle (ayrı sınıf yazılmadı; davranışlar gerçek MySQL entegrasyon testlerinde kapsandı)**

Aşağıdaki gerçek davranışları ayrı metotlarla doğrula:

```text
personelProfiliTumAlanlarlaDoner
hrIcinKalanIzinHesaplanir
adminIcinKalanIzinNullDonerVeIzinRepositoryCagrilmaz
yoneticiYoksaNullYetenekYoksaBosListeDoner
personelBulunamazsaKaynakBulunamadiExceptionAtar
```

Unit testte `Personel`, `Rol`, `Departman`, opsiyonel yönetici ve `PersonelYetenek` nesnelerini gerçek entity olarak kullan; yalnız repository’leri mockla. Kalan izin örneği `14 - 5 = 9` literal assertion’ıyla doğrulanır.

- [x] **Step 2: HTTP/MySQL integration testlerini ekle**

Aşağıdaki senaryoları `ProfilControllerIntegrationTest extends AbstractIntegrationTest` içinde doğrula:

```text
personelKendiProfiliniGercekVerilerleGorur -> 200
yalnizOnaylanmisYillikIzinKalanIzindenDuser -> 200 ve doğru kalanIzin
hrIcinKalanIzinHesaplanir -> 200
adminVeYoneticiIcinKalanIzinNullDoner -> 200
kimlikDogrulanmamissa401Doner -> 401
principalPersoneliBulunamazsa404Doner -> 404
```

İlk test response’ta yönetici ad-soyadı, sicil numarası, departman, rol, yeteneklerin alfabetik sırası ve boş olmayan profil alanlarını doğrular. İzin testi onaylı yıllık izni düşürür; bekleyen yıllık izin ve onaylı hastalık izninin sonucu değiştirmediğini kanıtlar.

- [x] **Step 3: Profil test grubunu çalıştır**

```powershell
.\mvnw.cmd -Dtest=ProfilServiceTest,ProfilControllerIntegrationTest test
```

Beklenen: failures/errors/skips sıfır.

- [ ] **Step 4: Tam backend testlerini çalıştır**

```powershell
.\mvnw.cmd test
```

Beklenen: `BUILD SUCCESS`; gerçek test sayısı Maven çıktısından alınır.

- [x] **Step 5: Doğrulanmış belgeleri güncelle**

- `API_SPEC.md`: `/api/profile/me` yerine uygulanmış `GET /api/profil` sözleşmesini, alanları ve rol bazlı kalan izin davranışını yaz.
- `ROADMAP.md`: kendi profilini görüntüleme maddesini tamamla; “Profil response’una mezuniyet yılını aktar” maddesini kaldır.
- `PROGRESS.md`: gerçek test sayısı ve uygulanan profil davranışıyla yeni ilerleme kaydı ekle.
- `SECURITY.md`: Authentication kimliğinin JWT principal’dan alınması zaten genel kuralla kapsandığından yeni güvenlik mimarisi oluşmadıkça zorunlu değişiklik yapma.
