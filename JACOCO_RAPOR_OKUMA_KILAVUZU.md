# JaCoCo Test Coverage Raporu - Okuma Kılavuzu

## 📊 Rapor Dosyaları

JaCoCo raporunda şu dosyalar yer alır:

| Dosya | Açıklama |
|-------|----------|
| **index.html** | Ana rapor sayfası - başlayın buradan |
| **jacoco.csv** | Verileri CSV formatında (Excel'de açabilirsiniz) |
| **jacoco.xml** | XML formatı - CI/CD araçlarında kullanılır |
| **jaco-sessions.html** | Test oturumları detayları |

---

## 🎯 İndex.html Sayfasında Neler Vardır?

### 1. **Üst Kısım - Genel İstatistikler**

```
Missed Instructions:  250 / 1500    (16%)
Covered Instructions: 1250 / 1500   (84%)

Line Coverage:       92 of 120      (77%)
Method Coverage:     18 of 20       (90%)
Branch Coverage:     12 of 15       (80%)
Complexity:          2.5 avg
```

- **Missed vs Covered**: Test sırasında kaç satır çalıştırıldı
- **Coverage %**: Ne kadarı test edildi
- **Branch**: İf/else gibi şartlı kodlar
- **Complexity**: Kodun karmaşıklık derecesi

### 2. **Paketlere Göre Rapor (Package List)**

Örnek:
```
Package Name                Coverage    Lines    Branches    Methods
────────────────────────────────────────────────────────────────────
com.jforce.backend          82%         145/176  12/15       18/20
├─ com.jforce.controller    95%         80/84    8/8         10/10
├─ com.jforce.service       78%         45/58    4/6         5/7
└─ com.jforce.repository    70%         20/28    0/1         3/3
```

**Ne demek?**
- Yeşil = İyi coverage (>80%)
- Sarı = Orta coverage (50-80%)
- Kırmızı = Düşük coverage (<50%)

### 3. **Sınıf Detayları (Class Coverage)**

Her pakete tıklar**ız, o paketteki sınıfları görebiliriz:

```
Class Name                  Instructions  Lines   Branches   Methods
────────────────────────────────────────────────────────────────────
UserController.java         25/26         12/13   5/5        3/3
UserService.java            15/20         8/10    2/3        2/2
```

### 4. **Kaynak Kodu Vurgulama (Source Highlights)**

Sınıfa tıklamak açılır:

```java
public class UserService {
    
    public User findById(Long id) {  // ✅ Yeşil = Test edilmiş
        if (id == null) {             // ✅ Yeşil = Test edilmiş
            return null;
        }
        return userRepository.findById(id);
    }
    
    public void deleteUser(Long id) { // 🔴 Kırmızı = Test edilmemiş
        userRepository.deleteById(id);
    }
}
```

---

## 🎨 Renk Kodlaması

| Renk | Anlamı | Durum |
|------|--------|-------|
| 🟢 **Yeşil** | Satır test edilmiş | İyi |
| 🔴 **Kırmızı** | Satır test edilmemiş | Test yok |
| 🟡 **Sarı** | Kısmi test (branch) | Bazı şartlar test edilmemiş |

---

## 📈 Coverage Metriklerini Anlamak

### Instruction Coverage
```
Tüm bytecode komutlarının % kaçı çalıştırıldı
Örnek: 84% = 1250 komut çalıştı / 1500 toplam komut
```

### Line Coverage
```
Tüm satırların % kaçı çalıştırıldı
Örnek: 77% = 92 satır test edildi / 120 toplam satır
```

### Branch Coverage
```
Koşulların (if/else/switch) % kaçı test edildi
Örnek: 80% = if true ve false her iki durum test edildi
```

### Method Coverage
```
Metodların % kaçı çağrıldı
Örnek: 90% = 18 metod test edildi / 20 toplam metod
```

### Cyclomatic Complexity
```
Kodun karmaşıklığı - ne kadar düşükse o kadar iyi
1-5: Basit
6-10: Orta
10+: Karmaşık (refactor etmeyi düşün)
```

---

## 🔍 Raporu İnceleme Adımları

### Adım 1: Genel Duruma Bakın
Main sayfadaki genel coverage yüzdesini kontrol edin:
- ✅ %80+ = Mükemmel
- ✅ %70-80 = İyi
- ⚠️ %50-70 = Orta (iyileştirilmeli)
- ❌ <50% = Düşük (test yazın)

### Adım 2: Pakletlere Göre Analiz Yapın
Hangi paketler düşük coverage'a sahip?
```
❌ Düşük coverage paketleri:
   - com.jforce.service     (45%)
   - com.jforce.util        (35%)

✅ Yüksek coverage paketleri:
   - com.jforce.controller  (95%)
   - com.jforce.model       (92%)
```

### Adım 3: Sınıflara Tıklayıp Detay Kontrol Edin
Hangi metodlar test edilmemiş?
```
UserService.java:
  ❌ deleteUser()      - Test yok
  ❌ updateUser()      - Test yok
  ✅ findById()        - Test var
```

### Adım 4: Kaynak Kodunda Kontrol Edin
Kırmızı satırları görmek için sınıfa tıklayın.

---

## 💡 Practical Örnek

**Raporda şunu gördüğünüzde:**

```
UserService Coverage: 65%
Missed Lines: 15 lines not covered
```

**Anlamı:**
- UserService'in %65'i test edilmiş
- 15 satır test edilmemiş
- %35 coverage eksik

**Ne yapmalı?**
1. Sınıfa tıklayın
2. Kırmızı satırları görmek için açın
3. O satırları test eden yeni test case'ler yazın

---

## 📋 Kontrol Listesi

### Coverage İyi mi?

- [ ] Toplam coverage %70+ mı?
- [ ] Kritik paketler (service, controller) %80+ mı?
- [ ] Hiç paket %30'dan düşük mü?
- [ ] Branch coverage %60+ mı?

### Test Yazmayı Nerede Başlayım?

**Öncelik Sırası:**
1. **Kritik paketler** - Business logic
2. **Controller** - API endpoint'leri
3. **Service** - İşlemler
4. **Repository** - Database sorguları
5. **Utility** - Helper metodları

### Exclude Edilen Sınıflar

Raporda görülmeyen sınıflar (pom.xml'de exclude ettiklerimiz):
- ❌ Application.class
- ❌ Entity sınıfları
- ❌ DTO sınıfları
- ❌ Config sınıfları
- ❌ Exception sınıfları

---

## 🚀 Coverage'ı Artırma İpuçları

### 1. Happy Path Test'i Yazın
```java
@Test
void testFindUserSuccess() {
    User user = userService.findById(1L);
    assertNotNull(user);
}
```

### 2. Exception Test'i Yazın
```java
@Test
void testFindUserNotFound() {
    assertThrows(UserNotFoundException.class, 
        () -> userService.findById(999L));
}
```

### 3. Branch'leri Test Edin
```java
@Test
void testFindUserWithNullId() {
    User user = userService.findById(null);
    assertNull(user);
}
```

### 4. Sınır Durumları Test Edin
```java
@Test
void testFindUserWithZeroId() {
    // Edge case
}
```

---

## 🔗 Raporu CI/CD'ye Ekleme

Bir sonraki adım olarak şu yazılabilir:

**GitHub Actions (.github/workflows/test.yml):**
```yaml
- name: Run Tests with Coverage
  run: mvn clean test jacoco:report

- name: Upload Coverage
  uses: codecov/codecov-action@v3
  with:
    files: ./backend/target/site/jacoco/jacoco.xml
```

**GitLab CI (.gitlab-ci.yml):**
```yaml
test:coverage:
  script:
    - mvn clean test jacoco:report
  coverage: '/Lines covered: (\d+)%/'
```

---

## 📞 Sorun Giderme

### "Rapor boş/eksik gözüküyor"
- Test'leri çalıştırdınız mı? `mvn clean test`
- Target klasöründe dosyalar var mı? `backend/target/site/jacoco/`

### "Beklediğimden daha düşük coverage"
- Exclude kurallarını kontrol edin
- Test dosyalarının bulunduğundan emin olun (`src/test/java/`)

### "Rapor HTML açılmıyor"
- Tarayıcı tarafından açın (File Explorer'da çift tıklayın)
- Veya: `start target/site/jacoco/index.html` (Windows)

---

## 🎓 Özet

| Bölüm | Nedir | Hedef |
|-------|-------|-------|
| **Instructions** | Çalışan bytecode | %80+ |
| **Lines** | Çalışan satırlar | %75+ |
| **Branches** | Test edilen şartlar | %60+ |
| **Methods** | Çağrılan metodlar | %80+ |
| **Complexity** | Karmaşıklık derecesi | <5 |

Happy testing! 🎉
