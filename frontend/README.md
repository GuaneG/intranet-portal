# Intranet Portal Frontend

Intranet Portal’ın React ve TypeScript arayüzüdür. TailAdmin görsel altyapısı ürün ekranlarına uyarlanmış; authentication, profil, ekip dizini, yönetim, tatil takvimi ve oda rezervasyonu akışları gerçek backend endpoint’lerine bağlanmıştır.

## Teknolojiler

- React 19 ve TypeScript
- Vite 6
- Tailwind CSS 4
- React Router 7
- FullCalendar 6
- ESLint

## Uygulanan akışlar

- Login, zorunlu ilk parola değişimi, sessiz refresh ve logout
- Role göre sidebar ve route koruması
- Profil bilgileri, yetenekler ve profil fotoğrafı
- Personel yönetimi, aktifleştirme/pasifleştirme, rol ve yetenek yönetimi
- Departman ve yetenek katalog yönetimi
- Filtrelenebilir ekip dizini ve role uygun organizasyon hiyerarşisi
- Dashboard ekip özeti
- Tatil takvimi listeleme ve HR/Admin tek/çok günlük tatil grubu oluşturma, güncelleme ve silme
- Takvimde şirket genelindeki aktif çalışanların güvenli doğum günü olayları
- İzin türü lookup, iş günü önizleme, izin talebi oluşturma ve kişinin kendi taleplerini listeleme
- Owner bekleyen talep iptali; Yönetici doğrudan ekip ve Admin şirket geneli izin kuyruğu ile onay/red işlemleri
- Yalnız Admin’e açık, tarih/eylem/aktör UUID/hedef UUID filtreli ve sayfalı audit log ekranı
- Bütün aktif roller için günlük oda uygunluk grid’i, rezervasyon oluşturma, kendi geçmişini görüntüleme ve başlamamış rezervasyonu iptal etme
- Admin için şirket geneli rezervasyon geçmişi ve yetkili iptal; şirket takviminde güvenli rezervasyon olayları ve dashboard oda doluluk kartı
- Aktif rezervasyonla çakışan tatil oluşturma/güncelleme isteğinde backend’in güvenli `409` mesajını form değerlerini koruyarak gösterme

## Bilinen kapsam

- Onaylı kişisel izinleri ve Yönetici takım izinlerini ürün takviminde ayrı olay türleri olarak gösterme henüz tamamlanmamıştır.
- HR/Admin izin türü yönetimi ile hizmet yılına göre hak tahakkuku ve devreden bakiye modeli gelecekte değerlendirilecek ayrı ürün işleridir; mevcut frontend bunları uygulanmış kabul etmez.
- Audit ekranındaki aktör ve hedef filtreleri şimdilik UUID ile çalışır; ad veya kullanıcı adıyla aranabilir seçim gelecekte eklenecektir.
- Ekipman, duyuru ve takdir panosu ürün akışları henüz tamamlanmamıştır.
- TailAdmin’den kalan bazı demo route, component ve asset’lerin temizliği devam etmektedir.

## Kurulum ve çalıştırma

Güncel bir Node.js LTS sürümü kullanın.

```powershell
npm install
npm run dev
```

Frontend geliştirme sunucusu varsayılan olarak `http://localhost:5173` adresindedir ve API için `http://localhost:8080` backend’ini kullanır.

## Komutlar

```powershell
npm run dev
npm run lint
npm run build
npm run preview
```

`package.json` içinde birleşik bir `test` script’i veya ayrı bir frontend test framework’ü henüz yoktur. `test/` klasöründeki hafif `node:test` dosyaları ilgili dosya doğrudan Node ile çağrılarak çalıştırılabilir.

## Kaynak yapısı

```text
src/
├── components/    # Ortak ve özellik bazlı UI parçaları
├── context/       # Authentication ve uygulama bağlamları
├── layout/        # Sidebar, header ve ana yerleşim
├── lib/           # API istemcisi ve yardımcılar
├── pages/         # Route seviyesindeki ürün ekranları
└── types/         # API/UI TypeScript sözleşmeleri
```

Backend endpoint ve rol sözleşmeleri için kökteki [API_SPEC.md](../API_SPEC.md), güvenlik kuralları için [SECURITY.md](../SECURITY.md) esas alınır.

## Lisans ve şablon kaynağı

Arayüz TailAdmin React Free başlangıç şablonundan türetilmiştir. Şablonun lisans metni [LICENSE.MD](LICENSE.MD) dosyasındadır.
