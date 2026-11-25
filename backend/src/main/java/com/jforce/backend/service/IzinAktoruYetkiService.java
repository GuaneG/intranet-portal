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
public class IzinAktoruYetkiService {

    private final PersonelRepository personelRepository;

    public IzinAktoruYetkiService(
            PersonelRepository personelRepository
    ) {
        this.personelRepository = personelRepository;
    }

    public Personel talepAktoruGetir(String aktorId) {
        Personel aktor = aktoruGetir(aktorId);
        talepIslemineUygunluguDogrula(aktor);
        return aktor;
    }

    public Personel lookupAktoruGetir(String aktorId) {
        Personel aktor = aktoruGetir(aktorId);
        lookupUygunlugunuDogrula(aktor);
        return aktor;
    }

    public Personel kararAktoruGetir(String aktorId) {
        Personel aktor = aktoruGetir(aktorId);
        ortakUygunluguDogrula(aktor);

        RolAdi rol = aktor.getRol().getRolAdi();
        if (rol != RolAdi.YONETICI && rol != RolAdi.ADMIN) {
            throw new AccessDeniedException(
                    "Bu işlem için yetkiniz bulunmuyor."
            );
        }
        return aktor;
    }

    public Personel iptalAktoruGetir(String aktorId) {
        Personel aktor = aktoruGetir(aktorId);
        ortakUygunluguDogrula(aktor);
        return aktor;
    }

    public void talepIslemineUygunluguDogrula(Personel aktor) {
        ortakUygunluguDogrula(aktor);
        talepRolUygunlugunuDogrula(aktor);
    }

    public void lookupUygunlugunuDogrula(Personel aktor) {
        ortakUygunluguDogrula(aktor);

        if (aktor.getRol().getRolAdi() != RolAdi.ADMIN) {
            talepRolUygunlugunuDogrula(aktor);
        }
    }

    private Personel aktoruGetir(String aktorId) {
        return personelRepository.findById(aktorId)
                .orElseThrow(() -> new KaynakBulunamadiException(
                        "Personel bulunamadı."
                ));
    }

    private void ortakUygunluguDogrula(Personel aktor) {
        if (!Boolean.TRUE.equals(aktor.getCalisiyorMu())) {
            throw new AccessDeniedException(
                    "Pasif personel izin işlemi yapamaz."
            );
        }

        RolAdi jwtRolu = securityContexttekiTekUygulamaRolunuGetir();
        RolAdi dbRolu = aktor.getRol().getRolAdi();

        if (jwtRolu != dbRolu) {
            throw new AccessDeniedException(
                    "JWT ve personel rolü uyuşmuyor."
            );
        }
    }

    private void talepRolUygunlugunuDogrula(Personel aktor) {
        RolAdi rolAdi = aktor.getRol().getRolAdi();

        if (rolAdi == RolAdi.PERSONEL || rolAdi == RolAdi.HR) {
            return;
        }

        if (rolAdi == RolAdi.YONETICI
                && uygunUstYoneticiyeSahip(aktor)) {
            return;
        }

        throw new AccessDeniedException(
                "Bu rol izin işlemi yapamaz."
        );
    }

    private boolean uygunUstYoneticiyeSahip(Personel aktor) {
        Personel ustYonetici = aktor.getYonetici();

        return ustYonetici != null
                && Boolean.TRUE.equals(ustYonetici.getCalisiyorMu())
                && ustYonetici.getRol().getRolAdi()
                == RolAdi.YONETICI;
    }

    private RolAdi securityContexttekiTekUygulamaRolunuGetir() {
        Authentication authentication = SecurityContextHolder
                .getContext()
                .getAuthentication();

        if (authentication == null) {
            throw new AccessDeniedException(
                    "JWT ve personel rolü uyuşmuyor."
            );
        }

        String[] authorities = authentication.getAuthorities()
                .stream()
                .map(authority -> authority.getAuthority())
                .filter(authority -> authority.startsWith("ROLE_"))
                .toArray(String[]::new);

        if (authorities.length != 1) {
            throw new AccessDeniedException(
                    "JWT ve personel rolü uyuşmuyor."
            );
        }

        try {
            return RolAdi.valueOf(
                    authorities[0].substring("ROLE_".length())
            );
        } catch (IllegalArgumentException exception) {
            throw new AccessDeniedException(
                    "JWT ve personel rolü uyuşmuyor."
            );
        }
    }
}
