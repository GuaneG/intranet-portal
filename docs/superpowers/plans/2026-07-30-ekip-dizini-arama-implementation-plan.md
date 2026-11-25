# Ekip Dizini Arama Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Mevcut `GET /api/ekip-dizini` endpoint’ine ad, soyad ve tam ad üzerinde çalışan, diğer filtreler ve pagination ile uyumlu opsiyonel `arama` parametresi eklemek.

**Architecture:** Mevcut controller, service, repository ve DTO yapıları korunur. Controller yalnız parametreyi service’e aktarır; service trim/boş normalizasyonunu yapar; JPQL ana ve count sorguları aynı arama koşulunu uygular.

**Tech Stack:** Java 21, Spring Boot, Spring MVC, Spring Data JPA, JUnit 5, MockMvc, Testcontainers, MySQL 8.

## Global Constraints

- Backend Java ve test kodlarını kullanıcı kopyalayıp uygular; agent bu dosyaları doğrudan değiştirmez.
- Frontend dosyalarına dokunulmaz.
- Git komutu çalıştırılmaz ve plan commit adımı içermez.
- `EkipDiziniResponse` ve `SayfaliResponse` değiştirilmez.
- Mevcut `page`, `size`, `departmanId`, `yetenekId`, aktif personel ve sabit sıralama davranışları korunur.
- Arama baş ve son boşlukları trim edildikten sonra boşsa filtre uygulanmaz.
- Arama ad, soyad ve `ad + " " + soyad` üzerinde case-insensitive çalışır.
- Ana sorgu ve count sorgusu aynı arama koşulunu içerir.
- Arama, departman ve yetenek filtreleri `AND` ile birleşir.

---

## Dosya haritası

Değişecek production dosyaları:

- `backend/src/main/java/com/jforce/backend/controller/EkipDiziniController.java`
- `backend/src/main/java/com/jforce/backend/service/EkipDiziniService.java`
- `backend/src/main/java/com/jforce/backend/repository/PersonelRepository.java`

Değişecek test dosyası:

- `backend/src/test/java/com/jforce/backend/EkipDiziniControllerIntegrationTest.java`

Doğrulama sonrası değişecek belgeler:

- `API_SPEC.md`
- `ROADMAP.md`
- `PROGRESS.md`

---

### Task 1: Arama sözleşmesini failing integration testlerle sabitle

**Files:**

- Test: `backend/src/test/java/com/jforce/backend/EkipDiziniControllerIntegrationTest.java`

**Interfaces:**

- Consumes: Mevcut `GET /api/ekip-dizini`.
- Produces: Opsiyonel `arama` query parametresinin gözlenebilir HTTP sözleşmesi.

- [ ] **Step 1: Ad, soyad ve tam ad testini ekle**

Test sınıfına aşağıdaki parameterized testi yardımcı metotlardan önce ekle:

```java
@ParameterizedTest
@CsvSource({
        "'  AYŞE  ', Ayşe",
        "yılmaz, Ayşe",
        "'AYŞE yılMAZ', Ayşe"
})
void aramaAdSoyadVeTamAdUzerindeCaseInsensitiveCalisir(
        String arama,
        String beklenenAd
) throws Exception {
    personelKaydet(
            "ayse.yilmaz",
            "Ayşe",
            "Yılmaz",
            personelRolu,
            bilgiTeknolojileri,
            true
    );
    personelKaydet(
            "deniz.kaya",
            "Deniz",
            "Kaya",
            personelRolu,
            bilgiTeknolojileri,
            true
    );

    mockMvc.perform(get("/api/ekip-dizini")
                    .param("arama", arama)
                    .header(
                            HttpHeaders.AUTHORIZATION,
                            "Bearer " + token(
                                    aktor,
                                    RolAdi.PERSONEL
                            )
                    ))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.toplamKayit").value(1))
            .andExpect(jsonPath("$.toplamSayfa").value(1))
            .andExpect(jsonPath("$.icerik.length()").value(1))
            .andExpect(
                    jsonPath("$.icerik[0].ad")
                            .value(beklenenAd)
            );
}
```

- [ ] **Step 2: Boş arama testini ekle**

```java
@ParameterizedTest
@ValueSource(strings = {"", "   "})
void bosAramaFiltreUygulanmamisGibiDavranir(String arama)
        throws Exception {
    personelKaydet(
            "ayse.yilmaz",
            "Ayşe",
            "Yılmaz",
            personelRolu,
            bilgiTeknolojileri,
            true
    );

    mockMvc.perform(get("/api/ekip-dizini")
                    .param("arama", arama)
                    .header(
                            HttpHeaders.AUTHORIZATION,
                            "Bearer " + token(
                                    aktor,
                                    RolAdi.PERSONEL
                            )
                    ))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.toplamKayit").value(2))
            .andExpect(jsonPath("$.icerik.length()").value(2));
}
```

- [ ] **Step 3: Arama, departman ve yetenek AND testini ekle**

```java
@Test
void aramaDepartmanVeYetenekFiltreleriAndIleBirlesir()
        throws Exception {
    Departman insanKaynaklari =
            departmanKaydet("İnsan Kaynakları");
    Integer javaId = yetenekOlustur("Java");

    Personel eslesen = personelKaydet(
            "ayse.java",
            "Ayşe",
            "Java",
            personelRolu,
            bilgiTeknolojileri,
            true
    );
    Personel yanlisDepartman = personelKaydet(
            "ayse.ik",
            "Ayşe",
            "İK",
            personelRolu,
            insanKaynaklari,
            true
    );
    personelKaydet(
            "ayse.yeteneksiz",
            "Ayşe",
            "Yeteneksiz",
            personelRolu,
            bilgiTeknolojileri,
            true
    );

    personelYetenekOlustur(eslesen, javaId);
    personelYetenekOlustur(yanlisDepartman, javaId);

    mockMvc.perform(get("/api/ekip-dizini")
                    .param("arama", "  AYŞE  ")
                    .param(
                            "departmanId",
                            bilgiTeknolojileri
                                    .getDepartmanId()
                                    .toString()
                    )
                    .param("yetenekId", javaId.toString())
                    .header(
                            HttpHeaders.AUTHORIZATION,
                            "Bearer " + token(
                                    aktor,
                                    RolAdi.PERSONEL
                            )
                    ))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.toplamKayit").value(1))
            .andExpect(jsonPath("$.toplamSayfa").value(1))
            .andExpect(jsonPath("$.icerik.length()").value(1))
            .andExpect(
                    jsonPath("$.icerik[0].ad")
                            .value("Ayşe")
            )
            .andExpect(
                    jsonPath("$.icerik[0].soyad")
                            .value("Java")
            );
}
```


- [ ] **Step 4: Count ve pagination tutarlılık testini ekle**

```java
@Test
void aramaCountSorgusuVePaginationIleTutarlidir()
        throws Exception {
    personelKaydet(
            "ayse.a",
            "Ayşe",
            "A",
            personelRolu,
            bilgiTeknolojileri,
            true
    );
    personelKaydet(
            "ayse.b",
            "Ayşe",
            "B",
            personelRolu,
            bilgiTeknolojileri,
            true
    );
    personelKaydet(
            "deniz.c",
            "Deniz",
            "C",
            personelRolu,
            bilgiTeknolojileri,
            true
    );

    mockMvc.perform(get("/api/ekip-dizini")
                    .param("arama", "ayşe")
                    .param("page", "1")
                    .param("size", "1")
                    .header(
                            HttpHeaders.AUTHORIZATION,
                            "Bearer " + token(
                                    aktor,
                                    RolAdi.PERSONEL
                            )
                    ))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.sayfa").value(1))
            .andExpect(jsonPath("$.sayfaBoyutu").value(1))
            .andExpect(jsonPath("$.toplamKayit").value(2))
            .andExpect(jsonPath("$.toplamSayfa").value(2))
            .andExpect(jsonPath("$.icerik.length()").value(1))
            .andExpect(
                    jsonPath("$.icerik[0].soyad")
                            .value("B")
            );
}
```

- [ ] **Step 5: RED sonucunu doğrula**

```powershell
.\mvnw.cmd -Dtest=EkipDiziniControllerIntegrationTest test
```

Beklenen: Mevcut 25 invocation’a 7 invocation eklenir ve toplam 32 test
çalışır. Endpoint `arama` parametresini henüz kullanmadığı için en az ad,
soyad, tam ad ve filtre birleşimi senaryoları failure verir; compilation error
oluşmaz.

---

### Task 2: Controller, service ve repository arama implementasyonu

**Files:**

- Modify: `backend/src/main/java/com/jforce/backend/controller/EkipDiziniController.java`
- Modify: `backend/src/main/java/com/jforce/backend/service/EkipDiziniService.java`
- Modify: `backend/src/main/java/com/jforce/backend/repository/PersonelRepository.java`

**Interfaces:**

- Produces: `EkipDiziniService#ekipDizininiGetir(String, int, int, Integer, Integer, String)`.

- [ ] **Step 1: Controller parametresini ekle**

Controller metoduna `Authentication` parametresinden önce ekle:

```java
@RequestParam(required = false)
String arama,
```

Service çağrısını şu hale getir:

```java
SayfaliResponse<EkipDiziniResponse> response =
        ekipDiziniService.ekipDizininiGetir(
                authentication.getName(),
                page,
                size,
                departmanId,
                yetenekId,
                arama
        );
```

- [ ] **Step 2: Service imzasını ve normalizasyonu ekle**

Metot imzası:

```java
public SayfaliResponse<EkipDiziniResponse> ekipDizininiGetir(
        String aktorId,
        int page,
        int size,
        Integer departmanId,
        Integer yetenekId,
        String arama
)
```

Repository çağrısından önce:

```java
String normalizeArama = aramayiNormalizeEt(arama);
```

Repository çağrısını:

```java
Page<Personel> personelSayfasi =
        personelRepository.findAktifEkipDizini(
                departmanId,
                yetenekId,
                normalizeArama,
                PageRequest.of(page, size)
        );
```

Yardımcı metot:

```java
private String aramayiNormalizeEt(String arama) {
    if (arama == null) {
        return null;
    }

    String normalizeArama = arama.trim();
    return normalizeArama.isEmpty()
            ? null
            : normalizeArama;
}
```

- [ ] **Step 3: Ana sorguya arama koşulunu ekle**

Mevcut yetenek filtresinden sonra, `ORDER BY` öncesine ekle:

```java
AND (
      :arama IS NULL
      OR LOWER(p.adi) LIKE
         CONCAT('%', CONCAT(LOWER(:arama), '%'))
      OR LOWER(p.soyadi) LIKE
         CONCAT('%', CONCAT(LOWER(:arama), '%'))
      OR LOWER(
            CONCAT(
                p.adi,
                CONCAT(' ', p.soyadi)
            )
         ) LIKE
         CONCAT('%', CONCAT(LOWER(:arama), '%'))
)
```

- [ ] **Step 4: Count sorgusuna aynı koşulu ekle**

Count sorgusundaki yetenek filtresinden sonra aynı bloğu eksiksiz ekle:

```java
AND (
      :arama IS NULL
      OR LOWER(p.adi) LIKE
         CONCAT('%', CONCAT(LOWER(:arama), '%'))
      OR LOWER(p.soyadi) LIKE
         CONCAT('%', CONCAT(LOWER(:arama), '%'))
      OR LOWER(
            CONCAT(
                p.adi,
                CONCAT(' ', p.soyadi)
            )
         ) LIKE
         CONCAT('%', CONCAT(LOWER(:arama), '%'))
)
```

- [ ] **Step 5: Repository imzasını güncelle**

```java
Page<Personel> findAktifEkipDizini(
        @Param("departmanId") Integer departmanId,
        @Param("yetenekId") Integer yetenekId,
        @Param("arama") String arama,
        Pageable pageable
);
```

- [ ] **Step 6: GREEN sonucunu doğrula**

```powershell
.\mvnw.cmd -Dtest=EkipDiziniControllerIntegrationTest test
```

Beklenen: 32 test invocation, 0 failure, 0 error.

---

### Task 3: Tam regresyon ve belgeler

**Files:**

- Verify: bütün backend testleri
- Modify after verified output: `API_SPEC.md`
- Modify after verified output: `ROADMAP.md`
- Modify after verified output: `PROGRESS.md`

- [ ] **Step 1: Tam backend suite’i çalıştır**

```powershell
.\mvnw.cmd test
```

Mevcut doğrulanmış taban 196 testtir. Bu plandaki 7 yeni invocation ile,
başka eşzamanlı test değişikliği yoksa 203 test beklenir. Kabul ölçütü gerçek
çıktıda `0 failure`, `0 error` ve `BUILD SUCCESS` olmasıdır.

- [ ] **Step 2: API sözleşmesini güncelle**

`API_SPEC.md` ekip dizini bölümüne:

```markdown
- Opsiyonel `arama` parametresi ad, soyad ve `ad + soyad` üzerinde case-insensitive çalışır. Baş ve son boşluklar temizlenir; boş değer filtre uygulanmamış gibi davranır.
- `arama`, `departmanId` ve `yetenekId` birlikte gönderildiğinde `AND` uygulanır. Arama koşulu ana ve count sorgularında aynıdır; pagination ve sabit sıralama korunur.
```

- [ ] **Step 3: Roadmap ve progress’i gerçek test sayılarıyla güncelle**

`ROADMAP.md`:

```markdown
- [x] Ekip dizininde ad, soyad ve tam ad araması
    - Opsiyonel `arama`; departman ve yetenek filtreleriyle `AND` uygulanır, pagination ve sabit sıralama korunur.
```

`PROGRESS.md` için yalnız gerçek çıktıyla:

```markdown
- Added optional `arama` filtering to `GET /api/ekip-dizini` across name, surname and full name with trim and case-insensitive matching.
- Kept blank search values filter-free and combined search with department and skill filters using `AND` in both the page and count queries.
- Preserved the existing stable ordering, pagination DTO and response fields.
- Focused verification reported by the developer: `EkipDiziniControllerIntegrationTest` ran 32 tests with 0 failures, 0 errors and 0 skipped.
- Full backend verification reported by the developer: 203 tests, 0 failures, 0 errors and 0 skipped; build successful.
```

203 sayısı gerçek tam-suite çıktısı farklıysa belgeye yazılmaz; gerçek sayı
kullanılır.

---

## Son kabul ölçütleri

- Ad, soyad ve tam ad araması case-insensitive çalışır.
- Baş/son boşluklar temizlenir; boş arama filtresiz davranır.
- Arama, departman ve yetenek filtreleri `AND` ile birleşir.
- Ana ve count sorguları aynı arama koşuluna sahiptir.
- Pagination, sıralama ve DTO sözleşmesi değişmez.
- Odaklı ve tam backend testleri başarılıdır.
