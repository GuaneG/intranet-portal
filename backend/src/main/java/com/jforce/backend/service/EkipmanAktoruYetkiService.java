package com.jforce.backend.service;

import com.jforce.backend.exception.KaynakBulunamadiException;
import com.jforce.backend.model.entity.Personel;
import com.jforce.backend.model.enums.RolAdi;
import com.jforce.backend.repository.PersonelRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class EkipmanAktoruYetkiService {

    private final PersonelRepository personelRepository;

    public EkipmanAktoruYetkiService(PersonelRepository personelRepository) {
        this.personelRepository = personelRepository;
    }

    public Personel aktifAktorGetir(String aktorId) {
        Personel aktor = personelRepository.findById(aktorId)
                .orElseThrow(() -> new KaynakBulunamadiException(
                        "Personel bulunamadı."
                ));

        if (!Boolean.TRUE.equals(aktor.getCalisiyorMu())) {
            throw new AccessDeniedException(
                    "Pasif personel ekipman işlemi yapamaz."
            );
        }

        if (securityContexttekiRol() != aktor.getRol().getRolAdi()) {
            throw new AccessDeniedException(
                    "JWT ve personel rolü uyuşmuyor."
            );
        }

        return aktor;
    }

    public Personel adminAktoruGetir(String aktorId) {
        Personel aktor = aktifAktorGetir(aktorId);

        if (aktor.getRol().getRolAdi() != RolAdi.ADMIN) {
            throw new AccessDeniedException(
                    "Bu işlem için yetkiniz bulunmuyor."
            );
        }

        return aktor;
    }

    public Personel yoneticiAktoruGetir(String aktorId) {
        Personel aktor = aktifAktorGetir(aktorId);

        if (aktor.getRol().getRolAdi() != RolAdi.YONETICI) {
            throw new AccessDeniedException(
                    "Bu işlem için yetkiniz bulunmuyor."
            );
        }

        return aktor;
    }

    public Personel kararAktoruGetir(String aktorId) {
        Personel aktor = aktifAktorGetir(aktorId);
        RolAdi rol = aktor.getRol().getRolAdi();

        if (rol != RolAdi.YONETICI && rol != RolAdi.ADMIN) {
            throw new AccessDeniedException(
                    "Bu işlem için yetkiniz bulunmuyor."
            );
        }

        return aktor;
    }

    public Personel selfServiceAktoruGetir(String aktorId) {
        Personel aktor = aktifAktorGetir(aktorId);
        selfServiceAktoruDogrula(aktor);
        return aktor;
    }

    public void selfServiceAktoruDogrula(Personel aktor) {
        if (aktor == null || !Boolean.TRUE.equals(aktor.getCalisiyorMu())) {
            throw new AccessDeniedException(
                    "Pasif personel ekipman işlemi yapamaz."
            );
        }

        RolAdi dbRolu = aktor.getRol().getRolAdi();
        if (securityContexttekiRol() != dbRolu) {
            throw new AccessDeniedException(
                    "JWT ve personel rolü uyuşmuyor."
            );
        }

        if (dbRolu != RolAdi.PERSONEL && dbRolu != RolAdi.HR) {
            throw new AccessDeniedException(
                    "Bu işlem için yetkiniz bulunmuyor."
            );
        }
    }

    private RolAdi securityContexttekiRol() {
        Authentication authentication = SecurityContextHolder
                .getContext()
                .getAuthentication();

        if (authentication == null) {
            throw new AccessDeniedException(
                    "JWT ve personel rolü uyuşmuyor."
            );
        }

        String[] roller = authentication.getAuthorities()
                .stream()
                .map(authority -> authority.getAuthority())
                .filter(authority -> authority.startsWith("ROLE_"))
                .toArray(String[]::new);

        if (roller.length != 1) {
            throw new AccessDeniedException(
                    "JWT ve personel rolü uyuşmuyor."
            );
        }

        try {
            return RolAdi.valueOf(roller[0].substring("ROLE_".length()));
        } catch (IllegalArgumentException exception) {
            throw new AccessDeniedException(
                    "JWT ve personel rolü uyuşmuyor."
            );
        }
    }
}
