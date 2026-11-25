# Faz 6 — Personel Aktifleştirme Tasarımı

## Amaç

HR ve Admin, Personel Yönetimi detay ekranından yetkili oldukları pasif personeli yeniden aktifleştirebilmelidir.

## Seçilen Akış

- Aktifleştirme, liste satırında değil mevcut personel detay ekranında sunulur.
- Bu seçim, pasifleştirmeyle aynı işlem yüzeyini korur; kullanıcı hedefin rolü ve durumunu görerek işlem yapar ve liste/pagination aksiyonlarını gereksizce karmaşıklaştırmaz.
- Detay verisi pasifse `Aktifleştir` düğmesi görünür; aktif kayıtta görünmez.
- Kullanıcı düğmeye bastığında ad-soyadı içeren kısa bir onay modali açılır.
- Onaydan sonra istemci body olmadan `PATCH /api/hr/personel/{personelId}/aktiflestir` çağrısı yapar.
- Başarılı `204 No Content` cevabından sonra kullanıcı `replace` ile varsayılan aktif personel listesine döner.

## Yetki Sınırı

- Detay endpoint'i aynı hedef matrisini backend'de uygular; bu nedenle detay başarıyla yüklendiyse hedefin görünürlüğü zaten sunucu tarafından belirlenmiştir.
- Frontend, kendi kimliği için güvenilir `personelId` taşımadığından öz-hedef kararını yeniden üretmez.
- `ADMIN` hedefi, öz-hedef veya HR'ın `YONETICI` hedefi için doğrudan istek yapılırsa backend kesin olarak `403` üretir.
- Frontend yalnızca `detay.aktif` ile UX görünürlüğünü belirler; yetkilendirme güvenliği backend'dedir.

## İşlem ve Hata Davranışı

- Onay verilmeden ağ isteği gönderilmez.
- Ref tabanlı kilit ve disabled kontroller, hızlı yinelenen tıklamalarla birden fazla PATCH gönderilmesini önler.
- Başarı cevabı JSON olarak okunmaz.
- `401`, `403`, `404` ve ağ hataları ortak `ApiError` mesaj mekanizmasıyla modalda gösterilir; modal açık kalır ve kullanıcı yeniden deneyebilir.
- Optimistic update uygulanmaz. Başarılı yönlendirme sonrası liste backend'den güncel veriyi tekrar çeker.
- Yeniden aktifleştirilen yöneticinin eski çalışan ilişkileri frontend'de geri kurulmaz; backend sözleşmesindeki davranış olduğu gibi korunur.

## Kapsam Dışı

- Mevcut pasifleştirme akışının değiştirilmesi
- Liste satırına aktifleştirme aksiyonu eklenmesi
- Yeniden aktifleştirme sonrası çalışan-yönetici ilişkisini otomatik kurma
- Backend endpoint, rol kontrolü veya audit değişikliği
- Yeni dependency ya da test framework'ü
