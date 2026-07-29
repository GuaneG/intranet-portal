package com.jforce.backend.service;


import com.jforce.backend.exception.*;
import com.jforce.backend.model.dto.request.IlkParolaDegistirRequest;
import com.jforce.backend.model.dto.request.ParolaDegistirRequest;
import com.jforce.backend.model.entity.Personel;
import com.jforce.backend.model.enums.AuditEylem;
import com.jforce.backend.repository.PersonelRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Service
public class ParolaService {

    private final PasswordEncoder passwordEncoder;
    private final PersonelRepository personelRepository;
    private final RefreshTokenService refreshTokenService;
    private final AuditService auditService;

    public ParolaService(PasswordEncoder passwordEncoder, PersonelRepository personelRepository, RefreshTokenService refreshTokenService, AuditService auditService) {
        this.passwordEncoder = passwordEncoder;
        this.personelRepository = personelRepository;
        this.refreshTokenService = refreshTokenService;
        this.auditService = auditService;
    }

    public String hashle(String hamParola) {
        return passwordEncoder.encode(hamParola);
    }

    public boolean eslesiyorMu(String hamParola, String parolaHash) {
        return passwordEncoder.matches(hamParola, parolaHash);
    }

    public void yeniParolayiDogrula(String yeniParola, String kullaniciAdi, String mevcutParolaHash) {

        if (yeniParola == null || yeniParola.length() < 8 || yeniParola.length() > 72) {
            throw new GecersizParolaException("Parola 8 ile 72 karakter arasında olmalıdır.");
        }

        //bcrypt'ın 72 byte sınırı geçilmemeli
        if (yeniParola.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new GecersizParolaException("Parola çok uzun. Lütfen daha kısa bir parola belirleyin.");
        }

        if (yeniParola.codePoints().anyMatch(Character::isWhitespace)) {
            throw new GecersizParolaException("Parola boşluk içeremez.");
        }

        boolean kucukHarfVar = yeniParola.codePoints().anyMatch(Character::isLowerCase);
        boolean buyukHarfVar = yeniParola.codePoints().anyMatch(Character::isUpperCase);
        boolean rakamVar = yeniParola.codePoints().anyMatch(Character::isDigit);
        boolean ozelKarakterVar = yeniParola.codePoints().anyMatch(karakter -> !Character.isLetterOrDigit(karakter) && !Character.isWhitespace(karakter));

        if (!kucukHarfVar || !buyukHarfVar || !rakamVar || !ozelKarakterVar) {
            throw new GecersizParolaException("Parola küçük harf, büyük harf, rakam ve özel karakter içermelidir.");
        }

        if (kullaniciAdi != null && yeniParola.equalsIgnoreCase(kullaniciAdi)) {
            throw new GecersizParolaException("Parola kullanıcı adıyla aynı olamaz.");
        }

        if (mevcutParolaHash != null && eslesiyorMu(yeniParola, mevcutParolaHash)) {
            throw new GecersizParolaException("Yeni parola mevcut parolayla aynı olamaz.");
        }

    }

    public String kullaniciAdiniNormalizeEt(String kullaniciAdi) {
        if (kullaniciAdi == null) {
            throw new GecersizIstekException("Kullanıcı adı zorunludur.");
        }

        String normalizeKullaniciAdi = kullaniciAdi.trim().toLowerCase(Locale.ROOT);

        if (!normalizeKullaniciAdi.matches("^[a-z0-9._-]{3,50}$")) {
            throw new GecersizIstekException(
                    "Kullanıcı adı yalnızca harf, rakam, nokta, alt çizgi ve tire içerebilir."
            );
        }

        return normalizeKullaniciAdi;
    }

    @Transactional
    public void ilkParolayiDegistir(IlkParolaDegistirRequest request) {
        String kullaniciAdi = kullaniciAdiniNormalizeEt(request.kullaniciAdi());

        Personel personel = personelRepository.findByKullaniciAdi(kullaniciAdi).
                orElseThrow(() -> new BadCredentialsException("Kullanıcı adı veya mevcut parola hatalı."
        ));

        if (!Boolean.TRUE.equals(personel.getCalisiyorMu()) || !eslesiyorMu(request.mevcutParola(), personel.getParolaHash())) {
            throw new BadCredentialsException("Kullanıcı adı veya mevcut parola hatalı.");
        }

        if (!personel.getParolaDegistirilmeliMi()) {
            throw new KaynakCakismasiException("İlk parola değişikliği daha önce tamamlanmış.");
        }

        yeniParolayiDogrula(request.yeniParola(), personel.getKullaniciAdi(), personel.getParolaHash());

        personel.setParolaHash(hashle(request.yeniParola()));
        personel.setParolaDegistirilmeliMi(false);

        refreshTokenService.revokeAllForPersonel(personel);

        auditService.logKaydetOrtakTransaction(AuditEylem.PAROLA_DEGISTIRILDI, personel, personel.getKullaniciAdi(), "İlk giriş parolası değiştirildi.", personel
        );
    }

    @Transactional
    @PreAuthorize("isAuthenticated()") //metodu sadece giriş yapmış bir kullanıcı çağırabilir
    public void parolaDegistir(String personelId, ParolaDegistirRequest request) {
        //personeli al
        Personel personel = personelRepository.findById(personelId).orElseThrow(() -> new KaynakBulunamadiException("Personel bulunamadı."));


        if (!Boolean.TRUE.equals(personel.getCalisiyorMu())) {
            throw new AccessDeniedException("Pasif personel parola değiştiremez.");
        }

        if (!eslesiyorMu(request.mevcutParola(), personel.getParolaHash())) {
            throw new GecersizParolaException("Mevcut parola hatalı.");
        }

        yeniParolayiDogrula(request.yeniParola(), personel.getKullaniciAdi(), personel.getParolaHash());

        personel.setParolaHash(hashle(request.yeniParola()));
        personel.setParolaDegistirilmeliMi(false);

        refreshTokenService.revokeAllForPersonel(personel);

        auditService.logKaydetOrtakTransaction(
                AuditEylem.PAROLA_DEGISTIRILDI,
                personel,
                personel.getKullaniciAdi(),
                "Parola kullanıcı tarafından değiştirildi.",
                personel
        );
    }

}
