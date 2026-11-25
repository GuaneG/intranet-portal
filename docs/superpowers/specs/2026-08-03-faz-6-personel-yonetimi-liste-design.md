# Faz 6 Personel Yönetimi Listesi Tasarımı

**Tarih:** 2026-08-03  
**Durum:** Kullanıcı tarafından onaylandı.

## Amaç

Faz 6'nın ilk küçük tesliminde HR ve ADMIN kullanıcılarına rol korumalı, salt okunur personel yönetimi listesi sunmak. Oluşturma, güncelleme, pasifleştirme ve rol değiştirme sonraki ayrı teslimlerde bu temel üzerine eklenir.

## Route ve Yetki

- Route: `/yonetim/personel`
- Route mevcut `ProtectedRoute` altındaki `AppLayout` içinde çalışır.
- Yeni `RoleRoute`, `roller` prop'u ile yalnız `HR` ve `ADMIN` rollerine izin verir.
- Oturumu olmayan kullanıcı `/signin`, authenticated fakat yetkisiz kullanıcı `/` adresine `replace` ile yönlendirilir.
- Sidebar görünürlüğü ve `RoleRoute` yalnız kullanıcı deneyimidir; gerçek yetki backend'dedir.

## Liste ve Filtreler

Liste şu endpoint'i kullanır:

```http
GET /api/hr/personel?page=0&size=20&aktif=true
```

Boş opsiyonel filtreler query string'e eklenmez. Aktiflik “Tümü” seçildiğinde `aktif` parametresi gönderilmez.

İlk açılışta `aktif=true` gönderilir. Filtreler:

- ad, soyad veya kullanıcı adı araması,
- departman,
- rol,
- aktiflik: Aktif, Pasif veya Tümü.

Filtre alanları taslak state'te tutulur. Yalnız “Filtrele” veya Enter ile uygulanır; her uygulamada `page=0` olur. “Temizle” arama, departman ve rolü kaldırır; aktifliği başlangıç değeri olan Aktif'e döndürür. Pagination uygulanmış filtreleri korur.

Rol seçenekleri aktöre göre gösterilir:

- HR: `PERSONEL`, `HR`
- ADMIN: `PERSONEL`, `HR`, `YONETICI`

Backend yasak hedefleri liste ve count sonucundan çıkarır. Frontend ayrıca yetkisiz hedef üretmeye çalışmaz.

## Ekran

Tablo sütunları:

- ad soyad ve kullanıcı adı,
- departman,
- rol,
- aktiflik.

İlk teslimde satır eylemi, oluşturma, düzenleme, pasifleştirme veya rol değiştirme butonu gösterilmez. Çalışmayan veya “Yakında” eylemi eklenmez.

Masaüstünde tablo, mobilde okunabilir yatay kaydırma veya satırların kırılmasını engelleyen mevcut TailAdmin tablo davranışı kullanılır. Sayfa numarası kullanıcıya bir tabanlı gösterilir; backend state'i sıfır tabanlı kalır.

## Component ve Dosyalar

Yeni dosyalar:

- `frontend/src/components/RoleRoute.tsx`
- `frontend/src/types/personelYonetim.ts`
- `frontend/src/pages/PersonelYonetimi.tsx`
- `frontend/src/components/personel-yonetimi/PersonelYonetimTablosu.tsx`

Değişecek dosyalar:

- `frontend/src/lib/api.js`: personel listesi ve departman lookup çağrıları.
- `frontend/src/App.tsx`: HR/ADMIN rol korumalı route.
- `frontend/src/layout/AppSidebar.tsx`: Personel Yönetimi öğesindeki `yakinda` kaldırılır ve gerçek path eklenir.

Diğer sidebar öğeleri, backend, dashboard ve Ekip Dizini dosyaları değişmez.

## State ve İstek Yarışı

Sayfa şu state'leri yönetir:

- taslak filtreler,
- uygulanmış filtreler,
- mevcut sayfa,
- liste response'u,
- loading/refreshing,
- hata.

Her liste isteği artan sıra numarası alır; yalnız en son isteğin sonucu state'e yazılır. İlk yüklemede loading görünümü kullanılır. Sonraki filtre, pagination veya retry isteğinde önceki başarılı liste istek tamamlanana kadar korunur, alan `aria-busy` olur ve aynı anda ikinci eylem engellenir. Yeni istek başarısız olursa eski liste kaldırılır; istenen filtre ve sayfa state'i korunur, hata görünümü aynı sorguyu yeniden denetir. Böylece ekrandaki satırlar hiçbir zaman başka bir filtre veya sayfa bilgisiyle birlikte gösterilmez.

## Loading, Empty ve Error

- İlk loading sırasında empty mesajı gösterilmez.
- Filtresiz aktif liste boşsa “Aktif personel bulunmuyor” gösterilir.
- Filtreli sonuç boşsa “Filtrelere uygun personel bulunamadı” gösterilir.
- Kontrollü backend mesajı uygun durumlarda kullanılabilir; ham exception veya response gösterilmez.
- Hata durumunda “Personel listesi yüklenemedi” ve “Tekrar dene” sunulur.
- Departman lookup hatası listeyi gizlemez; departman filtresi pasifleşir ve ayrı retry gösterilir.

## Doğrulama

- `RoleRoute`: PERSONEL/YONETICI yönlendirilir, HR/ADMIN sayfayı görür.
- İlk istek `aktif=true` içerir.
- Ad, soyad, kullanıcı adı, departman, rol ve aktiflik filtreleri ayrı ve birlikte çalışır.
- “Temizle” yalnız aktif başlangıç durumuna döner.
- Filtreler pagination sırasında korunur.
- Loading, refreshing, empty, liste hatası ve lookup hatası ayrıdır.
- Eski istek yeni sonucu ezemez.
- Sidebar yalnız HR/ADMIN için çalışan link gösterir.
- Hedef lint, production build ve dört rolle masaüstü/mobil browser kontrolü yapılır.
- Tam lint'teki görev öncesi ilgisiz hatalar ayrı raporlanır.

Yeni dependency veya test framework'ü eklenmez. Git komutu çalıştırılmaz.
