package com.jforce.backend.service;

import com.jforce.backend.exception.GecersizIstekException;
import com.jforce.backend.exception.GecersizParolaException;
import com.jforce.backend.exception.KaynakBulunamadiException;
import com.jforce.backend.exception.KaynakCakismasiException;
import com.jforce.backend.model.dto.request.ParolaDegistirRequest;
import com.jforce.backend.model.dto.request.PersonelGuncelleRequest;
import com.jforce.backend.model.dto.request.PersonelOlusturRequest;
import com.jforce.backend.model.dto.response.PersonelOlusturResponse;
import com.jforce.backend.model.entity.Departman;
import com.jforce.backend.model.entity.DurumTuru;
import com.jforce.backend.model.entity.EkipmanTalep;
import com.jforce.backend.model.entity.Personel;
import com.jforce.backend.model.entity.Rol;
import com.jforce.backend.model.enums.AuditEylem;
import com.jforce.backend.model.enums.RolAdi;
import com.jforce.backend.model.enums.TalepDurum;
import com.jforce.backend.repository.DepartmanRepository;
import com.jforce.backend.repository.DurumTuruRepository;
import com.jforce.backend.repository.EkipmanTalepRepository;
import com.jforce.backend.repository.PersonelRepository;
import com.jforce.backend.repository.RolRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.w3c.dom.stylesheets.LinkStyle;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Year;
import java.time.Clock;
import java.util.*;

@Service
public class PersonelService {
    private final PersonelRepository personelRepository;
    private final RolRepository rolRepository;
    private final RefreshTokenService refreshTokenService;
    private final AuditService auditService;
    private final DepartmanRepository departmanRepository;
    private final ParolaService parolaService;
    private final PersonelYonetimYetkiService personelYonetimYetkiService;
    private final RezervasyonService rezervasyonService;
    private final EkipmanTalepRepository ekipmanTalepRepository;
    private final DurumTuruRepository durumTuruRepository;
    private final Clock clock;

    public PersonelService(PersonelRepository personelRepository, RolRepository rolRepository, RefreshTokenService refreshTokenService, AuditService auditService, DepartmanRepository departmanRepository, ParolaService parolaService, PersonelYonetimYetkiService personelYonetimYetkiService, RezervasyonService rezervasyonService, EkipmanTalepRepository ekipmanTalepRepository, DurumTuruRepository durumTuruRepository, Clock clock) {
        this.personelRepository = personelRepository;
        this.rolRepository = rolRepository;
        this.refreshTokenService = refreshTokenService;
        this.auditService = auditService;
        this.departmanRepository = departmanRepository;
        this.parolaService = parolaService;
        this.personelYonetimYetkiService = personelYonetimYetkiService;
        this.rezervasyonService = rezervasyonService;
        this.ekipmanTalepRepository = ekipmanTalepRepository;
        this.durumTuruRepository = durumTuruRepository;
        this.clock = clock;
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public void rolDegistir(String aktorId, String hedefPersonelId, RolAdi yeniRol) {
        Personel actorPersonel = personelYonetimYetkiService.aktifAktoruGetir(aktorId);

        Personel hedefPersonel = personelRepository.findByIdForUpdate(hedefPersonelId).orElseThrow(() -> new KaynakBulunamadiException("Personel id not found."));

        personelYonetimYetkiService.hedefiDogrula(actorPersonel, hedefPersonel);

        RolAdi eskiRol = hedefPersonel.getRol().getRolAdi();

        if (eskiRol == yeniRol) {
            return;
        }

        if (yeniRol == RolAdi.ADMIN) {
            throw new GecersizIstekException("Yeni rol admin olamaz.");
        }

        Optional<Rol> yeniRolObjesi = rolRepository.findByRolAdi(yeniRol);

        if ((eskiRol == RolAdi.PERSONEL || eskiRol == RolAdi.HR)
                && (yeniRol == RolAdi.YONETICI || yeniRol == RolAdi.ADMIN)) {
            bekleyenEkipmanTalepleriniIptalEt(
                    hedefPersonel,
                    actorPersonel,
                    LocalDateTime.now(clock)
            );
        }

        hedefPersonel.setRol(yeniRolObjesi.orElseThrow(() -> new KaynakBulunamadiException("Rol değiştirme başarısız.")));
        refreshTokenService.revokeAllForPersonel(hedefPersonel);
        auditService.logKaydetOrtakTransaction(AuditEylem.ROL_DEGISTIRILDI, actorPersonel, actorPersonel.getKullaniciAdi(), hedefPersonel.getKullaniciAdi()
                        + " ,Rol değişikliği: " + eskiRol + " -> " + yeniRol, hedefPersonel);
    }

    //----HELPER METHODLAR----

    private void yoneticiDogrula(Personel yonetici, Departman departman, String hedefPersonelId) {
        if (hedefPersonelId != null && Objects.equals(yonetici.getPersonelId(), hedefPersonelId)) {
            throw new GecersizIstekException("Personel kendisinin yöneticisi olamaz.");
        }

        if (!Boolean.TRUE.equals(yonetici.getCalisiyorMu())) {
            throw new GecersizIstekException("Pasif personel yönetici olarak atanamaz.");
        }

        if (yonetici.getRol().getRolAdi() != RolAdi.YONETICI) {
            throw new GecersizIstekException("Seçilen personelin yönetici rolü bulunmuyor.");
        }

        if (!Objects.equals(yonetici.getDepartman().getDepartmanId(), departman.getDepartmanId())) {
            throw new GecersizIstekException("Yönetici ve personel aynı departmanda olmalıdır.");
        }

        yoneticiZincirindeDonguDogrula(yonetici, hedefPersonelId);
    }

    private void bekleyenEkipmanTalepleriniIptalEt(
            Personel hedef,
            Personel aktor,
            LocalDateTime islemZamani
    ) {
        List<EkipmanTalep> talepler = ekipmanTalepRepository
                .findPendingByPersonelForUpdate(
                        hedef.getPersonelId(),
                        TalepDurum.ONAY_BEKLIYOR
                );
        if (talepler.isEmpty()) {
            return;
        }

        DurumTuru iptalDurumu = durumTuruRepository
                .findByDurumKodu(TalepDurum.IPTAL_EDILDI)
                .orElseThrow(() -> new KaynakBulunamadiException(
                        "Talep durumu bulunamadı."
                ));

        for (EkipmanTalep talep : talepler) {
            talep.setTalepDurum(iptalDurumu);
            talep.setIslemYapanId(aktor);
            talep.setIslemTarihi(islemZamani);
            talep.setKararNotu(null);
            auditService.logKaydetOrtakTransaction(
                    AuditEylem.EKIPMAN_TALEBI_IPTAL_EDILDI,
                    aktor,
                    aktor.getKullaniciAdi(),
                    "Ekipman talebi iptal edildi.",
                    hedef
            );
        }
    }

    private void yoneticiZincirindeDonguDogrula(Personel yonetici, String hedefPersonelId) {
        if (hedefPersonelId == null) {
            return;
        }

        Set<String> ziyaretEdilenPersonelIdleri = new HashSet<>();
        Personel mevcut = yonetici;

        while (mevcut != null) {
            String mevcutPersonelId = mevcut.getPersonelId();
            if (Objects.equals(mevcutPersonelId, hedefPersonelId)
                    || !ziyaretEdilenPersonelIdleri.add(mevcutPersonelId)) {
                throw new GecersizIstekException("Yönetici ataması döngü oluşturamaz.");
            }
            mevcut = mevcut.getYonetici();
        }
    }

    private String ePostayiNormalizeEt(String ePosta) {
        if (ePosta == null) {
            throw new GecersizIstekException("E-posta zorunludur.");
        }
        return ePosta.trim().toLowerCase(Locale.ROOT);
    }

    private String nullableMetniNormalizeEt(String deger, String alanAdi) {
        if (deger == null) {
            return null;
        }
        String temizlenmisDeger = deger.trim();
        if (temizlenmisDeger.isEmpty()) {
            throw new GecersizIstekException(alanAdi + " boş metin olarak gönderilemez. Temizlemek için null gönderilmelidir.");
        }
        return temizlenmisDeger;
    }

    @Transactional
    @PreAuthorize("hasAnyRole('HR', 'ADMIN')")
    public PersonelOlusturResponse personelOlustur(String aktorId, PersonelOlusturRequest request){
        //işlem yapan personeli al
        Personel aktor = personelYonetimYetkiService.aktifAktoruGetir(aktorId);
        //req'de gelen kullanici adını ve epostayı normalize et
        String kullaniciAdi = parolaService.kullaniciAdiniNormalizeEt(request.kullaniciAdi());
        String ePosta = ePostayiNormalizeEt(request.ePosta());

        //çakışma kontrolleri
        if (personelRepository.existsByKullaniciAdiIgnoreCase(kullaniciAdi)) {
            throw new KaynakCakismasiException("Bu kullanıcı adı zaten kullanılıyor.");
        }
        if (personelRepository.existsByEPostaIgnoreCase(ePosta)) {
            throw new KaynakCakismasiException("Bu e-posta zaten kullanılıyor.");
        }

        //kaynak kontrolleri
        Departman departman = departmanRepository.findById(request.departmanId()).orElseThrow(() -> new KaynakBulunamadiException("Departman bulunamadı."));
        Rol personelRolu = rolRepository.findByRolAdi(RolAdi.PERSONEL).orElseThrow(() -> new KaynakBulunamadiException("Personel rolü bulunamadı."));

        //yönetici initialize et
        Personel yonetici = null;
        //yönetici id opsiyonel olduğundan eğer yönetici geldiyse set et
        if (request.yoneticiId() != null && !request.yoneticiId().isBlank()) {
            yonetici = personelRepository.findById(request.yoneticiId()).orElseThrow(() -> new KaynakBulunamadiException("Yönetici bulunamadı."));
            //yöneticimi kontrol
            yoneticiDogrula(yonetici, departman, null);
        }
        //req'den gelenleri personel objesine set et
        Personel yeniPersonel = new Personel();
        yeniPersonel.setKullaniciAdi(kullaniciAdi);
        yeniPersonel.setAdi(request.ad().trim());
        yeniPersonel.setSoyadi(request.soyad().trim());
        yeniPersonel.setDogumTarihi(request.dogumTarihi());
        yeniPersonel.setePosta(ePosta);
        yeniPersonel.setDepartman(departman);
        yeniPersonel.setYonetici(yonetici);
        yeniPersonel.setRol(personelRolu);
        yeniPersonel.setIsGirisTarihi(request.isGirisTarihi());

        //ilk oluşturulan parola olusturulan normalize edilmiş kullanici adı ile aynıdır
        yeniPersonel.setParolaHash(parolaService.hashle(kullaniciAdi));
        // Yeni personel ilk girişte parolasını değiştirmek zorundadır.
        yeniPersonel.setParolaDegistirilmeliMi(true);

        //def değerler verildi
        yeniPersonel.setCalisiyorMu(true);
        yeniPersonel.setProfilFoto("def_pfp.png");
        yeniPersonel.setYillikIzinHakki(14);
        yeniPersonel.setIsCikisTarihi(null);

        Personel kaydedilenPersonel;

        try {                                       //saveandflush inserti direkt çağırır, buda exception'u doğru yerde yakalamamızı sağlar
            kaydedilenPersonel = personelRepository.saveAndFlush(yeniPersonel);
        } catch (DataIntegrityViolationException exception) {
            throw new KaynakCakismasiException("Kullanıcı adı veya e-posta zaten kullanılıyor.");
        }

        auditService.logKaydetOrtakTransaction(AuditEylem.PERSONEL_EKLENDI,
                aktor, aktor.getKullaniciAdi(), kullaniciAdi + " kullanıcısı oluşturuldu.", kaydedilenPersonel);

        return new PersonelOlusturResponse(kaydedilenPersonel.getPersonelId(), kaydedilenPersonel.getKullaniciAdi());
    }

    @Transactional
    @PreAuthorize("hasAnyRole('HR', 'ADMIN')")
    public void personelGuncelle(String aktorId, String hedefPersonelId, PersonelGuncelleRequest request) {
        if (!request.herhangiBirAlanGonderildiMi()) {
            throw new GecersizIstekException("Güncellenecek en az bir alan gönderilmelidir.");
        }

        Personel aktor = personelYonetimYetkiService.aktifAktoruGetir(aktorId);
        Personel hedefPersonel = personelRepository.findById(hedefPersonelId).orElseThrow(() -> new KaynakBulunamadiException("Güncellenecek personel bulunamadı."));

        personelYonetimYetkiService.hedefiDogrula(aktor, hedefPersonel);

        List<String> degisenAlanlar = new ArrayList<>();

        //request de değiştirme isteği varmı
        if (request.isAdGonderildiMi()) {
            //varsa boşmu çünkü boş istek atarsak bunu silme olarak kabul edeceğiz -> ama personel.ad objesi null olamaz NOTNULL constaraint!
            if (request.getAd() == null || request.getAd().isBlank()) {
                throw new GecersizIstekException("Personel adı boş olamaz.");
            }

            String yeniAd = request.getAd().trim();
            //yeni gelen ad ile eski gelen ad aynı mı
            if (!Objects.equals(hedefPersonel.getAdi(), yeniAd)) {
                hedefPersonel.setAdi(yeniAd);
                degisenAlanlar.add("ad");
            }
        }

        if (request.isSoyadGonderildiMi()) {
            if (request.getSoyad() == null || request.getSoyad().isBlank()) {
                throw new GecersizIstekException("Personel soyadı boş olamaz.");
            }

            String yeniSoyad = request.getSoyad().trim();

            if (!Objects.equals(hedefPersonel.getSoyadi(), yeniSoyad)) {
                hedefPersonel.setSoyadi(yeniSoyad);
                degisenAlanlar.add("soyad");
            }
        }

        if (request.isDogumTarihiGonderildiMi()) {
            if (request.getDogumTarihi() == null) {
                throw new GecersizIstekException("Doğum tarihi boş olamaz.");
            }

            if (!Objects.equals(hedefPersonel.getDogumTarihi(), request.getDogumTarihi())) {
                hedefPersonel.setDogumTarihi(request.getDogumTarihi());
                degisenAlanlar.add("dogumTarihi");
            }
        }

        if (request.isIsGirisTarihiGonderildiMi()) {
            if (request.getIsGirisTarihi() == null) {
                throw new GecersizIstekException("İşe giriş tarihi boş olamaz.");
            }

            if (!Objects.equals(hedefPersonel.getIsGirisTarihi(), request.getIsGirisTarihi())) {
                hedefPersonel.setIsGirisTarihi(request.getIsGirisTarihi());
                degisenAlanlar.add("isGirisTarihi");
            }
        }

        if (request.isePostaGonderildiMi()) {
            if (request.getePosta() == null || request.getePosta().isBlank()) {
                throw new GecersizIstekException("E-posta boş olamaz.");
            }

            String yeniEPosta = ePostayiNormalizeEt(request.getePosta());

            if (!Objects.equals(hedefPersonel.getePosta(), yeniEPosta)) {
                if (personelRepository.existsByEPostaIgnoreCaseAndPersonelIdNot(yeniEPosta, hedefPersonelId)) {
                    throw new KaynakCakismasiException("Bu e-posta başka bir personel tarafından kullanılıyor."
                    );
                }
                hedefPersonel.setePosta(yeniEPosta);
                degisenAlanlar.add("ePosta");
            }
        }

        if (request.isTelNoGonderildiMi()) {
            String yeniTelNo = nullableMetniNormalizeEt(request.getTelNo(), "Telefon numarası"
            );

            if (!Objects.equals(hedefPersonel.getTelNo(), yeniTelNo)) {
                hedefPersonel.setTelNo(yeniTelNo);
                degisenAlanlar.add("telNo");
            }
        }

        if (request.isMezunOkulGonderildiMi()) {
            String yeniMezunOkul = nullableMetniNormalizeEt(request.getMezunOkul(), "Mezun olunan okul");

            if (!Objects.equals(hedefPersonel.getMezunOkul(), yeniMezunOkul)) {
                hedefPersonel.setMezunOkul(yeniMezunOkul);
                degisenAlanlar.add("mezunOkul");
            }
        }

        if (request.isMezunBolumGonderildiMi()) {
            String yeniMezunBolum = nullableMetniNormalizeEt(request.getMezunBolum(), "Mezun olunan bölüm");

            if (!Objects.equals(hedefPersonel.getMezunBolum(), yeniMezunBolum)) {
                hedefPersonel.setMezunBolum(yeniMezunBolum);
                degisenAlanlar.add("mezunBolum");
            }
        }

        if (request.isMezuniyetYiliGonderildiMi()) {
            Integer yeniMezuniyetYili = request.getMezuniyetYili();

            if (yeniMezuniyetYili != null && (yeniMezuniyetYili <= 0 || yeniMezuniyetYili > Year.now().getValue())) {
                throw new GecersizIstekException("Mezuniyet yılı geçerli bir yıl olmalıdır.");
            }

            if (!Objects.equals(hedefPersonel.getMezuniyetYil(), yeniMezuniyetYili)) {
                hedefPersonel.setMezuniyetYil(yeniMezuniyetYili);
                degisenAlanlar.add("mezuniyetYili");
            }
        }

        // Departman veya yönetici alanlarından biri gönderilirse ilişki kurallarını değerlendir.
        // Departman gerçekten değişiyorsa yoneticiId alanı da request'te bulunmalıdır.
        // Yönetici mevcut departman içinde tek başına değiştirilebilir
        // veya null gönderilerek kaldırılabilir.
        if (request.isDepartmanIdGonderildiMi() || request.isYoneticiIdGonderildiMi()) {

            //anlık olduğu departman
            Departman etkiliDepartman = hedefPersonel.getDepartman();
            //departman değiştimi kontrol için initialize
            boolean departmanDegisti = false;

            if (request.isDepartmanIdGonderildiMi()) {
                //departman güncelleme isteği boşmu?
                if (request.getDepartmanId() == null) {
                    throw new GecersizIstekException("Departman boş olamaz.");
                }
                //değil yeni departmanı al
                Departman yeniDepartman = departmanRepository.findById(request.getDepartmanId()).orElseThrow(() -> new KaynakBulunamadiException("Departman bulunamadı."));
                //yeni departman eskiye eşitmi kontrol eşitse flag false kalıcak
                departmanDegisti = !Objects.equals(hedefPersonel.getDepartman().getDepartmanId(), yeniDepartman.getDepartmanId());
                //departman değişcekse yöneticide değişmeli
                if (departmanDegisti && !request.isYoneticiIdGonderildiMi()) {
                    throw new GecersizIstekException("Departman değiştirilirken yönetici alanı da gönderilmelidir.");
                }
                //if bloğuna girmedi demekki hem departman hem yönetici değişiyor değiştir.
                etkiliDepartman = yeniDepartman;
            }

            Personel yeniYonetici = hedefPersonel.getYonetici();
            //yönetici değiştirilcekmi
            if (request.isYoneticiIdGonderildiMi()) {
                //değişcekse null mu
                if (request.getYoneticiId() == null) {
                    yeniYonetici = null;
                } else {
                    //değilse boşmu kontrol
                    if (request.getYoneticiId().isBlank()) {
                        throw new GecersizIstekException("Yönetici ID boş metin olamaz. Yöneticiyi kaldırmak için null gönderilmelidir.");
                    }
                    //yeni yönetici al
                    yeniYonetici = personelRepository.findById(request.getYoneticiId()).orElseThrow(() -> new KaynakBulunamadiException("Yönetici bulunamadı."));
                    //yöneticiyi kontrol et
                    yoneticiDogrula(yeniYonetici, etkiliDepartman, hedefPersonelId);
                }
            }

            if (departmanDegisti) {
                hedefPersonel.setDepartman(etkiliDepartman);
                degisenAlanlar.add("departmanId");
            }

            if (request.isYoneticiIdGonderildiMi()) {
                //yönetici isteği null gelirse yöneticiyi kaldır, değilse yeni yöneticiyi ata
                String mevcutYoneticiId = hedefPersonel.getYonetici() == null ? null : hedefPersonel.getYonetici().getPersonelId();
                String yeniYoneticiId = yeniYonetici == null ? null : yeniYonetici.getPersonelId();

                //yöneticiler aynı değilse değiştir
                if (!Objects.equals(mevcutYoneticiId, yeniYoneticiId)) {
                    hedefPersonel.setYonetici(yeniYonetici);
                    degisenAlanlar.add("yoneticiId");
                }
            }
        }
        //değişen alan varmı bak
        if (degisenAlanlar.isEmpty()) {
            return;
        }

        try {
            //yapılan bütün değişiklikleri kaydet
            personelRepository.saveAndFlush(hedefPersonel);
        } catch (DataIntegrityViolationException exception) {
            throw new KaynakCakismasiException("Bu e-posta başka bir personel tarafından kullanılıyor.");
        }

        auditService.logKaydetOrtakTransaction(AuditEylem.PERSONEL_GUNCELLENDI, aktor, aktor.getKullaniciAdi(), "Güncellenen alanlar: " + String.join(", ", degisenAlanlar), hedefPersonel);

    }

    @Transactional
    @PreAuthorize("hasAnyRole('HR', 'ADMIN')")
    public void personelAktiflestir(
            String aktorId,
            String hedefPersonelId
    ) {
        Personel aktor =
                personelYonetimYetkiService
                        .aktifAktoruGetir(aktorId);

        Personel hedef = personelRepository
                .findById(hedefPersonelId)
                .orElseThrow(
                        () -> new KaynakBulunamadiException(
                                "Personel bulunamadı."
                        )
                );

        personelYonetimYetkiService.hedefiDogrula(
                aktor,
                hedef
        );

        if (Boolean.TRUE.equals(
                hedef.getCalisiyorMu()
        )) {
            return;
        }

        hedef.setCalisiyorMu(true);
        hedef.setIsCikisTarihi(null);
        auditService.logKaydetOrtakTransaction(
                AuditEylem.PERSONEL_AKTIFLESTIRILDI,
                aktor,
                aktor.getKullaniciAdi(),
                "Personel aktifleştirildi.",
                hedef
        );
    }

    @Transactional
    @PreAuthorize("hasAnyRole('HR', 'ADMIN')")
    public void personelPasiflestir(String aktorId, String hedefPersonelId) {
        Personel aktor = personelYonetimYetkiService.aktifAktoruGetir(aktorId);

        Personel hedef = personelRepository.findByIdForUpdate(hedefPersonelId).orElseThrow(() -> new KaynakBulunamadiException("Personel bulunamadı."));

        personelYonetimYetkiService.hedefiDogrula(aktor, hedef);
        RolAdi hedefRol = hedef.getRol().getRolAdi();

        if (!Boolean.TRUE.equals(hedef.getCalisiyorMu())) {
            return;
        }

        bekleyenEkipmanTalepleriniIptalEt(
                hedef,
                aktor,
                LocalDateTime.now(clock)
        );

        int yoneticisiKaldirilanPersonelSayisi = 0;

        if (hedefRol == RolAdi.YONETICI) {
            List<Personel> dogrudanCalisanlar = personelRepository.findAllByYonetici(hedef);
            dogrudanCalisanlar.forEach(personel -> personel.setYonetici(null));
            yoneticisiKaldirilanPersonelSayisi = dogrudanCalisanlar.size();
        }

        int iptalEdilenRezervasyonSayisi =
                rezervasyonService.personelPasiflestirmeRezervasyonlariniIptalEt(
                        aktor,
                        hedef
                );

        hedef.setCalisiyorMu(false);
        hedef.setIsCikisTarihi(LocalDate.now());

        refreshTokenService.revokeAllForPersonel(hedef);

        auditService.logKaydetOrtakTransaction(
                AuditEylem.PERSONEL_PASIFE_ALINDI,
                aktor,
                aktor.getKullaniciAdi(),
                "Personel pasife alındı. "
                        + "Yöneticisi kaldırılan personel sayısı: "
                        + yoneticisiKaldirilanPersonelSayisi
                        + ". İptal edilen rezervasyon sayısı: "
                        + iptalEdilenRezervasyonSayisi,
                hedef
        );
    }



}
