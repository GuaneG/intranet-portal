# JaCoCo Raporu - Hızlı Referans Kartı

## 🎯 Başlamadan İlk Adımlar

### 1. Raporu Açın
```
backend/target/site/jacoco/index.html
```
Tarayıcıda açın (Chrome, Firefox, Edge)

### 2. Hangi Sayfada Ne Var?

```
📄 index.html
│
├─ 📊 Genel İstatistikler (sayfa üstü)
│  ├─ Coverage %
│  ├─ Missed vs Covered
│  └─ Branch Coverage
│
├─ 📦 Paket Listesi (ortada)
│  ├─ com.jforce.backend
│  ├─ com.jforce.controller
│  ├─ com.jforce.service
│  └─ Tıkla → Detayları Gör
│
└─ 📄 Sınıf Detayları
   ├─ UserController
   ├─ UserService
   └─ Tıkla → Kaynak Kodu Gör
```

---

## 📊 Sayfada Gördüğünüz İstatistikler

### Örnek Rapor:
```
ELEMENT                 MISSED    COVERED   COV. %
─────────────────────────────────────────────────
Missed Instructions        250      1250      84%
Missed Branches             3        12       80%
Missed Lines               28        92       77%
Missed Methods              2        18       90%
Missed Complexity          2.5      8.5      77%
```

### Bunu Nasıl Okuyacaksınız?

| Metrik | Anlamı | Hedef |
|--------|--------|-------|
| **Missed Instructions** | Çalışmayan kod komutları | 0 |
| **Covered Instructions** | Çalışan kod komutları | Max |
| **Missed Branches** | Test edilmeyen if/else | 0 |
| **Missed Lines** | Test edilmeyen satırlar | 0 |
| **Missed Methods** | Çağrılmayan metodlar | 0 |

---

## 🌈 Renk Kodlaması Hızlı Rehberi

### Paket/Sınıf Listesinde:

```
🟢 Yeşil ████████ 90% → Çok İyi
🟡 Turuncu ██████░░ 65% → Orta
🔴 Kırmızı ████░░░░ 45% → Düşük
⚫ Siyah   ░░░░░░░░ 0%  → Test Yok
```

### Kaynak Kodunda:

```java
public class UserService {
    
    🟢 public User find() {        // Yeşil = Test edilmiş
        🟢 return repository.get(); // Yeşil = Çalıştırıldı
    }
    
    🔴 public void delete() {      // Kırmızı = Test edilmemiş
        🔴 repo.delete();          // Kırmızı = Hiç çalışmadı
    }
}
```

---

## 📈 Metrik Değerleri Nedir?

### Instructions
```
= Tüm Java bytecode komutları
Örnek: "25 / 30 instructions"
Anlamı: 30 komuttan 25'i çalıştırıldı = %83
```

### Branches
```
= if, else, switch, for, while gibi şartlı kod
Örnek: "12 / 15 branches"
Anlamı: 15 şartten 12'si test edildi = %80
```

### Lines
```
= Kaynak kod satırları
Örnek: "92 / 120 lines"
Anlamı: 120 satırdan 92'si çalıştırıldı = %77
```

### Methods
```
= Metodlar/Fonksiyonlar
Örnek: "18 / 20 methods"
Anlamı: 20 metoddan 18'i çağrıldı = %90
```

### Complexity
```
= Kodun karmaşıklığı
1-5: ✅ Basit
6-10: ⚠️ Orta
10+: 🔴 Karmaşık
```

---

## 🎯 Nasıl Yapmalı? Senaryo Tabanlı

### Senaryo 1: Coverage % Çok Düşük
```
Rapor: UserService Coverage: 35%
Başı Problem!
```

**Yapılacaklar:**
1. UserService'e tıkla
2. Kırmızı satırları gör
3. O satırlar için test yaz

### Senaryo 2: Bir Metodun Test'i Yok
```
Rapor: UserService.java
  ✅ findById()      - 95%
  ✅ update()        - 90%
  ❌ delete()        - 0%
```

**Yapılacaklar:**
```java
@Test
void testDeleteUser() {
    userService.delete(1L);
    // verify silme işlemi yapıldı
}
```

### Senaryo 3: Branch Coverage Düşük
```
Rapor: UserService.findById()
  ❌ Branch Coverage: 40%
  = if (id == null) şartı tam test edilmemiş
```

**Yapılacaklar:**
```java
@Test
void testFindByIdNull() {
    assertNull(userService.findById(null));
}

@Test
void testFindByIdValid() {
    assertNotNull(userService.findById(1L));
}
```

---

## 💾 CSV/XML Dosyalarını Kullanma

### jacoco.csv (Excel'de Aç)
```
GROUP,PACKAGE,CLASS,SOURCEFILE,INSTRUCTION_MISSED,INSTRUCTION_COVERED,...
...
com.jforce,com.jforce.service,UserService,UserService.java,25,200,...
```

**Faydası:**
- Verileri filterleyebilir
- Grafik yapabilir
- Rapor oluşturabilir

### jacoco.xml (CI/CD Araçlarında)
Bitbucket, GitLab, SonarQube gibi araçlar bu dosyayı okur.

---

## ✅ Kontrol Listesi - Raporu Anladı mısınız?

- [ ] İndex.html dosyasını açabildim
- [ ] Genel coverage % 'ini görebildim
- [ ] Paket listesinde farklı coverage yüzdeleri gördüm
- [ ] Bir pakete tıklayıp sınıfları görebildim
- [ ] Bir sınıfa tıklayıp kaynak kodu ve renkleri gördüm
- [ ] Kırmızı satırları (test edilmeyen kod) tanıdım
- [ ] Coverage'ı artırmak için hangi satırları test etmem gerektiğini anladım

---

## 🚀 İleri Seviye: Raporun Ayrıntıları

### Coverage Arttırma Strateji

**Sırasıyla yapın:**

1️⃣ **Kritik Paketleri %90+ Yap**
   - Controller (API endpoint'leri)
   - Service (İş mantığı)

2️⃣ **Orta Paketleri %70+ Yap**
   - Utility
   - Helper

3️⃣ **Önemsiz Paketleri En Az %50**
   - Constants
   - Enums

### Branch Coverage Arttırma

```java
// Kötü: %50 branch coverage
public boolean isValid(User user) {
    if (user != null && user.getId() > 0) {
        return true;
    }
    return false;
}

// İyi test: %100 branch coverage
@Test void testBothTrue() { /* user != null && id > 0 */ }
@Test void testUserNull() { /* user == null */ }
@Test void testIdZero() { /* user != null && id <= 0 */ }
```

---

## 📞 Sık Sorulan Sorular

### S: "Raporda çok sınıf yok, neden?"
**C:** Exclude kurallarına bakmış mısınız?
```xml
<excludes>
    <exclude>**/Application.class</exclude>
    <exclude>**/entity/**</exclude>
    <exclude>**/dto/**</exclude>
</excludes>
```

### S: "Coverage hiç değişmiyor"
**C:** 
- `mvn clean test` çalıştırdınız mı?
- Test dosyalarını yazdınız mı? (`src/test/java/`)
- Eski raporu sildim mı? (`rm -rf target/`)

### S: "Rapor açılmıyor"
**C:** 
- İndex.html dosyası var mı? (backend/target/site/jacoco/index.html)
- Tarayıcıdan açtınız mı?

---

## 🎓 Özet - 1 Dakikada JaCoCo

1. **Raporu aç** → `index.html`
2. **Genel coverage'ı gör** → Sayfa üstünde % ile
3. **Düşük paketleri bul** → Kırmızı olanlar
4. **Test yok olan kodları bul** → Sınıfa tıkla, kırmızı satırları gör
5. **Test yaz** → Kırmızı satırlar için test case'ler ekle
6. **Tekrarla** → `mvn clean test jacoco:report`

---

**İhtiyacınız varsa:** JACOCO_RAPOR_OKUMA_KILAVUZU.md dosyasına bakın 📖
