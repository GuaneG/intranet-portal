package com.jforce.backend.service;

import com.jforce.backend.exception.GecersizIstekException;
import com.jforce.backend.exception.KaynakBulunamadiException;
import com.jforce.backend.exception.KaynakCakismasiException;
import com.jforce.backend.model.dto.response.EkipmanTalepSahibiResponse;
import com.jforce.backend.model.dto.response.EkipmanZimmetAdminResponse;
import com.jforce.backend.model.dto.response.SayfaliResponse;
import com.jforce.backend.model.dto.response.ZimmetResponse;
import com.jforce.backend.model.entity.EkipmanBilgileri;
import com.jforce.backend.model.entity.Personel;
import com.jforce.backend.model.entity.ZimmetlemeBilgileri;
import com.jforce.backend.model.enums.AuditEylem;
import com.jforce.backend.model.enums.EkipmanDurum;
import com.jforce.backend.repository.EkipmanBilgileriRepository;
import com.jforce.backend.repository.PersonelRepository;
import com.jforce.backend.repository.ZimmetlemeBilgileriRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

@Service
public class ZimmetService {

    private final EkipmanAktoruYetkiService aktorYetkiService;
    private final ZimmetlemeBilgileriRepository zimmetRepository;
    private final PersonelRepository personelRepository;
    private final EkipmanBilgileriRepository ekipmanRepository;
    private final AuditService auditService;
    private final Clock clock;

    public ZimmetService(
            EkipmanAktoruYetkiService aktorYetkiService,
            ZimmetlemeBilgileriRepository zimmetRepository,
            PersonelRepository personelRepository,
            EkipmanBilgileriRepository ekipmanRepository,
            AuditService auditService,
            Clock clock
    ) {
        this.aktorYetkiService = aktorYetkiService;
        this.zimmetRepository = zimmetRepository;
        this.personelRepository = personelRepository;
        this.ekipmanRepository = ekipmanRepository;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('PERSONEL', 'HR', 'YONETICI', 'ADMIN')")
    public SayfaliResponse<ZimmetResponse> kendiZimmetleriniGetir(
            String aktorId,
            int page,
            int size
    ) {
        Personel aktor = aktorYetkiService.aktifAktorGetir(aktorId);
        sayfalamaDogrula(page, size);
        Page<ZimmetlemeBilgileri> sonuc = zimmetRepository.findKendiZimmetleri(
                aktor.getPersonelId(), PageRequest.of(page, size)
        );
        return sayfaOlustur(sonuc, this::kendiZimmetResponse);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ADMIN')")
    public SayfaliResponse<EkipmanZimmetAdminResponse> adminZimmetleriniGetir(
            String aktorId,
            Boolean aktif,
            String personelId,
            Integer ekipmanTipiId,
            int page,
            int size
    ) {
        aktorYetkiService.adminAktoruGetir(aktorId);
        sayfalamaDogrula(page, size);
        if (ekipmanTipiId != null && ekipmanTipiId <= 0) {
            throw new GecersizIstekException(
                    "Ekipman tipi ID pozitif olmalıdır."
            );
        }
        String dogrulanmisPersonelId = personelId == null
                ? null
                : canonicalUuid(personelId, "Geçersiz personel ID.");
        Page<ZimmetlemeBilgileri> sonuc = zimmetRepository.findAdminZimmetleri(
                aktif,
                dogrulanmisPersonelId,
                ekipmanTipiId,
                PageRequest.of(page, size)
        );
        return sayfaOlustur(sonuc, this::adminZimmetResponse);
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public void zimmetiIadeEt(String aktorId, String zimmetId) {
        Personel aktor = aktorYetkiService.adminAktoruGetir(aktorId);
        String dogrulanmisZimmetId = canonicalUuid(
                zimmetId, "Geçersiz zimmet ID."
        );
        String ownerId = zimmetRepository.findOwnerIdByZimmetId(
                        dogrulanmisZimmetId
                )
                .orElseThrow(this::zimmetBulunamadi);
        Personel owner = personelRepository.findByIdForUpdate(ownerId)
                .orElseThrow(this::zimmetBulunamadi);
        ZimmetlemeBilgileri zimmet = zimmetRepository.findByIdForUpdate(
                        dogrulanmisZimmetId
                )
                .orElseThrow(this::zimmetBulunamadi);
        if (!Objects.equals(
                ownerId, zimmet.getPersonelId().getPersonelId()
        )) {
            throw zimmetBulunamadi();
        }

        String ekipmanId = zimmet.getEkipmanId().getEkipmanId();
        EkipmanBilgileri ekipman = ekipmanRepository.findByIdForUpdate(ekipmanId)
                .orElseThrow(() -> new KaynakBulunamadiException(
                        "Ekipman bulunamadı."
                ));
        if (!Objects.equals(
                ekipmanId, zimmet.getEkipmanId().getEkipmanId()
        )) {
            throw zimmetBulunamadi();
        }
        if (zimmet.getIadeTarihi() != null) {
            throw new KaynakCakismasiException(
                    "Zimmet artık açık değil."
            );
        }

        zimmet.setIadeTarihi(LocalDateTime.now(clock));
        ekipman.setEkipmanDurum(EkipmanDurum.DEPODA);
        ekipman.setKullanimDisiNedeni(null);
        ekipmanRepository.saveAndFlush(ekipman);
        zimmetRepository.saveAndFlush(zimmet);

        auditService.logKaydetOrtakTransaction(
                AuditEylem.ZIMMET_IADE_EDILDI,
                aktor,
                aktor.getKullaniciAdi(),
                "Zimmet iade edildi.",
                owner
        );
    }

    private ZimmetResponse kendiZimmetResponse(ZimmetlemeBilgileri zimmet) {
        EkipmanBilgileri ekipman = zimmet.getEkipmanId();
        return new ZimmetResponse(
                zimmet.getZimmetId(),
                ekipman.getEkipmanTipiId().getEkipmanTipiId(),
                ekipman.getEkipmanTipiId().getTipAdi(),
                ekipman.getMarka(),
                ekipman.getModel(),
                ekipman.getSeriNo(),
                zimmet.getTeslimTarihi(),
                zimmet.getIadeTarihi()
        );
    }

    private EkipmanZimmetAdminResponse adminZimmetResponse(
            ZimmetlemeBilgileri zimmet
    ) {
        Personel owner = zimmet.getPersonelId();
        EkipmanBilgileri ekipman = zimmet.getEkipmanId();
        return new EkipmanZimmetAdminResponse(
                zimmet.getZimmetId(),
                new EkipmanTalepSahibiResponse(
                        owner.getAdi(),
                        owner.getSoyadi(),
                        owner.getDepartman().getDepartmanAdi(),
                        owner.getProfilFoto(),
                        Boolean.TRUE.equals(owner.getCalisiyorMu())
                ),
                ekipman.getEkipmanTipiId().getEkipmanTipiId(),
                ekipman.getEkipmanTipiId().getTipAdi(),
                ekipman.getMarka(),
                ekipman.getModel(),
                ekipman.getSeriNo(),
                zimmet.getTeslimTarihi(),
                zimmet.getIadeTarihi()
        );
    }

    private <T> SayfaliResponse<T> sayfaOlustur(
            Page<ZimmetlemeBilgileri> sonuc,
            java.util.function.Function<ZimmetlemeBilgileri, T> donusturucu
    ) {
        return new SayfaliResponse<>(
                sonuc.getContent().stream().map(donusturucu).toList(),
                sonuc.getNumber(),
                sonuc.getSize(),
                sonuc.getTotalElements(),
                sonuc.getTotalPages()
        );
    }

    private void sayfalamaDogrula(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new GecersizIstekException(
                    "Geçersiz sayfalama bilgisi."
            );
        }
    }

    private String canonicalUuid(String deger, String mesaj) {
        if (deger == null) {
            throw new GecersizIstekException(mesaj);
        }
        try {
            String canonical = UUID.fromString(deger).toString();
            if (!canonical.equals(deger)) {
                throw new GecersizIstekException(mesaj);
            }
            return canonical;
        } catch (IllegalArgumentException exception) {
            throw new GecersizIstekException(mesaj);
        }
    }

    private KaynakBulunamadiException zimmetBulunamadi() {
        return new KaynakBulunamadiException("Zimmet bulunamadı.");
    }
}
