package com.jforce.backend.service;

import com.jforce.backend.exception.GecersizIstekException;
import com.jforce.backend.exception.KaynakBulunamadiException;
import com.jforce.backend.model.dto.request.ProfilUpdateRequest;
import com.jforce.backend.model.dto.response.LookUpResponse;
import com.jforce.backend.model.dto.response.ProfilBilgileriResponse;
import com.jforce.backend.model.entity.Personel;
import com.jforce.backend.model.enums.AuditEylem;
import com.jforce.backend.model.enums.RolAdi;
import com.jforce.backend.model.enums.TalepDurum;
import com.jforce.backend.repository.IzinBilgileriRepository;
import com.jforce.backend.repository.PersonelRepository;
import com.jforce.backend.repository.PersonelYetenekRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.jforce.backend.model.dto.request.YetenekUpdateRequest;
import com.jforce.backend.model.entity.PersonelYetenek;
import com.jforce.backend.model.entity.PersonelYetenekId;
import com.jforce.backend.model.entity.Yetenek;
import com.jforce.backend.repository.YetenekRepository;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.math.BigDecimal;

@Service
public class ProfilService {

    private final PersonelRepository personelRepository;
    private final PersonelYetenekRepository personelYetenekRepository;
    private final IzinBilgileriRepository izinBilgileriRepository;
    private final AuditService auditService;
    private final YetenekRepository yetenekRepository;
    private final PersonelYonetimYetkiService personelYonetimYetkiService;

    public ProfilService(
            PersonelRepository personelRepository,
            PersonelYetenekRepository personelYetenekRepository,
            IzinBilgileriRepository izinBilgileriRepository,
            AuditService auditService,
            YetenekRepository yetenekRepository,
            PersonelYonetimYetkiService personelYonetimYetkiService
    ) {
        this.personelRepository = personelRepository;
        this.personelYetenekRepository = personelYetenekRepository;
        this.izinBilgileriRepository = izinBilgileriRepository;
        this.auditService = auditService;
        this.yetenekRepository = yetenekRepository;
        this.personelYonetimYetkiService = personelYonetimYetkiService;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("isAuthenticated()")
    public ProfilBilgileriResponse profilimiGetir(String personelId) {

        Personel personel = personelRepository.findById(personelId).orElseThrow(() -> new KaynakBulunamadiException("Personel bulunamadı."));

        List<LookUpResponse> yetenekler = personelYetenekRepository.findAllByPersonelIdWithYetenek(personelId)
                .stream()
                .map(personelYetenek -> new LookUpResponse(
                        personelYetenek.getYetenek().getYetenekId(),
                        personelYetenek.getYetenek().getYetenekAdi()
                ))
                .toList();

        RolAdi rolAdi = personel.getRol().getRolAdi();

        BigDecimal kalanIzin = null;

        if (rolAdi == RolAdi.PERSONEL || rolAdi == RolAdi.HR) {
            BigDecimal kullanilanIzin = izinBilgileriRepository.onaylanmisIzinIsGunuToplami(personelId,TalepDurum.ONAYLANDI);

            kalanIzin = BigDecimal.valueOf(personel.getYillikIzinHakki()).subtract(kullanilanIzin).setScale(1);
        }

        String yonetici = personel.getYonetici() == null ? null : personel.getYonetici().getAdi() + " " + personel.getYonetici().getSoyadi();

        return new ProfilBilgileriResponse(
                personel.getKullaniciAdi(),
                personel.getAdi(),
                personel.getSoyadi(),
                personel.getDogumTarihi(),
                personel.getePosta(),
                personel.getSicilNumarasi(),
                personel.getDepartman().getDepartmanAdi(),
                rolAdi.name(),
                yonetici,
                personel.getYillikIzinHakki(),
                personel.getProfilFoto(),
                kalanIzin,
                personel.getMezunOkul(),
                personel.getMezunBolum(),
                personel.getTelNo(),
                personel.getIsGirisTarihi(),
                yetenekler
        );
    }

    @Transactional
    @PreAuthorize("isAuthenticated()")
    public void profilimiGuncelle(String personelId, ProfilUpdateRequest request) {
        Personel personel = personelRepository.findById(personelId).orElseThrow(() -> new KaynakBulunamadiException("Personel bulunamadı."));

        if (!Boolean.TRUE.equals(personel.getCalisiyorMu())) {
            throw new AccessDeniedException("Pasif personel profilini güncelleyemez.");
        }

        if (!request.herhangiBirAlanGonderildiMi()) {
            throw new GecersizIstekException("Güncellenecek en az bir alan gönderilmelidir.");
        }

        List<String> degisenAlanlar = new ArrayList<>();

        if (request.isTelNoGonderildiMi()) {
            String yeniTelNo = nullableMetniNormalizeEt(request.getTelNo(), "Telefon numarası");

            if (!Objects.equals(personel.getTelNo(), yeniTelNo)) {
                personel.setTelNo(yeniTelNo);
                degisenAlanlar.add("telNo");
            }
        }

        if (request.isMezunOkulGonderildiMi()) {
            String yeniMezunOkul = nullableMetniNormalizeEt(request.getMezunOkul(), "Mezun olunan okul");

            if (!Objects.equals(personel.getMezunOkul(), yeniMezunOkul)) {
                personel.setMezunOkul(yeniMezunOkul);
                degisenAlanlar.add("mezunOkul");
            }
        }

        if (request.isMezunBolumGonderildiMi()) {
            String yeniMezunBolum = nullableMetniNormalizeEt(request.getMezunBolum(), "Mezun olunan bölüm");

            if (!Objects.equals(personel.getMezunBolum(), yeniMezunBolum)) {
                personel.setMezunBolum(yeniMezunBolum);
                degisenAlanlar.add("mezunBolum");
            }
        }

        if (request.isMezuniyetYiliGonderildiMi()) {
            Integer yeniMezuniyetYili = request.getMezuniyetYili();

            if (yeniMezuniyetYili != null && (yeniMezuniyetYili <= 0 || yeniMezuniyetYili > Year.now().getValue())) {
                throw new GecersizIstekException("Mezuniyet yılı geçerli bir yıl olmalıdır.");
            }

            if (!Objects.equals(personel.getMezuniyetYil(), yeniMezuniyetYili)) {
                personel.setMezuniyetYil(yeniMezuniyetYili);degisenAlanlar.add("mezuniyetYili");
            }
        }

        if (degisenAlanlar.isEmpty()) {
            return;
        }

        auditService.logKaydetOrtakTransaction(AuditEylem.PROFIL_GUNCELLENDI, personel, personel.getKullaniciAdi(), "Güncellenen alanlar: " + String.join(", ", degisenAlanlar), personel);
    }

    @Transactional
    @PreAuthorize("isAuthenticated()")
    public void profilYeteneklerimiGuncelle(String personelId, YetenekUpdateRequest request) {
        Personel personel = personelRepository.findById(personelId).orElseThrow(() -> new KaynakBulunamadiException("Personel bulunamadı."));

        if (!Boolean.TRUE.equals(personel.getCalisiyorMu())) {
            throw new AccessDeniedException("Pasif personel yeteneklerini güncelleyemez."
            );
        }

        yetenekKumesiniGuncelle(personel, personel, request);
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public void personelYetenekleriniAdminOlarakGuncelle(
            String aktorId,
            String hedefPersonelId,
            YetenekUpdateRequest request
    ) {
        Personel aktor = personelYonetimYetkiService.aktifAdminAktoruGetir(aktorId);
        Personel hedef = personelRepository.findById(hedefPersonelId)
                .orElseThrow(() -> new KaynakBulunamadiException("Personel bulunamadı."));

        personelYonetimYetkiService.adminYetenekHedefiniDogrula(aktor, hedef);
        yetenekKumesiniGuncelle(aktor, hedef, request);
    }

    private void yetenekKumesiniGuncelle(
            Personel aktor,
            Personel hedef,
            YetenekUpdateRequest request
    ) {
        String hedefPersonelId = hedef.getPersonelId();

        List<Integer> istenenIdListesi = request.yetenekIdleri();
        Set<Integer> istenenIdler = new HashSet<>(istenenIdListesi);

        if (istenenIdler.size() != istenenIdListesi.size()) {
            throw new GecersizIstekException("Yetenek ID'leri tekrarlanamaz.");
        }

        List<Yetenek> istenenYetenekler =
                yetenekRepository.findAllById(istenenIdler);

        if (istenenYetenekler.size() != istenenIdler.size()) {
            throw new KaynakBulunamadiException("Yetenek bulunamadı.");
        }

        List<PersonelYetenek> mevcutBaglantilar = personelYetenekRepository.findAllByPersonelIdWithYetenek(hedefPersonelId);

        Set<Integer> mevcutIdler = mevcutBaglantilar.stream()
                .map(baglanti -> baglanti.getYetenek().getYetenekId())
                .collect(Collectors.toSet());

        if (mevcutIdler.equals(istenenIdler)) {
            return;
        }

        List<PersonelYetenek> silinecekBaglantilar = mevcutBaglantilar.stream()
                        .filter(baglanti -> !istenenIdler.contains(baglanti.getYetenek().getYetenekId()))
                        .toList();

        List<PersonelYetenek> eklenecekBaglantilar = istenenYetenekler
                        .stream()
                        .filter(yetenek -> !mevcutIdler.contains(yetenek.getYetenekId()))
                        .map(yetenek -> {
                            PersonelYetenek baglanti = new PersonelYetenek();
                            baglanti.setId(new PersonelYetenekId(hedefPersonelId, yetenek.getYetenekId()));
                            baglanti.setPersonel(hedef);
                            baglanti.setYetenek(yetenek);

                            return baglanti;
                        }).toList();

        personelYetenekRepository.deleteAll(silinecekBaglantilar);
        personelYetenekRepository.saveAll(eklenecekBaglantilar);

        auditService.logKaydetOrtakTransaction(
                AuditEylem.PROFIL_GUNCELLENDI,
                aktor,
                aktor.getKullaniciAdi(),
                "Güncellenen alanlar: yetenekler",
                hedef
        );
    }

    private String nullableMetniNormalizeEt(String deger, String alanAdi) {
        if (deger == null) {
            return null;
        }
        String temizlenmisDeger = deger.trim();
        if (temizlenmisDeger.isEmpty()) {
            throw new GecersizIstekException(alanAdi + " boş metin olarak gönderilemez. " + "Temizlemek için null gönderilmelidir.");
        }
        return temizlenmisDeger;
    }
}
