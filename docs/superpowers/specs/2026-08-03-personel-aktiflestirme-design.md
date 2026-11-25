# Personel Aktifleştirme Tasarımı

**Tarih:** 2026-08-03

## Amaç

HR ve Admin'in mevcut personel yönetimi yetki matrisi içinde pasif personeli güvenli, idempotent ve audit edilebilir biçimde yeniden aktifleştirmesini sağlamak.

## Kapsam

- `PATCH /api/hr/personel/{personelId}/aktiflestir`
- Request body yoktur.
- Başarılı gerçek değişiklik ve zaten aktif no-op sonucu `204 No Content` döner.
- Frontend değişikliği, access-token versioning, geçmiş yönetici ilişkilerini geri yükleme ve istihdam geçmişi tablosu kapsam dışıdır.

## Yetki matrisi

| Aktör | İzin verilen hedefler |
|---|---|
| Aktif HR | `PERSONEL`, başka HR |
| Aktif Admin | `PERSONEL`, HR, `YONETICI` |

- HR, `YONETICI` veya `ADMIN` hedefini aktifleştiremez.
- Admin, `ADMIN` hedefini aktifleştiremez.
- HR/Admin kendisini aktifleştiremez.
- Pasif HR/Admin yönetim işlemi yapamaz.
- Yetki ve öz-hedef kontrolleri no-op kararından önce çalışır.

## Mimari ve veri akışı

Controller, JWT principal ID'sini ve path'teki hedef personel ID'sini `PersonelService.personelAktiflestir(...)` metoduna aktarır. Metot `@Transactional` ve `@PreAuthorize("hasAnyRole('HR', 'ADMIN')")` ile korunur.

Service sırası:

1. `PersonelYonetimYetkiService.aktifAktoruGetir(...)` ile aktörü yükle ve aktifliğini doğrula.
2. Hedef personeli yükle; bulunamazsa `404 Personel bulunamadı.` döndür.
3. `PersonelYonetimYetkiService.hedefiDogrula(...)` ile öz-hedef ve rol matrisini uygula.
4. Hedef zaten aktifse yan etkisiz dön.
5. `calisiyorMu=true` ve `isCikisTarihi=null` yap.
6. `PERSONEL_AKTIFLESTIRILDI` audit kaydını aynı transaction içinde oluştur.

Gerçek aktifleştirme parola, rol, departman, yönetici, refresh-token veya access-token üretmez/değiştirmez. Pasifleştirme sırasında kaldırılmış eski çalışan-yönetici ilişkileri otomatik geri yüklenmez. Normal personelin hâlâ mevcut olan geçerli yönetici ilişkisine dokunulmaz.

## Idempotency ve veri tutarlılığı

`calisiyorMu=true` olan hedef için istek no-op'tur: `204` döner, persistence ve audit yan etkisi oluşturmaz. Aktif görünüp çıkış tarihi dolu olan tutarsız kayıt bu endpoint tarafından gizlice onarılmaz; ayrı veri düzeltme konusu olarak ele alınır.

Pasif hedefte aktifleştirme `isCikisTarihi` alanını `null` yapar. Önceki çalışma dönemlerini korumak gerekirse gelecekte ayrı bir istihdam geçmişi modeli tasarlanmalıdır.

## Authentication ve token davranışı

- Token yoksa/geçersizse `401`.
- JWT principal personeli bulunamazsa `404`.
- Principal pasifse `403`.
- Mevcut parola ve `parolaDegistirilmeliMi` değeri korunur.
- Aktifleştirme token üretmez. Kullanıcı normal login ile yeni token alır.
- Mevcut stateless access-token mimarisi değiştirilmez; `tokenVersion` bu görevin kapsamı dışındadır.

## Audit ve transaction

- Olay: `PERSONEL_AKTIFLESTIRILDI`
- Aktör: JWT principal personeli
- Hedef: aktifleştirilen personel
- Detay: `Personel aktifleştirildi.`
- No-op audit oluşturmaz.
- Aktiflik, çıkış tarihinin temizlenmesi ve audit aynı transaction'a katılır; dış hata hepsini rollback eder.

## Test tasarımı

Yeni odaklı `PersonelAktiflestirmeControllerIntegrationTest` sınıfı şu senaryoları kapsar:

- HR'ın pasif `PERSONEL` ve başka HR hedefini aktifleştirmesi.
- Admin'in pasif `PERSONEL`, HR ve `YONETICI` hedefini aktifleştirmesi.
- HR'ın `YONETICI`/`ADMIN`, Admin'in `ADMIN`, her iki rolün öz-hedef isteklerinde `403`.
- Pasif aktör `403`, bulunamayan principal/hedef `404`, tokensız istek `401`.
- Başarılı işlemde `calisiyorMu=true`, `isCikisTarihi=null`, boş `204` ve tek güvenli audit.
- Zaten aktif hedefte persistence/audit yan etkisiz `204`.
- Aktifleştirilen yöneticinin eski çalışanlarının otomatik geri bağlanmaması.
- Dış hatada personel ve audit rollback'i.
- Aktifleştirilen personelin aktif ekip dizininde yeniden görünmesi.

Regresyon sırası:

1. Yeni odaklı integration testleri.
2. `HrPersonelControllerIntegrationTest` ve `PersonelServiceTest`.
3. `EkipDiziniControllerIntegrationTest`.
4. Tam backend test paketi ve JaCoCo kontrolü.

## Değişecek dosyalar

Production:

- `backend/src/main/java/com/jforce/backend/controller/HrPersonelController.java`
- `backend/src/main/java/com/jforce/backend/service/PersonelService.java`
- `backend/src/main/java/com/jforce/backend/model/enums/AuditEylem.java`

Test:

- Yeni `backend/src/test/java/com/jforce/backend/PersonelAktiflestirmeControllerIntegrationTest.java`
- Yalnız gerekli regresyon boşluğu bulunursa mevcut ilgili test sınıfları

Belgeler:

- `API_SPEC.md`
- `ROADMAP.md`
- `SECURITY.md`
- `PROGRESS.md`

Java, test ve frontend dosyalarını agent doğrudan değiştirmez. Git komutu çalıştırılmaz.
