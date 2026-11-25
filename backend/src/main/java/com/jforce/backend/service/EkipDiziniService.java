package com.jforce.backend.service;

import com.jforce.backend.exception.GecersizIstekException;
import com.jforce.backend.exception.KaynakBulunamadiException;
import com.jforce.backend.model.dto.response.EkipDiziniResponse;
import com.jforce.backend.model.dto.response.LookUpResponse;
import com.jforce.backend.model.dto.response.SayfaliResponse;
import com.jforce.backend.model.entity.Personel;
import com.jforce.backend.model.enums.BugunIzinDurumu;
import com.jforce.backend.model.enums.RolAdi;
import com.jforce.backend.repository.PersonelRepository;
import com.jforce.backend.repository.PersonelYetenekRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.EnumSet;
import java.util.Set;

@Service
public class EkipDiziniService {

    private final PersonelRepository personelRepository;
    private final PersonelYetenekRepository personelYetenekRepository;
    private final BugunIzinDurumuService bugunIzinDurumuService;

    public EkipDiziniService(
            PersonelRepository personelRepository,
            PersonelYetenekRepository personelYetenekRepository,
            BugunIzinDurumuService bugunIzinDurumuService
    ) {
        this.personelRepository = personelRepository;
        this.personelYetenekRepository = personelYetenekRepository;
        this.bugunIzinDurumuService = bugunIzinDurumuService;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("isAuthenticated()")
    public SayfaliResponse<EkipDiziniResponse> ekipDizininiGetir(String aktorId, int page, int size, Integer departmanId, Integer yetenekId, String arama) {
        paginationDogrula(page, size);
        Personel aktor = personelRepository.findById(aktorId).orElseThrow(() -> new KaynakBulunamadiException("Personel bulunamadı."));

        if (!Boolean.TRUE.equals(aktor.getCalisiyorMu())) {
            throw new AccessDeniedException("Pasif personel ekip dizinini görüntüleyemez.");
        }

        EkipDiziniFiltreleri filtreler = EkipDiziniFiltreleri.olustur(
                arama,
                departmanId,
                yetenekId
        );
        RolAdi aktorRolu = aktor.getRol().getRolAdi();
        Set<RolAdi> gorunurRoller = gorunurRolleriBelirle(aktorRolu);
        Integer gorunurDepartmanId = aktorRolu == RolAdi.PERSONEL
                ? aktor.getDepartman().getDepartmanId()
                : null;

        Page<Personel> personelSayfasi =
                personelRepository.findAktifEkipDizini(
                        gorunurRoller,
                        gorunurDepartmanId,
                        filtreler.departmanId(),
                        filtreler.yetenekId(),
                        filtreler.arama(),
                        PageRequest.of(page, size)
                );

        Map<String, List<LookUpResponse>> yetenekler = yetenekleriPersonelIdyeGoreGetir(personelSayfasi.getContent());
        Map<String, BugunIzinDurumu> bugunIzinDurumlari =
                bugunIzinDurumuService.durumlariGetir(
                        personelSayfasi.getContent()
                );
        String aktorYoneticiId = aktor.getYonetici() == null
                ? null
                : aktor.getYonetici().getPersonelId();

        List<EkipDiziniResponse> icerik = personelSayfasi.getContent().stream()
                        .map(personel -> responseOlustur(
                                personel,
                                yetenekler,
                                aktorYoneticiId,
                                bugunIzinDurumlari
                                )).toList();

        return new SayfaliResponse<>(
                icerik,
                personelSayfasi.getNumber(),
                personelSayfasi.getSize(),
                personelSayfasi.getTotalElements(),
                personelSayfasi.getTotalPages()
        );
    }

    private Set<RolAdi> gorunurRolleriBelirle(RolAdi aktorRolu) {
        if (aktorRolu == RolAdi.ADMIN || aktorRolu == RolAdi.PERSONEL) {
            return EnumSet.allOf(RolAdi.class);
        }

        return EnumSet.of(
                RolAdi.PERSONEL,
                RolAdi.YONETICI,
                RolAdi.HR
        );
    }

    private void paginationDogrula(int page, int size) {
        if (page < 0) {
            throw new GecersizIstekException("Sayfa numarası sıfırdan küçük olamaz.");
        }

        if (size < 1 || size > 100) {
            throw new GecersizIstekException("Sayfa boyutu 1 ile 100 arasında olmalıdır.");
        }
    }

    private Map<String, List<LookUpResponse>> yetenekleriPersonelIdyeGoreGetir(List<Personel> personeller) {
        if (personeller.isEmpty()) {
            return Map.of();
        }

        List<String> personelIds = personeller.stream()
                .map(Personel::getPersonelId)
                .toList();

        Map<String, List<LookUpResponse>> sonuc = new HashMap<>();

        personelYetenekRepository.findAllByPersonelIdsWithYetenek(personelIds)
                .forEach(personelYetenek -> {
                    String personelId = personelYetenek.getPersonel().getPersonelId();
                    LookUpResponse yetenek = new LookUpResponse(personelYetenek.getYetenek().getYetenekId(), personelYetenek.getYetenek().getYetenekAdi());
                    sonuc.computeIfAbsent(personelId, key -> new ArrayList<>()).add(yetenek);
                });

        return sonuc;
    }

    private EkipDiziniResponse responseOlustur(
            Personel personel,
            Map<String, List<LookUpResponse>> yetenekler,
            String aktorYoneticiId,
            Map<String, BugunIzinDurumu> bugunIzinDurumlari
    ) {
        return new EkipDiziniResponse(
                personel.getAdi(),
                personel.getSoyadi(),
                personel.getDepartman().getDepartmanAdi(),
                personel.getProfilFoto(),
                yetenekler.getOrDefault(personel.getPersonelId(), List.of()),
                personel.getPersonelId().equals(aktorYoneticiId),
                bugunIzinDurumlari.get(personel.getPersonelId())
        );
    }
}
