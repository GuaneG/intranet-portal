# Intranet Portal Katkı Rehberi

## 1. Amaç

Bu belge Intranet Portal repository’sinde bir değişikliğin geliştirilmesi, doğrulanması ve dokümante edilmesi için ortak kontrol listelerini tanımlar.

Agent’ların kullanıcıyla çalışma biçimi ve yetki sınırları kök `AGENTS.md` dosyasındadır. Backend ve frontend’e özgü agent talimatları kendi dizinlerindeki `AGENTS.md` dosyalarında bulunur.

Bu belgedeki geniş “tamamlanma” listeleri her küçük değişikliğe bütünüyle uygulanmaz. Kontroller değişikliğin kapsamına ve riskine göre seçilir.

## 2. Değişiklik kapsamı

Bir değişikliğe başlamadan önce:

- İstenen davranışı ve kabul ölçütünü netleştir.
- İlgili mevcut kodu ve belgeleri incele.
- Aynı sorumluluğu karşılayan mevcut component, service, repository veya yardımcı yapı olup olmadığını kontrol et.
- Değişiklik için gerekli dosyalarla sınırlı kal.
- İlgisiz refactor veya gelecekte gerekebilir düşüncesiyle altyapı ekleme.
- Mevcut kullanıcı değişikliklerini koru.
- Ürün veya mimari davranışı önemli ölçüde değiştiren belirsizlikleri uygulamadan önce netleştir.
- Küçük, güvenli ve geri alınabilir uygulama kararlarında gereksiz onay turu oluşturma.

## 3. Backend modülü tamamlanma kontrol listesi

Aşağıdaki liste tam bir backend özelliği veya modülü teslim edilirken kullanılır. Küçük bug fix, salt kod inceleme, araştırma veya dokümantasyon görevinde yalnız ilgili maddeler uygulanır.

### İş kuralları

- Use-case ve iş kuralları açıkça tanımlandı mı?
- Başarılı, hatalı ve sınır senaryoları belirlendi mi?
- Durum geçişleri ve izin verilen işlemler net mi?
- Kullanıcıdan gelen hesaplanmış veya yetkiye bağlı değerlere güveniliyor mu?
- Aynı iş kuralı birden fazla katmanda gereksiz biçimde tekrarlanıyor mu?

### API ve DTO sözleşmesi

- Request ve response DTO’ları endpoint/use-case ihtiyacına göre tasarlandı mı?
- Entity doğrudan API response’u olarak döndürülüyor mu?
- Request DTO yalnız istemcinin belirlemesine izin verilen alanları mı kabul ediyor?
- Jakarta Validation ile doğrulanabilecek biçimsel kurallar tanımlandı mı?
- Business validation service katmanında uygulanıyor mu?
- Status code ve hata response’u mevcut API yaklaşımıyla uyumlu mu?
- Endpoint sözleşmesi `API_SPEC.md` ile uyumlu mu?

### Katmanlar

- Controller yalnız HTTP sözleşmesi ve service koordinasyonuyla mı ilgileniyor?
- Controller içinde iş kuralı veya doğrudan repository erişimi var mı?
- Service use-case, iş kuralı, ownership ve transaction sınırlarını yönetiyor mu?
- Repository yalnız gerekli veri erişim işlemlerini mi içeriyor?
- Kullanılmayan repository metodu, interface veya ortak altyapı eklendi mi?
- Entity–DTO dönüşümünün yeri ve sorumluluğu açık mı?

### JPA ve transaction

- Entity ilişkisi ile yalnız ID referansı arasında bilinçli seçim yapıldı mı?
- Fetch stratejisi sorgu ve serialization davranışına uygun mu?
- Cascade veya orphan removal gerçek yaşam döngüsü ihtiyacına dayanıyor mu?
- Transaction sınırı service katmanında mı?
- Toplu update/delete işlemleri gerekli transaction içinde mi?
- Rollback veya bağımsız audit davranışı gerçek veritabanıyla doğrulanmalı mı?
- Test verisi ve constraint davranışı MySQL ile uyumlu mu?

### Yetkilendirme

- Endpoint’in kim tarafından kullanılabileceği belirli mi?
- Rol kontrolü backend’de uygulanıyor mu?
- Kaynağa özgü ownership kontrolü bulunuyor mu?
- Kullanıcının kimliği doğrulanmış security context üzerinden alınıyor mu?
- Frontend’den gelen rol veya personel ID’si güvenilir kabul ediliyor mu?
- İzin verilen rol senaryosuyla birlikte yasaklanan erişim senaryosu da değerlendirildi mi?

### Hata davranışı

- Beklenen business hataları kontrollü response’a dönüşüyor mu?
- Beklenmeyen exception, SQL, dosya yolu veya stack trace ayrıntısı sızıyor mu?
- 400, 401, 403, 404 ve 500 ayrımları doğru mu?
- Spring Security tarafından üretilen hata ile controller hata sözleşmesi uyumlu mu?
- Kullanıcıya gösterilen hata mesajı teknik ayrıntı yerine anlamlı bir açıklama içeriyor mu?

### Unit test

- Service veya domain davranışının başarılı senaryosu test edildi mi?
- Hata ve sınır senaryoları test edildi mi?
- Dış bağımlılık izolasyonu gerekiyorsa Mockito kullanıldı mı?
- Saf davranış testine gereksiz mock eklendi mi?
- Test yalnız metodun çağrılmasını değil, iş kuralının sonucunu doğruluyor mu?
- Gönderilen parametre önemliyse gerekli durumda `ArgumentCaptor` ile kontrol edildi mi?
- Test implementation ayrıntısına gereksiz biçimde bağlanıyor mu?

### Integration test

Aşağıdaki davranışlardan biri önemliyse mevcut MySQL 8 Testcontainers ve `AbstractIntegrationTest` altyapısı değerlendirilmelidir:

- repository sorgusu,
- JPA mapping veya constraint,
- transaction ve rollback,
- Spring Security filter chain,
- HTTP status ve response şekli,
- rol veya ownership,
- refresh-token rotation/reuse,
- audit persistence,
- MySQL’e özgü davranış.

Kontroller:

- Test gerçek riski doğruluyor mu?
- Test verileri diğer testlere sızıyor mu?
- Aynı container veya Spring yapılandırması gereksiz biçimde tekrarlandı mı?
- Yalnız context’in açılması, gerçek davranış testi gibi sunuluyor mu?

### Backend dokümantasyonu

- Yeni veya değişen endpoint `API_SPEC.md` içinde güncel mi?
- Rol ve ownership davranışı dokümante edildi mi?
- Authentication veya token davranışı değiştiyse `SECURITY.md` güncellendi mi?
- Tamamlanan çalışma `PROGRESS.md` ile çelişiyor mu?
- Doküman, planlanan davranışı uygulanmış gibi gösteriyor mu?

## 4. Frontend özelliği tamamlanma kontrol listesi

Aşağıdaki liste tam bir frontend özelliği veya ekranı teslim edilirken kullanılır. Küçük görsel düzeltmelerde yalnız ilgili maddeler uygulanır.

### Ürün ve component yapısı

- Ekranın kullanıcı açısından amacı açık mı?
- Route ve component adları ürün alanını mı anlatıyor?
- Kullanılabilecek mevcut TailAdmin component’ı incelendi mi?
- Component sorumluluklarına göre anlamlı biçimde ayrıldı mı?
- Gereksiz küçük component veya global abstraction oluşturuldu mu?
- Aynı state veya API davranışı birden fazla yerde kopyalandı mı?

### TailAdmin dönüşümü

- Demo component doğrudan silinmeden önce ürüne dönüştürülebilirliği değerlendirildi mi?
- Demo metinleri ve örnek veriler gerçek ürün verisine dönüştürüldü mü?
- Dönüşüm tamamlandıktan sonra eski route, import ve asset kullanımları kontrol edildi mi?
- İlgisiz demo alanları değişiklik kapsamına alındı mı?
- Component’ın başka sayfalarda kullanımı silmeden veya yeniden adlandırmadan önce kontrol edildi mi?

### API ve veri akışı

- Endpoint mevcut backend veya `API_SPEC.md` sözleşmesiyle eşleşiyor mu?
- Authenticated çağrıda mevcut `src/lib/api.js` yaklaşımı kullanılıyor mu?
- Refresh-token akışı component içinde gereksiz biçimde tekrarlandı mı?
- API verisinin component’a ulaşma yolu anlaşılır mı?
- Hesaplanmış veya yetkiye bağlı değer frontend kararı olarak güvenilir kabul ediliyor mu?
- Backend response’unda bulunmayan bir alan tahmin edildi mi?

### UI durumları

- İlk loading durumu var mı?
- Başarılı ve dolu response gösteriliyor mu?
- Başarılı fakat boş response için anlamlı empty state var mı?
- Hata durumu kullanıcıya anlaşılır biçimde gösteriliyor mu?
- Authentication sona erdiğinde mevcut yönlendirme davranışı korunuyor mu?
- Tekrarlanan gönderimi engellemek gereken işlem durumu var mı?
- Disabled kontrol yalnız görsel değil davranışsal olarak da uygulanıyor mu?

### Frontend güvenliği

- Kullanıcı veya API metni normal React escaping ile gösteriliyor mu?
- `dangerouslySetInnerHTML` gerçekten gerekli mi?
- Hassas token veya kullanıcı bilgisi console’a yazılıyor mu?
- Menü veya buton gizleme backend yetkilendirmesi gibi sunuluyor mu?
- Authentication saklama veya refresh yaklaşımı yalnız frontend kararıyla değiştiriliyor mu?
- Backend hata nesnesinin tamamı kontrolsüz biçimde kullanıcıya gösteriliyor mu?

### Kullanılabilirlik ve erişilebilirlik

- Form alanlarında label ve anlaşılır validation mesajı var mı?
- Etkileşim yalnız renk üzerinden anlatılıyor mu?
- Tıklanabilir kontrol için uygun HTML elemanı kullanılıyor mu?
- Görsellerde doğru `alt` davranışı var mı?
- Klavye ile kullanım bozuluyor mu?
- Mobil ve masaüstü görünüm mevcut layout içinde kontrol edildi mi?

### Frontend doğrulaması

Repository’de mevcut frontend kontrolleri:

- `npm run lint`
- `npm run build`

Repository’de şu anda frontend otomatik test komutu veya test framework’ü bulunmamaktadır.

Bu nedenle:

- test çalıştırılmadıysa “frontend testleri geçti” denmemelidir,
- açık karar olmadan yeni test dependency’si eklenmemelidir,
- otomatik frontend test eksikliği ilgili değişiklikte kalan riskse belirtilmelidir,
- build veya lint çalıştırılmadıysa çalıştırılmış gibi raporlanmamalıdır.

## 5. Güvenlik değişiklikleri

Authentication, authorization, token, audit veya dosya yükleme değişikliklerinde önce `SECURITY.md` incelenmelidir.

Kontrol edilmesi gerekenler:

- Değişiklik mevcut access/refresh token sorumluluğunu etkiliyor mu?
- Cookie veya token saklama davranışı değişiyor mu?
- Rol veya ownership kontrolü backend’de uygulanıyor mu?
- Yeni bir audit olayı gerekli mi?
- Hassas veri loglanıyor veya response’a sızıyor mu?
- Olumlu senaryoyla birlikte saldırı veya yetkisiz erişim senaryosu değerlendirildi mi?
- Mevcut güvenlik açığı veya planlanan iyileştirme uygulanmış gibi gösteriliyor mu?

Yeni security dependency, tarama aracı, storage servisi veya token iptal altyapısı ayrı karar alınmadan eklenmemelidir.

## 6. Dokümantasyon tutarlılığı

Repository’deki mevcut temel belgeler:

- `README.md`: genel proje yapısı ve kurulum
- `ROADMAP.md`: onaylanmış ürün kararları, öncelikler ve gelecek görevler
- `API_SPEC.md`: endpoint ve rol sözleşmeleri
- `PROGRESS.md`: tamamlanan çalışmalar ve öğrenme notları
- `SECURITY.md`: mevcut güvenlik mimarisi ve planlanan güvenlik gereksinimleri
- `CONTRIBUTING.md`: geliştirme ve tamamlanma kontrol listeleri
- `AGENTS.md`: agent çalışma sözleşmesi

Dokümantasyon güncellenirken:

- Planlanan özellikleri tamamlanmış özelliklerden ayır.
- Kodla çelişen eski açıklamayı sessizce bırakma.
- Görev kapsamındaki belgeyi güncelle.
- Kapsam dışındaki gerekli belge değişikliğini sonuçta açıkça bildir.
- Aynı teknik kararı birden fazla belgede ayrıntılı biçimde tekrar etme.
- Uzun güvenlik gerekçelerini `SECURITY.md`, API sözleşmelerini `API_SPEC.md` içinde tut.



## 7. Değişiklik teslim özeti

Bir değişikliğin sonunda kısa biçimde şunlar belirtilmelidir:

- Ne değişti?
- Neden değişti?
- Hangi dosyalar etkilendi?
- Nasıl doğrulandı?
- Hangi kontroller çalıştırılmadı?
- Kalan risk veya sonraki adım var mı?
- Dokümantasyon güncellendi mi?

Doğrulama yapılmadan “tamamlandı”, “çalışıyor”, “testler geçti”, “lint temiz” veya “build başarılı” denmemelidir.

## 8. Commit standardı

Bu bölüm insan geliştiriciler için commit standardını tanımlar. Agent’ın git kullanma yetkisi kök `AGENTS.md` tarafından ayrıca sınırlandırılır.

Proje [Conventional Commits](https://www.conventionalcommits.org/) biçimini kullanır:

```text
<type>(<scope>): <description>
```

### Türler

- `feat`
- `fix`
- `refactor`
- `test`
- `docs`
- `chore`
- `style`

### Scope değerleri

- `auth`
- `user`
- `leave`
- `approval`
- `equipment`
- `reservation`
- `announcement`
- `dashboard`
- `frontend`
- `db`

### Örnekler

```text
feat(auth): add JWT token generation
fix(reservation): prevent overlapping bookings
chore: update maven dependencies
```

Git komutları agent tarafından çalıştırılmaz. Gerekli commit veya branch adımları kullanıcıya açıklanır ve kullanıcı tarafından yürütülür.