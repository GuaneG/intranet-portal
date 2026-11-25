# Admin Parola Sıfırlama Tasarımı

## 1. Amaç

Aktif Admin’in başka bir `PERSONEL`, `HR` veya `YONETICI` hesabına güvenli bir geçici parola atamasını, mevcut oturumları geçersiz kılmasını ve kullanıcıyı sonraki girişte mevcut ilk-parola değiştirme akışına zorlamasını sağlamak.

## 2. HTTP sözleşmesi

```http
PATCH /api/admin/personel/{personelId}/parola-sifirla
Content-Type: application/json

{
  "geciciParola": "Gecici123!"
}
```

Başarılı işlem body olmadan `204 No Content` döner. Geçici parola veya hash hiçbir response’a eklenmez.

## 3. Yetki ve hedef matrisi

- JWT rolü `ADMIN` olmalıdır.
- Aktör DB’den yeniden yüklenir; mevcut, aktif ve gerçek rolü `ADMIN` olmalıdır.
- Aktif veya pasif `PERSONEL`, `HR` ve `YONETICI` hedefleri izinlidir.
- Admin öz-hedefi ve bütün `ADMIN` hedefler `403` alır.
- JWT rolü Admin olsa bile DB rolü Admin olmayan aktör `403` alır.
- Eksik aktör veya hedef `404 Personel bulunamadı.` döner.
- Kimlik doğrulanmamış istek `401` alır.
- Hedef yetkisi geçici parola validation’ından önce uygulanır.

Pasif hedefin parolası hazırlanabilir; hedef yeniden aktifleştirilene kadar giriş yapamaz.

## 4. Katmanlar ve veri akışı

`AdminPersonelController` yalnız path, `@Valid` request, `Authentication.getName()` ve `204` response’uyla ilgilenir.

Yeni `AdminParolaSifirlaRequest` yalnız `geciciParola` alanını taşır. Null, eksik veya boş değer HTTP validation ile `400` olur.

`ParolaService` yeni transactional use-case’i yönetir:

1. Method security ile `ADMIN` rolünü doğrular.
2. Aktörü `PersonelYonetimYetkiService.aktifAdminAktoruGetir` ile DB’den doğrular.
3. Hedefi path ID’sinden yükler.
4. Öz-hedef ve Admin hedefini reddeder.
5. Geçici parolayı mevcut güçlü parola politikasıyla doğrular.
6. BCrypt hash üretir ve yalnız hash’i entity’ye yazar.
7. `parolaDegistirilmeliMi=true` yapar.
8. Hedefin bütün refresh tokenlarını siler.
9. Güvenli auditi aynı transaction’da kaydeder.

Yeni repository sorgusu gerekmez.

## 5. Parola ve oturum davranışı

Geçici parola mevcut parola politikasını kullanır: 8–72 karakter, en fazla 72 UTF-8 byte, boşluksuz; küçük harf, büyük harf, rakam ve özel karakter içerir; kullanıcı adı veya mevcut parolayla aynı olamaz.

Hedefin bütün refresh token kayıtları aynı transaction’da silinir. Eski refresh cookie istemcide kalsa bile DB kaydı olmadığı için refresh isteği `401` alır.

Access token stateless olduğundan anında geri alınamaz; mevcut yapılandırmadaki en fazla 15 dakikalık kalan ömrü boyunca çalışabilir. Bu sınır `SECURITY.md` içinde açıkça belgelenir.

Yeni login, `parolaDegistirilmeliMi=true` nedeniyle token üretmeden `428` döner. Kullanıcı mevcut `/api/auth/ilk-parola` akışıyla geçici parolayı doğrulayıp yeni parolasını belirler.

## 6. Audit ve transaction

Yeni audit olayı `PAROLA_SIFIRLANDI` olur.

- Aktör: işlemi yapan Admin
- Hedef: path’teki personel
- Kullanıcı adı: Admin’in kullanıcı adı
- Detay: `Personel parolası Admin tarafından sıfırlandı.`

Geçici parola, hash veya credential bilgisi audit, response veya kontrollü loglara yazılmaz. Hash, flag, refresh-token silme ve audit birlikte commit veya rollback olur.

## 7. Test tasarımı

- Admin’in aktif/pasif `PERSONEL`, `HR`, `YONETICI` hedeflerinde `204` alması
- BCrypt hash, flag ve refresh-token iptali
- Başka kullanıcı tokenlarının korunması
- Geçici parolayla login `428`, ilk parola değişiminden sonra yeni login başarısı
- Yasak aktör rolleri, JWT/DB rol uyuşmazlığı, pasif/missing Admin, missing hedef ve `401`
- Öz-hedef ve Admin hedefinin validation’dan önce `403` alması
- Null/boş/eksik/bozuk body ve bütün parola politika sınırları
- Başarısızlıklarda hash, flag, token ve audit yan etkisizliği
- Güvenli audit aktörü/hedefi/detayı
- Dış transaction hatasında bütün değişikliklerin rollback olması
- Mevcut parola, login, refresh ve personel yönetimi regresyonları

