package com.jforce.backend.service;

import com.jforce.backend.exception.KaynakBulunamadiException;
import com.jforce.backend.model.entity.Personel;
import com.jforce.backend.model.enums.RolAdi;
import com.jforce.backend.repository.PersonelRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.Set;

@Service
public class PersonelYonetimYetkiService {

    private static final Set<RolAdi> HR_HEDEFLERI = Set.of(RolAdi.PERSONEL, RolAdi.HR);
    private static final Set<RolAdi> ADMIN_HEDEFLERI = Set.of(RolAdi.PERSONEL, RolAdi.HR, RolAdi.YONETICI);

    private final PersonelRepository personelRepository;

    public PersonelYonetimYetkiService(PersonelRepository personelRepository) {
        this.personelRepository = personelRepository;
    }

    public Personel aktifAktoruGetir(String aktorId) {
        Personel aktor = personelRepository.findById(aktorId).orElseThrow(() -> new KaynakBulunamadiException("Personel bulunamadı."));

        if (!Boolean.TRUE.equals(aktor.getCalisiyorMu())) {
            throw new AccessDeniedException("Pasif personel yönetim işlemi yapamaz.");
        }
        izinliHedefRolleri(aktor);
        return aktor;
    }

    public Personel aktifAdminAktoruGetir(String aktorId) {
        Personel aktor = personelRepository.findById(aktorId)
                .orElseThrow(() -> new KaynakBulunamadiException("Personel bulunamadı."));

        if (!Boolean.TRUE.equals(aktor.getCalisiyorMu())) {
            throw new AccessDeniedException("Pasif Admin personel yönetim işlemi yapamaz.");
        }

        if (aktor.getRol().getRolAdi() != RolAdi.ADMIN) {
            throw new AccessDeniedException("Bu işlem yalnız Admin tarafından yapılabilir.");
        }

        return aktor;
    }

    public Set<RolAdi> izinliHedefRolleri(Personel aktor) {
        return switch (aktor.getRol().getRolAdi()) {
            case HR -> HR_HEDEFLERI;
            case ADMIN -> ADMIN_HEDEFLERI;
            default -> throw new AccessDeniedException("Personel yönetimi için yetkiniz bulunmuyor.");
        };
    }

    public void hedefiDogrula(Personel aktor, Personel hedef) {
        if (Objects.equals(aktor.getPersonelId(), hedef.getPersonelId())) {
            throw new AccessDeniedException("Personel kendi kaydını yönetemez.");
        }

        RolAdi hedefRolu = hedef.getRol().getRolAdi();

        if (!izinliHedefRolleri(aktor).contains(hedefRolu)) {
            throw new AccessDeniedException("Hedef personel üzerinde işlem yapılamaz.");
        }
    }

    public void adminYetenekHedefiniDogrula(Personel aktor, Personel hedef) {
        if (Objects.equals(aktor.getPersonelId(), hedef.getPersonelId())) {
            throw new AccessDeniedException("Admin kendi yeteneklerini bu endpoint üzerinden yönetemez.");
        }

        if (!Boolean.TRUE.equals(hedef.getCalisiyorMu())) {
            throw new AccessDeniedException("Pasif personelin yetenekleri yönetilemez.");
        }

        if (!ADMIN_HEDEFLERI.contains(hedef.getRol().getRolAdi())) {
            throw new AccessDeniedException("Hedef personelin yetenekleri yönetilemez.");
        }
    }
}
