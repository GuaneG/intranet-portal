# Intranet Portal

JForce için geliştirilen, Spring Boot backend ve React frontend’den oluşan şirket içi portal projesidir. Proje; kimlik doğrulama, personel/profil yönetimi, ekip dizini, organizasyon hiyerarşisi, tatil takvimi, izin ve oda rezervasyonu iş akışlarını tek uygulamada toplar.

## Teknoloji yığını

**Backend**

- Java 21, Spring Boot 3.5
- Spring Security ve JWT
- Spring Data JPA / Hibernate
- MySQL 8
- SpringDoc OpenAPI
- Maven, JUnit 5, Mockito, Testcontainers ve JaCoCo

**Frontend**

- React 19 ve TypeScript
- Vite
- Tailwind CSS 4
- React Router
- FullCalendar

## Güncel kapsam

| Alan | Durum |
|---|---|
| Authentication, refresh/logout ve ilk parola akışı | Tamamlandı |
| Profil, profil fotoğrafı ve kişisel yetenekler | Tamamlandı |
| HR/Admin personel, rol, departman ve yetenek yönetimi | Tamamlandı |
| Ekip dizini, görünürlük kuralları ve yönetici hiyerarşisi | Backend ve frontend tamamlandı |
| Dashboard ekip özeti | Tamamlandı |
| Tatil takvimi ve iş günü hesabı | Backend tek/çok günlük tatil gruplarını destekliyor; aktif oda rezervasyonuyla çakışan tatil oluşturma/güncelleme güvenli `409` ile engelleniyor |
| Tatil takvimi frontend’i | Tek ve çok günlük tatil grubu görüntüleme/yönetme akışları hazır |
| Doğum günleri | Dashboard kartı ile şirket-geneli güvenli takvim backend ve frontend entegrasyonu hazır |
| İzin talebi ve karar akışı | Lookup, iş günü önizleme, talep oluşturma, kendi talepleri, owner iptali, Yönetici/Admin kuyrukları ve onay/red backend ile frontend’de hazır |
| Admin audit log görüntüleme | Güvenli, filtreli ve sayfalı backend sorgusu ile yalnız Admin frontend ekranı hazır |
| Oda rezervasyonu | Backend ve frontend oda/takvim/create/geçmiş/iptal, Admin şirket geçmişi, şirket takvimi, pasifleştirme ve dashboard doluluk akışları tamamlandı |
| Ekipman talebi, envanter ve zimmet | Backend talep/karar, stok, zimmet/iade, güvenlik ve migration akışları hazır; frontend entegrasyonu bekliyor |
| Duyuru ve takdir | Veri modeli veya planlama aşamasında |

Uygulanan endpoint ve rol sözleşmelerinin ayrıntısı [API_SPEC.md](API_SPEC.md), güvenlik kararları [SECURITY.md](SECURITY.md), yol haritası [ROADMAP.md](ROADMAP.md) ve tamamlanan çalışmalar [PROGRESS.md](PROGRESS.md) içindedir.

## Proje yapısı

```text
.
├── backend/
│   ├── db/manual-migrations/   # Sırası gözetilerek elle uygulanacak, sürümlü MySQL migration dosyaları
│   └── src/                    # Spring Boot kaynakları ve testler
├── frontend/
│   ├── src/                    # React uygulaması
│   └── test/                   # Hafif node:test kontrolleri
└── docs/superpowers/           # Tarihli tasarım ve uygulama planı kayıtları
```

`docs/superpowers` altındaki tarihlendirilmiş dosyalar kendi teslimlerinin tarihsel kayıtlarıdır. Güncel ürün sözleşmesi için kökteki canlı belgeler esas alınır.

## Backend’i çalıştırma

Gereksinimler: Java 21, Docker Desktop ve/veya erişilebilir bir MySQL 8 veritabanı.

Gerekli veritabanı ve JWT ayarlarını ortam değişkenleriyle sağladıktan sonra:

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

Testler:

```powershell
.\mvnw.cmd test
```

`backend/db/manual-migrations` altındaki SQL dosyaları mevcut ortamlara sırayla uygulanmalıdır. `ddl-auto=update`, MySQL `ENUM` genişletme veya kontrollü veri dönüşümleri için migration yerine kullanılmamalıdır.

## Frontend’i çalıştırma

Gereksinim: güncel bir Node.js LTS sürümü.

```powershell
cd frontend
npm install
npm run dev
```

Frontend varsayılan olarak backend’e `http://localhost:8080` üzerinden bağlanır. Statik kontroller için:

```powershell
npm run lint
npm run build
```

Frontend’de birleşik bir `npm test` komutu henüz yoktur; `frontend/test` altında doğrudan Node ile çalıştırılan hafif testler bulunur.

## Katkı ve çalışma kuralları

Geliştirme ve modül tamamlanma kontrol listeleri için [CONTRIBUTING.md](CONTRIBUTING.md) belgesine bakın.
