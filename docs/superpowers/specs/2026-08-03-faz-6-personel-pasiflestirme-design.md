# Faz 6 — Personel Pasifleştirme Tasarımı

## Amaç

HR ve Admin, Personel Yönetimi detay ekranından yetkili oldukları aktif bir personeli pasife alabilmelidir. İşlem geri alınması kolay olmayan bir iş etkisi taşıdığı için kullanıcı onayı gerektirir.

## Kapsam

- Mevcut personel detay ekranına pasifleştirme aksiyonu eklenir.
- İstemci, `PATCH /api/hr/personel/{personelId}/pasiflestir` isteğini body olmadan gönderir.
- Başarılı `204 No Content` sonrasında kullanıcı varsayılan aktif personel listesine yönlendirilir.
- Başarısız istekte kullanıcı detay ekranında kalır ve anlaşılır hata mesajını görür.

## Arayüz Akışı

1. Detay verisi yüklendiğinde hedef aktifse `Düzenle` aksiyonunun yanında tehlikeli işlem stiliyle `Pasifleştir` düğmesi gösterilir.
2. Düğme, hedef kişinin adıyla birlikte onay alanını açar. Metin; personelin pasif duruma geçeceğini, oturumlarının sonlandırılacağını ve bu teslimde yeniden aktifleştirme ekranı olmadığını belirtir.
3. `Vazgeç` onayı kapatır ve ağ isteği göndermez.
4. `Pasifleştir` tek isteği gönderir. İstek sürerken onay alanındaki kontroller devre dışı kalır; yinelenen istek oluşmaz.
5. Başarıda kullanıcı `/yonetim/personel` adresine yönlendirilir. Varsayılan aktif filtre sayesinde hedef kişi listede görünmez.

## Yetki ve Güvenlik

- Frontend düğmesi yalnız UX amaçlıdır; gerçek karar backend'dedir.
- Backend yalnız aktif HR/Admin aktörlere izin verir.
- HR, `PERSONEL` ve `HR`; Admin ek olarak `YONETICI` hedeflerini pasifleştirebilir.
- `ADMIN` hedefler ve öz-hedef işlemleri backend tarafından reddedilir.
- Yöneticinin pasifleştirilmesi halinde doğrudan çalışanlarının yönetici ilişkisi backend tarafından kaldırılır.
- Backend işlemde hedefin refresh tokenlarını iptal eder ve `PERSONEL_PASIFE_ALINDI` audit kaydı oluşturur.

## Kapsam Dışı

- Yeniden aktifleştirme
- Toplu pasifleştirme
- Yeni bir route veya ayrı pasifleştirme sayfası
- Backend endpoint veya yetki kurallarında değişiklik

## Doğrulama

- Yetkili kullanıcı aktif hedefte onayı açabilir, vazgeçebilir ve istek göndermeden detayda kalır.
- Başarılı pasifleştirme sonrası aktif listeye dönülür.
- `403` veya ağ hatasında kullanıcıya hata gösterilir; ekran ve onay yeniden kullanılabilir kalır.
- Pasif hedefte düğme gösterilmez.
- Hedefe yönelik backend yetki kararı (öz-hedef, admin hedef veya rol sınırı) frontend tarafından güvenlik kabul edilmez.
