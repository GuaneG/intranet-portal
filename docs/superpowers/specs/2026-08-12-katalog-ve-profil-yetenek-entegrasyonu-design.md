# Katalog ve Profil Yetenek Entegrasyonu Tasarımı

## Amaç

Backend'de uygulanmış departman/yetenek katalog yönetimini ve kullanıcının kendi yetenek kümesini mevcut TailAdmin frontend'ine küçük, bağımsız fazlar hâlinde bağlamak.

Takvim korunur. İleride rezervasyonlar ve doğum günleri için ürün takvimine dönüştürülecektir; bu çalışma takvim verisi veya route davranışına dokunmaz. `Blank` ve diğer demo route'ları da bu fazların kapsamı dışındadır.

## Faz 1 — Katalog Yönetimi

### Kullanıcı ve route

- Yeni route: `/yonetim/katalog`.
- Sidebar'da Yönetim altında `Katalog Yönetimi` olarak yalnız `HR` ve `ADMIN` için görünür.
- Route frontend'de yalnız görünürlük amacıyla rol sınırı kullanır; backend mutation endpoint'leri gerçek yetki kararını korur.

### Ekran yapısı

- Sayfa iki sekme taşır: `Departmanlar` ve `Yetenekler`.
- Aktif sekmede mevcut kayıtlar alfabetik olarak listelenir.
- Sayfa üstündeki kısa form yeni kayıt ekler.
- Her satırdaki `Düzenle` işlemi küçük bir modal açar; kullanıcı yalnız adı değiştirir ve açık onayla kaydeder.
- Silme, personel ataması değiştirme veya yetenek seçimi bu ekranın kapsamında değildir.

### API sözleşmesi

Mevcut ortak `apiFetch` katmanı kullanılır; ayrı fetch/refresh mantığı yazılmaz.

| İşlem | Endpoint | Request | Başarı |
|---|---|---|---|
| Departmanları listele | `GET /api/departments` | - | `LookUpResponse[]` |
| Departman oluştur | `POST /api/departments` | `{ departmanAdi }` | `201`, `LookUpResponse` |
| Departman yeniden adlandır | `PATCH /api/departments/{id}` | `{ departmanAdi }` | `204` |
| Yetenekleri listele | `GET /api/skills` | - | `LookUpResponse[]` |
| Yetenek oluştur | `POST /api/skills` | `{ yetenekAdi }` | `201`, `LookUpResponse` |
| Yetenek yeniden adlandır | `PATCH /api/skills/{id}` | `{ yetenekAdi }` | `204` |

Başarılı mutation sonrasında ilgili liste yeniden yüklenir; böylece backend'in normalize edilmiş adı tek kaynak olur.

### Durumlar ve hata davranışı

- İlk yükleme: aktif sekme için loading durumu.
- Boş liste: katalog kaydı olmadığını belirten ayrı empty state.
- Liste veya mutation hatası: `ApiError` durumuna göre kontrollü, kullanıcıya uygun mesaj; ham backend detayları gösterilmez.
- Gönderim sırasında form ve modal eylemleri devre dışı kalır; ref tabanlı kilit aynı isteğin çift gönderilmesini önler.
- Başarılı işlem görünür bir başarı mesajıyla bildirilir.

## Faz 2 — Profil Yeteneklerim

### Kullanıcı deneyimi

- Mevcut `/profile` ekranındaki salt-okunur `Eğitim ve Yetenekler` kartına `Yetenekleri düzenle` eylemi eklenir.
- Tüm roller kendi yeteneklerini katalogdan çoklu seçebilir; yeni katalog kaydı oluşturamaz.
- Modal, mevcut seçimleri gösterir. Kaydetme işlemi seçili tüm ID'leri gönderir; boş seçim bütün yetenekleri kaldırır.
- Başarılı kayıttan sonra profil `GET /api/profil` ile yeniden yüklenir. Ekrandaki yetenekler response'tan türetilir, yerelde tahmin edilmez.

### API sözleşmesi

| İşlem | Endpoint | Request | Başarı |
|---|---|---|---|
| Katalog seçenekleri | `GET /api/skills` | - | `LookUpResponse[]` |
| Kendi yeteneklerini kaydet | `PUT /api/profil/yetenekler` | `{ yetenekIdleri: number[] }` | `204` |
| Güncel profil | `GET /api/profil` | - | `ProfilBilgileri` |

Bu endpoint tam-değiştirme semantiği kullanır; frontend yalnız seçilen ID kümesini gönderir ve personel kimliği göndermez.

### Durumlar ve hata davranışı

- Katalog yüklenirken modalda loading durumu gösterilir.
- Katalog boşsa seçim kontrolü yerine anlaşılır empty state gösterilir; kullanıcı mevcut seçim yoksa kaydedebilir.
- Seçenek yükleme ve kaydetme hataları ayrı ele alınır, teknik exception metni gösterilmez.
- Kaydetme devam ederken modal kapatma ve tekrar kaydetme engellenir.

## Faz 3 — Kontrollü demo temizliği

- Katalog ve profil akışları doğrulandıktan sonra yalnız ürün navigasyonundan erişilemeyen demo route'ları envanterlenir.
- Takvim ve onun FullCalendar bağımlılıkları korunur.
- `Blank`, form, tablo, grafik, UI örnek route'ları ancak bağımlılıkları doğrulandıktan ve ayrı onay alındıktan sonra kaldırılır.

## Ortak teknik kararlar

- Mevcut TypeScript/JavaScript dosya uzantıları korunur; yeni dependency veya test framework eklenmez.
- `ProtectedRoute`, `PublicRoute`, sessiz refresh ve `apiFetch` davranışı değişmez.
- API'nin backend yetkilendirmesi frontend menü veya buton gizlemeyle ikame edilmez.
- Her faz sonrasında `npx tsc --noEmit`, `npm run lint` ve `npm run build` çalıştırılır. Repository'de frontend otomatik test altyapısı olmadığından manuel browser doğrulaması mümkünse yapılır.

## Teslim sırası

1. Katalog Yönetimi tamamlanır ve doğrulanır.
2. Profil Yeteneklerim tamamlanır ve doğrulanır.
3. Demo route envanteri çıkarılır; silme yalnız ayrı onayla ele alınır.
