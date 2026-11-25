# Departman ve Yetenek Silme Tasarımı

## 1. Amaç

Kullanılmayan departman ve yetenek katalog kayıtlarının mevcut HR/Admin yetki matrisi korunarak fiziksel olarak silinebilmesini; kullanımda olan kayıtların cascade uygulanmadan kontrollü `409` ile korunmasını sağlamak.

## 2. HTTP sözleşmeleri

```http
DELETE /api/departments/{departmanId}
DELETE /api/skills/{yetenekId}
```

Başarılı silme body olmadan `204 No Content` döner. DELETE request body veya yeni request/response DTO gerektirmez.

## 3. Yetki ve hata matrisi

Mevcut katalog create/update matrisi aynen korunur:

- Aktif HR ve Admin: izinli
- PERSONEL ve YONETICI: `403`
- Pasif HR/Admin: `403`
- DB’de bulunmayan principal: `404 Personel bulunamadı.`
- Kimlik doğrulanmamış istek: `401`
- Pozitif olmayan path ID: `400`
- Katalog kaydı bulunamazsa `404`

Kullanımda kayıtlar:

- Aktif veya pasif herhangi bir personele bağlı departman: `409 Departman kullanımda olduğu için silinemez.`
- Herhangi bir `personel_yetenek` ilişkisine bağlı yetenek: `409 Yetenek kullanımda olduğu için silinemez.`

Pasif personel de zorunlu departman FK’sını taşır; geçmiş bütünlüğü nedeniyle ilişki olarak kabul edilir. Cascade veya sessiz yeniden atama yapılmaz.

## 4. Transaction ve yarış güvenliği

Silme service katmanında tek transaction içinde çalışır:

1. Aktör mevcut katalog yönetimi yaklaşımıyla doğrulanır.
2. Katalog satırı pessimistic write lock ile yüklenir.
3. İlişki varlığı kontrol edilir.
4. Kullanım varsa güvenli `409` üretilir.
5. Kayıt silinir ve transaction içinde `flush` edilir.
6. Başarılı flush sonrasında audit aynı transaction’a yazılır.

Parent satır kilidi, yeni child FK ekleme yarışını DB seviyesinde sınırlar. Önceden başlamış yarış veya gerçek şema farkı nedeniyle oluşabilecek `DataIntegrityViolationException`, SQL/constraint ayrıntısı sızdırılmadan aynı güvenli `409` mesajına çevrilir.

Repository etkileri:

- `DepartmanRepository`: pessimistic-lock ID lookup
- `PersonelRepository`: departmana bağlı herhangi bir personel varlığı
- `YetenekRepository`: pessimistic-lock ID lookup
- `PersonelYetenekRepository`: yeteneğe bağlı ilişki varlığı

## 5. Audit

Yeni audit olayları:

- `DEPARTMAN_SILINDI`
- `YETENEK_SILINDI`

Aktör işlemi yapan HR/Admin’dir; hedef personel `null` olur. Detay yalnız katalog için güvenli kayıt adını içerir. Silme ve audit birlikte commit veya rollback olur. Başarısız `409` isteği audit oluşturmaz.

## 6. Test tasarımı

- Aktif HR ve Admin’in ilişkisiz kaydı `204` ile silmesi
- Bütün yasak aktör/principal senaryoları
- Pozitif olmayan ID `400`, missing kayıt `404`
- Aktif ve pasif personele bağlı departmanda `409`; personel kaydı korunur
- Herhangi bir personel-yetenek ilişkisinde `409`; join kaydı korunur
- Cascade olmadığının doğrulanması
- Başarılı audit action/actor/null-target/güvenli detay
- Dış transaction rollback’inde katalog ve auditin geri gelmesi
- FK yarışı/constraint ihlalinin kontrollü `409` olması
- Create/update/lookup regresyonlarının korunması

