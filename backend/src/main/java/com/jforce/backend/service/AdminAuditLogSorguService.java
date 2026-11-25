package com.jforce.backend.service;

import com.jforce.backend.exception.GecersizIstekException;
import com.jforce.backend.model.dto.response.AuditAktorResponse;
import com.jforce.backend.model.dto.response.AuditHedefResponse;
import com.jforce.backend.model.dto.response.AuditLogResponse;
import com.jforce.backend.model.dto.response.SayfaliResponse;
import com.jforce.backend.model.entity.AuditLog;
import com.jforce.backend.model.entity.Personel;
import com.jforce.backend.model.enums.AuditEylem;
import com.jforce.backend.repository.AuditLogRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

@Service
public class AdminAuditLogSorguService {

    private static final ZoneId TURKIYE_SAAT_DILIMI = ZoneId.of("Europe/Istanbul");

    private final AuditLogRepository auditLogRepository;
    private final PersonelYonetimYetkiService personelYonetimYetkiService;

    public AdminAuditLogSorguService(
            AuditLogRepository auditLogRepository,
            PersonelYonetimYetkiService personelYonetimYetkiService
    ) {
        this.auditLogRepository = auditLogRepository;
        this.personelYonetimYetkiService = personelYonetimYetkiService;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ADMIN')")
    public SayfaliResponse<AuditLogResponse> auditKayitlariniGetir(
            String aktorId,
            int page,
            int size,
            LocalDate baslangicTarihi,
            LocalDate bitisTarihi,
            AuditEylem eylemTipi,
            String aktorPersonelId,
            String hedefPersonelId
    ) {
        personelYonetimYetkiService.aktifAdminAktoruGetir(aktorId);

        if (baslangicTarihi != null
                && bitisTarihi != null
                && baslangicTarihi.isAfter(bitisTarihi)) {
            throw new GecersizIstekException(
                    "Başlangıç tarihi bitiş tarihinden sonra olamaz."
            );
        }

        String dogrulanmisAktorPersonelId = uuidFiltresiniDogrula(
                aktorPersonelId,
                "Aktör personel ID geçerli UUID olmalıdır."
        );
        String dogrulanmisHedefPersonelId = uuidFiltresiniDogrula(
                hedefPersonelId,
                "Hedef personel ID geçerli UUID olmalıdır."
        );

        Instant baslangic = baslangicTarihi == null
                ? null
                : baslangicTarihi.atStartOfDay(TURKIYE_SAAT_DILIMI).toInstant();
        Instant bitisHaric = bitisTarihi == null
                ? null
                : bitisTarihi.plusDays(1).atStartOfDay(TURKIYE_SAAT_DILIMI).toInstant();

        Page<AuditLog> auditSayfasi = auditLogRepository
                .findAuditKayitlari(
                        baslangic,
                        bitisHaric,
                        eylemTipi,
                        dogrulanmisAktorPersonelId,
                        dogrulanmisHedefPersonelId,
                        PageRequest.of(page, size)
                );

        return new SayfaliResponse<>(
                auditSayfasi.getContent().stream()
                        .map(this::responseOlustur)
                        .toList(),
                auditSayfasi.getNumber(),
                auditSayfasi.getSize(),
                auditSayfasi.getTotalElements(),
                auditSayfasi.getTotalPages()
        );
    }

    private AuditLogResponse responseOlustur(AuditLog auditLog) {
        Personel kayitAktoru = auditLog.getPersonel();
        AuditAktorResponse aktorResponse = new AuditAktorResponse(
                kayitAktoru == null ? null : kayitAktoru.getPersonelId(),
                kayitAktoru == null ? null : adSoyad(kayitAktoru),
                auditLog.getKullaniciAdi()
        );

        Personel kayitHedefi = auditLog.getHedefPersonel();
        AuditHedefResponse hedefResponse = kayitHedefi == null
                ? null
                : new AuditHedefResponse(
                        kayitHedefi.getPersonelId(),
                        adSoyad(kayitHedefi)
                );

        return new AuditLogResponse(
                auditLog.getLogId(),
                auditLog.getEylemTipi(),
                auditLog.getOlusmaZamani(),
                aktorResponse,
                hedefResponse,
                auditLog.getDetay()
        );
    }

    private String adSoyad(Personel personel) {
        return personel.getAdi() + " " + personel.getSoyadi();
    }

    private String uuidFiltresiniDogrula(String deger, String hataMesaji) {
        if (deger == null || deger.isBlank()) {
            return null;
        }

        String normalizeDeger = deger.trim();
        try {
            UUID uuid = UUID.fromString(normalizeDeger);
            if (!uuid.toString().equalsIgnoreCase(normalizeDeger)) {
                throw new IllegalArgumentException("UUID canonical değil");
            }
            return uuid.toString();
        } catch (IllegalArgumentException exception) {
            throw new GecersizIstekException(hataMesaji);
        }
    }
}
