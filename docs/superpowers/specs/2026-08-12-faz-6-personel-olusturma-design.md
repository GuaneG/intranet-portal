# Faz 6 — Personel Oluşturma Tasarımı

## Amaç

HR ve Admin'in Personel Yönetimi üzerinden backend sözleşmesine uygun yeni bir `PERSONEL` kaydı oluşturabilmesini sağlamak.

## Arayüz ve Route

- Personel listesine `Yeni Personel` aksiyonu eklenir.
- Aksiyon, HR/Admin korumalı `/yonetim/personel/yeni` ayrı form sayfasına gider.
- Ayrı sayfa; kullanıcı adı, kişisel alanlar, departman ve yönetici lookup'ları için liste modalinden daha okunur ve doğrudan URL ile de güvenli biçimde açılabilir.

## Form Sözleşmesi

İstemci yalnız `POST /api/hr/personel` endpoint'inin kabul ettiği alanları gönderir:

- Zorunlu: `kullaniciAdi`, `ad`, `soyad`, `dogumTarihi`, `ePosta`, `departmanId`, `isGirisTarihi`
- Opsiyonel: `yoneticiId`

Rol, parola, sicil numarası, aktiflik, işten çıkış tarihi ve diğer server-controlled alanlar formda veya request'te bulunmaz.

Departman seçilmeden yönetici seçilemez. Departman değiştiğinde önceki yönetici seçimi temizlenir; yeni departmanın atanabilir yöneticileri mevcut lookup endpoint'iyle yüklenir. Frontend yalnız kullanım kolaylığı sunar; yönetici aktifliği, rolü ve departman eşleşmesini backend doğrular.

## Başarı Akışı

- Başarılı `201 Created` response'u `{ personelId, kullaniciAdi }` içerir.
- Formun yerine aynı sayfada başarı kartı gösterilir.
- Kart, personelin ilk girişte kullanıcı adıyla giriş yapıp parolasını değiştirmesi gerektiğini belirtir; parolayı yeni veya ayrı bir hassas değer olarak ekranda göstermez.
- Kullanıcı `Personel detayını aç` veya `Listeye dön` aksiyonlarından birini seçebilir.

## Hata ve Yüklenme Davranışı

- `400`, `401`, `403`, `404`, `409` ve ağ hataları ortak `ApiError` mesaj düzeniyle formda görünür.
- Başarısız istekte form değerleri korunur ve kullanıcı düzeltip tekrar deneyebilir.
- Gönderim boyunca bütün alanlar, lookup tekrar deneme düğmeleri ve gönderim düğmesi devre dışıdır; ref tabanlı kilit hızlı çift POST isteğini de önler.

## Güvenlik ve Kapsam

- Route görünürlüğü yalnız UX'tir; backend HR/Admin yetkisini, aktif aktörü ve tüm request kurallarını belirler.
- Token, parola veya backend hata nesnesi UI'a/loga aktarılmaz.
- Personel düzenleme, pasifleştirme ve aktifleştirme akışları değiştirilmez.
- Yeni dependency, test framework'ü veya backend değişikliği yapılmaz.
