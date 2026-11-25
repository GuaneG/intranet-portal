package com.jforce.backend.service;

import com.jforce.backend.model.dto.response.HiyerarsiDugumResponse;
import com.jforce.backend.model.dto.response.HiyerarsiResponse;
import com.jforce.backend.model.dto.response.LookUpResponse;
import com.jforce.backend.model.dto.response.YoneticiHiyerarsisiResponse;
import com.jforce.backend.model.dto.response.YoneticiZinciriElemaniResponse;
import com.jforce.backend.model.dto.response.YoneticiZinciriResponse;
import com.jforce.backend.model.entity.Personel;
import com.jforce.backend.model.enums.BugunIzinDurumu;
import com.jforce.backend.model.enums.RolAdi;
import com.jforce.backend.exception.KaynakBulunamadiException;
import com.jforce.backend.exception.KaynakCakismasiException;
import com.jforce.backend.repository.PersonelRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class YoneticiHiyerarsisiService {

    private static final Comparator<Personel> PERSONEL_SIRASI = Comparator
            .comparing(Personel::getAdi)
            .thenComparing(Personel::getSoyadi)
            .thenComparing(Personel::getPersonelId);

    private final PersonelRepository personelRepository;
    private final PersonelYonetimYetkiService personelYonetimYetkiService;
    private final BugunIzinDurumuService bugunIzinDurumuService;

    public YoneticiHiyerarsisiService(
            PersonelRepository personelRepository,
            PersonelYonetimYetkiService personelYonetimYetkiService,
            BugunIzinDurumuService bugunIzinDurumuService
    ) {
        this.personelRepository = personelRepository;
        this.personelYonetimYetkiService = personelYonetimYetkiService;
        this.bugunIzinDurumuService = bugunIzinDurumuService;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('HR', 'ADMIN')")
    public HiyerarsiResponse tumHiyerarsiyiGetir(
            String aktorId,
            Integer departmanId,
            Integer yetenekId,
            String arama
    ) {
        Personel aktor = personelYonetimYetkiService.aktifAktoruGetir(aktorId);
        String aktorYoneticiId = aktor.getYonetici() == null
                ? null
                : aktor.getYonetici().getPersonelId();
        EkipDiziniFiltreleri filtreler = EkipDiziniFiltreleri.olustur(
                arama,
                departmanId,
                yetenekId
        );

        List<Personel> aktifPersoneller =
                personelRepository.findAllAktifHiyerarsiPersonelleri();

        aktifHiyerarsideDonguOlmadiginiDogrula(aktifPersoneller);

        Map<String, Personel> personelHaritasi = new LinkedHashMap<>();
        for (Personel personel : aktifPersoneller) {
            personelHaritasi.put(personel.getPersonelId(), personel);
        }

        Set<String> retainedIds = retainedPersonelIds(
                filtreler,
                personelHaritasi
        );
        Map<String, BugunIzinDurumu> bugunIzinDurumlari =
                bugunIzinDurumuService.durumlariGetir(
                        retainedPersoneller(
                                personelHaritasi,
                                retainedIds
                        )
                );

        return forestOlustur(
                personelHaritasi,
                retainedIds,
                aktorYoneticiId,
                bugunIzinDurumlari
        );
    }

    private Set<String> retainedPersonelIds(
            EkipDiziniFiltreleri filtreler,
            Map<String, Personel> aktifPersonelHaritasi
    ) {
        if (!filtreler.aktifMi()) {
            return new HashSet<>(aktifPersonelHaritasi.keySet());
        }

        List<String> eslesenPersonelIds =
                personelRepository.findAktifHiyerarsiEslesenPersonelIds(
                        filtreler.arama(),
                        filtreler.departmanId(),
                        filtreler.yetenekId()
                );
        Set<String> retainedIds = new HashSet<>();

        for (String personelId : eslesenPersonelIds) {
            Personel personel = aktifPersonelHaritasi.get(personelId);

            while (personel != null
                    && retainedIds.add(personel.getPersonelId())) {
                Personel yonetici = personel.getYonetici();
                personel = yonetici == null
                        ? null
                        : aktifPersonelHaritasi.get(yonetici.getPersonelId());
            }
        }

        return retainedIds;
    }

    private HiyerarsiResponse forestOlustur(
            Map<String, Personel> personelHaritasi,
            Set<String> retainedIds,
            String aktorYoneticiId,
            Map<String, BugunIzinDurumu> bugunIzinDurumlari
    ) {

        Map<String, List<Personel>> dogrudanBaglilar = new HashMap<>();
        List<Personel> kokler = new ArrayList<>();

        for (Personel personel : personelHaritasi.values()) {
            if (!retainedIds.contains(personel.getPersonelId())) {
                continue;
            }

            Personel yonetici = personel.getYonetici();
            if (yonetici == null
                    || !retainedIds.contains(yonetici.getPersonelId())) {
                kokler.add(personel);
                continue;
            }

            dogrudanBaglilar
                    .computeIfAbsent(yonetici.getPersonelId(), ignored -> new ArrayList<>())
                    .add(personel);
        }

        kokler.sort(PERSONEL_SIRASI);
        dogrudanBaglilar.values().forEach(liste -> liste.sort(PERSONEL_SIRASI));

        List<HiyerarsiDugumResponse> kokDugumler = kokler.stream()
                .map(personel -> dugumeDonustur(
                        personel,
                        dogrudanBaglilar,
                        aktorYoneticiId,
                        bugunIzinDurumlari
                ))
                .toList();

        return new HiyerarsiResponse(kokDugumler);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('PERSONEL', 'YONETICI')")
    public YoneticiZinciriResponse yoneticiZinciriniGetir(String aktorId) {
        Personel aktor = personelRepository.findYoneticiZinciriAktoruById(aktorId)
                .orElseThrow(() -> new KaynakBulunamadiException("Personel bulunamadı."));

        if (!Boolean.TRUE.equals(aktor.getCalisiyorMu())) {
            throw new AccessDeniedException("Pasif personel yönetici zincirini görüntüleyemez.");
        }

        RolAdi aktorRolu = aktor.getRol().getRolAdi();
        if (aktorRolu != RolAdi.PERSONEL && aktorRolu != RolAdi.YONETICI) {
            throw new AccessDeniedException("Bu işlem yalnız Personel ve Yönetici rollerine açıktır.");
        }

        yoneticiZincirindeDonguOlmadiginiDogrula(aktor);

        List<YoneticiZinciriElemaniResponse> yoneticiler = new ArrayList<>();
        Personel yonetici = aktor.getYonetici();
        int seviye = 1;

        while (yonetici != null
                && seviye <= 2
                && Boolean.TRUE.equals(yonetici.getCalisiyorMu())) {
            yoneticiler.add(zincirElemaninaDonustur(yonetici, seviye));
            yonetici = yonetici.getYonetici();
            seviye++;
        }

        return new YoneticiZinciriResponse(List.copyOf(yoneticiler));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('YONETICI')")
    public YoneticiHiyerarsisiResponse yoneticiHiyerarsisiniGetir(
            String aktorId,
            Integer departmanId,
            Integer yetenekId,
            String arama
    ) {
        Personel aktor = personelRepository.findById(aktorId)
                .orElseThrow(() -> new KaynakBulunamadiException(
                        "Personel bulunamadı."
                ));

        if (!Boolean.TRUE.equals(aktor.getCalisiyorMu())) {
            throw new AccessDeniedException(
                    "Pasif yönetici hiyerarşisini görüntüleyemez."
            );
        }

        if (aktor.getRol().getRolAdi() != RolAdi.YONETICI) {
            throw new AccessDeniedException(
                    "Bu işlem yalnız Yönetici rolüne açıktır."
            );
        }

        EkipDiziniFiltreleri filtreler = EkipDiziniFiltreleri.olustur(
                arama,
                departmanId,
                yetenekId
        );

        List<Personel> aktifPersoneller =
                personelRepository.findAllAktifHiyerarsiPersonelleri();
        Map<String, Personel> aktifPersonelHaritasi = new LinkedHashMap<>();
        for (Personel personel : aktifPersoneller) {
            aktifPersonelHaritasi.put(personel.getPersonelId(), personel);
        }

        Personel graphAktoru = aktifPersonelHaritasi.get(aktorId);
        if (graphAktoru == null) {
            throw new AccessDeniedException(
                    "Pasif yönetici hiyerarşisini görüntüleyemez."
            );
        }

        yoneticiZincirindeDonguOlmadiginiDogrula(graphAktoru);

        Map<String, List<Personel>> tumDogrudanBaglilar =
                dogrudanBaglilariOlustur(aktifPersoneller);
        Set<String> retainedIds = yoneticiGuvenliKapsamIds(
                graphAktoru,
                aktifPersonelHaritasi,
                tumDogrudanBaglilar,
                filtreler
        );
        String aktorYoneticiId = graphAktoru.getYonetici() == null
                ? null
                : graphAktoru.getYonetici().getPersonelId();
        Map<String, BugunIzinDurumu> bugunIzinDurumlari =
                bugunIzinDurumuService.durumlariGetir(
                        retainedPersoneller(
                                aktifPersonelHaritasi,
                                retainedIds
                        )
                );
        HiyerarsiResponse hiyerarsi = forestOlustur(
                aktifPersonelHaritasi,
                retainedIds,
                aktorYoneticiId,
                bugunIzinDurumlari
        );

        return new YoneticiHiyerarsisiResponse(
                new LookUpResponse(
                        graphAktoru.getDepartman().getDepartmanId(),
                        graphAktoru.getDepartman().getDepartmanAdi()
                ),
                hiyerarsi.kokler()
        );
    }

    private Map<String, List<Personel>> dogrudanBaglilariOlustur(
            List<Personel> aktifPersoneller
    ) {
        Map<String, List<Personel>> dogrudanBaglilar = new HashMap<>();

        for (Personel personel : aktifPersoneller) {
            Personel yonetici = personel.getYonetici();
            if (yonetici != null) {
                dogrudanBaglilar
                        .computeIfAbsent(
                                yonetici.getPersonelId(),
                                ignored -> new ArrayList<>()
                        )
                        .add(personel);
            }
        }

        return dogrudanBaglilar;
    }

    private Set<String> yoneticiGuvenliKapsamIds(
            Personel aktor,
            Map<String, Personel> aktifPersonelHaritasi,
            Map<String, List<Personel>> dogrudanBaglilar,
            EkipDiziniFiltreleri filtreler
    ) {
        Set<String> retainedIds = new HashSet<>();
        retainedIds.add(aktor.getPersonelId());

        Personel ust = aktor.getYonetici();
        int seviye = 0;
        while (ust != null && seviye < 2) {
            Personel aktifUst = aktifPersonelHaritasi.get(ust.getPersonelId());
            if (aktifUst == null) {
                break;
            }
            retainedIds.add(aktifUst.getPersonelId());
            ust = aktifUst.getYonetici();
            seviye++;
        }

        Set<String> astIds = aktifAstIds(
                aktor.getPersonelId(),
                dogrudanBaglilar
        );

        if (!filtreler.aktifMi()) {
            retainedIds.addAll(astIds);
            return retainedIds;
        }

        Set<String> eslesenAstIds = new HashSet<>(
                personelRepository.findAktifHiyerarsiEslesenPersonelIds(
                        filtreler.arama(),
                        filtreler.departmanId(),
                        filtreler.yetenekId()
                )
        );
        eslesenAstIds.retainAll(astIds);

        for (String eslesenAstId : eslesenAstIds) {
            Personel personel = aktifPersonelHaritasi.get(eslesenAstId);

            while (personel != null
                    && !personel.getPersonelId().equals(aktor.getPersonelId())) {
                retainedIds.add(personel.getPersonelId());
                Personel yonetici = personel.getYonetici();
                personel = yonetici == null
                        ? null
                        : aktifPersonelHaritasi.get(yonetici.getPersonelId());
            }
        }

        return retainedIds;
    }

    private Set<String> aktifAstIds(
            String aktorId,
            Map<String, List<Personel>> dogrudanBaglilar
    ) {
        Set<String> gorulenIds = new HashSet<>();
        gorulenIds.add(aktorId);
        Set<String> astIds = new HashSet<>();

        Deque<Personel> beklenecekler = new ArrayDeque<>(
                dogrudanBaglilar.getOrDefault(
                        aktorId,
                        List.of()
                )
        );

        while (!beklenecekler.isEmpty()) {
            Personel personel = beklenecekler.removeFirst();
            if (!gorulenIds.add(personel.getPersonelId())) {
                hiyerarsiTutarsizligiFirlat();
            }
            astIds.add(personel.getPersonelId());
            beklenecekler.addAll(
                    dogrudanBaglilar.getOrDefault(
                            personel.getPersonelId(),
                            List.of()
                    )
            );
        }

        return astIds;
    }

    private void aktifHiyerarsideDonguOlmadiginiDogrula(
            List<Personel> aktifPersoneller
    ) {
        Map<String, Personel> aktifPersonelHaritasi = new HashMap<>();
        for (Personel personel : aktifPersoneller) {
            aktifPersonelHaritasi.put(personel.getPersonelId(), personel);
        }

        Set<String> tamamlananlar = new HashSet<>();

        for (Personel baslangic : aktifPersoneller) {
            if (tamamlananlar.contains(baslangic.getPersonelId())) {
                continue;
            }

            Set<String> mevcutZincir = new HashSet<>();
            Personel personel = baslangic;

            while (personel != null
                    && aktifPersonelHaritasi.containsKey(personel.getPersonelId())) {
                if (tamamlananlar.contains(personel.getPersonelId())) {
                    break;
                }
                if (!mevcutZincir.add(personel.getPersonelId())) {
                    hiyerarsiTutarsizligiFirlat();
                }

                Personel yonetici = personel.getYonetici();
                personel = yonetici == null
                        ? null
                        : aktifPersonelHaritasi.get(yonetici.getPersonelId());
            }

            tamamlananlar.addAll(mevcutZincir);
        }
    }

    private void yoneticiZincirindeDonguOlmadiginiDogrula(Personel aktor) {
        Set<String> gorulenPersoneller = new HashSet<>();
        Personel personel = aktor;

        while (personel != null && Boolean.TRUE.equals(personel.getCalisiyorMu())) {
            if (!gorulenPersoneller.add(personel.getPersonelId())) {
                hiyerarsiTutarsizligiFirlat();
            }
            personel = personel.getYonetici();
        }
    }

    private void hiyerarsiTutarsizligiFirlat() {
        throw new KaynakCakismasiException(
                "Yönetici hiyerarşisinde tutarsızlık tespit edildi."
        );
    }

    private HiyerarsiDugumResponse dugumeDonustur(
            Personel personel,
            Map<String, List<Personel>> dogrudanBaglilar,
            String aktorYoneticiId,
            Map<String, BugunIzinDurumu> bugunIzinDurumlari
    ) {
        List<HiyerarsiDugumResponse> altDugumler = dogrudanBaglilar
                .getOrDefault(personel.getPersonelId(), List.of())
                .stream()
                .map(altPersonel -> dugumeDonustur(
                        altPersonel,
                        dogrudanBaglilar,
                        aktorYoneticiId,
                        bugunIzinDurumlari
                ))
                .toList();

        return new HiyerarsiDugumResponse(
                personel.getAdi(),
                personel.getSoyadi(),
                personel.getDepartman().getDepartmanAdi(),
                personel.getRol().getRolAdi(),
                personel.getProfilFoto(),
                personel.getPersonelId().equals(aktorYoneticiId),
                bugunIzinDurumlari.get(personel.getPersonelId()),
                altDugumler
        );
    }

    private List<Personel> retainedPersoneller(
            Map<String, Personel> personelHaritasi,
            Set<String> retainedIds
    ) {
        return retainedIds.stream()
                .map(personelHaritasi::get)
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    private YoneticiZinciriElemaniResponse zincirElemaninaDonustur(
            Personel yonetici,
            int seviye
    ) {
        return new YoneticiZinciriElemaniResponse(
                seviye,
                yonetici.getAdi(),
                yonetici.getSoyadi(),
                yonetici.getDepartman().getDepartmanAdi(),
                yonetici.getRol().getRolAdi(),
                yonetici.getProfilFoto()
        );
    }
}
