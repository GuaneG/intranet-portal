package com.jforce.backend.service;

import com.jforce.backend.exception.GecersizIstekException;
import com.jforce.backend.model.dto.response.PersonelYonetimListeResponse;
import com.jforce.backend.model.dto.response.LookUpResponse;
import com.jforce.backend.model.dto.response.SayfaliResponse;
import com.jforce.backend.model.entity.Personel;
import com.jforce.backend.model.enums.RolAdi;
import com.jforce.backend.repository.PersonelRepository;
import com.jforce.backend.repository.PersonelYetenekRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.jforce.backend.exception.KaynakBulunamadiException;
import com.jforce.backend.model.dto.response.PersonelYonetimDetayResponse;
import com.jforce.backend.model.dto.response.AtanabilirYoneticiResponse;
import com.jforce.backend.repository.DepartmanRepository;

import java.util.List;
import java.util.Set;

@Service
public class PersonelYonetimSorguService {

    private final PersonelRepository personelRepository;
    private final PersonelYonetimYetkiService yetkiService;
    private final DepartmanRepository departmanRepository;
    private final PersonelYetenekRepository personelYetenekRepository;

    public PersonelYonetimSorguService(
            PersonelRepository personelRepository,
            PersonelYonetimYetkiService yetkiService,
            DepartmanRepository departmanRepository,
            PersonelYetenekRepository personelYetenekRepository
    ) {
        this.personelRepository = personelRepository;
        this.yetkiService = yetkiService;
        this.departmanRepository = departmanRepository;
        this.personelYetenekRepository = personelYetenekRepository;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('HR', 'ADMIN')")
    public PersonelYonetimDetayResponse personelDetayiniGetir(String aktorId, String hedefPersonelId) {
        Personel aktor = yetkiService.aktifAktoruGetir(aktorId);
        Personel hedef = personelRepository.findYonetimDetayiById(hedefPersonelId).orElseThrow(() -> new KaynakBulunamadiException("Personel bulunamadı."));
        yetkiService.hedefiDogrula(aktor, hedef);

        List<LookUpResponse> yetenekler = personelYetenekRepository
                .findAllByPersonelIdWithYetenek(hedefPersonelId)
                .stream()
                .map(personelYetenek -> new LookUpResponse(
                        personelYetenek.getYetenek().getYetenekId(),
                        personelYetenek.getYetenek().getYetenekAdi()
                ))
                .toList();

        return detayResponseOlustur(hedef, yetenekler);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('HR', 'ADMIN')")
    public SayfaliResponse<PersonelYonetimListeResponse> personelleriGetir(String aktorId, int page, int size, String arama, Integer departmanId, RolAdi rol, Boolean aktif) {
        paginationDogrula(page, size);
        departmanFiltresiniDogrula(departmanId);
        Personel aktor = yetkiService.aktifAktoruGetir(aktorId);

        Set<RolAdi> izinliRoller = yetkiService.izinliHedefRolleri(aktor);
        String normalizeArama = aramayiNormalizeEt(arama);

        Page<Personel> personelSayfasi = personelRepository.findPersonelYonetimSayfasi(
                                aktor.getPersonelId(),
                                izinliRoller,
                                normalizeArama,
                                departmanId,
                                rol,
                                aktif,
                                PageRequest.of(page, size)
                        );

        List<PersonelYonetimListeResponse> icerik = personelSayfasi.getContent()
                        .stream()
                        .map(this::listeResponseOlustur)
                        .toList();

        return new SayfaliResponse<>(
                icerik,
                personelSayfasi.getNumber(),
                personelSayfasi.getSize(),
                personelSayfasi.getTotalElements(),
                personelSayfasi.getTotalPages()
        );
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('HR', 'ADMIN')")
    public List<AtanabilirYoneticiResponse> atanabilirYoneticileriGetir(String aktorId, Integer departmanId, String haricPersonelId) {
        yetkiService.aktifAktoruGetir(aktorId);

        if (departmanId == null || departmanId <= 0) {
            throw new GecersizIstekException("Departman ID pozitif olmalıdır.");
        }
        if (!departmanRepository.existsById(departmanId)) {
            throw new KaynakBulunamadiException("Departman bulunamadı.");
        }

        String normalizeHaricPersonelId = haricPersonelIdyiNormalizeEt(haricPersonelId);

        return personelRepository.findAtanabilirYoneticiler(departmanId,RolAdi.YONETICI, normalizeHaricPersonelId)
                .stream()
                .map(this::atanabilirYoneticiResponseOlustur)
                .toList();
    }

    private String haricPersonelIdyiNormalizeEt(String haricPersonelId) {
        if (haricPersonelId == null) {
            return null;
        }
        String normalizePersonelId = haricPersonelId.trim();
        return normalizePersonelId.isEmpty() ? null : normalizePersonelId;
    }

    private AtanabilirYoneticiResponse atanabilirYoneticiResponseOlustur(Personel personel) {
        return new AtanabilirYoneticiResponse(personel.getPersonelId(), personel.getAdi(), personel.getSoyadi(), personel.getKullaniciAdi());
    }

    private String aramayiNormalizeEt(String arama) {
        if (arama == null) {return null;}
        String normalizeArama = arama.trim();
        return normalizeArama.isEmpty() ? null : normalizeArama;
    }

    private void paginationDogrula(int page, int size) {
        if (page < 0) {throw new GecersizIstekException("Sayfa numarası sıfırdan küçük olamaz.");}
        if (size < 1 || size > 100) {throw new GecersizIstekException("Sayfa boyutu 1 ile 100 arasında olmalıdır.");}
    }

    private void departmanFiltresiniDogrula(Integer departmanId) {
        if (departmanId != null && departmanId <= 0) {throw new GecersizIstekException("Departman ID pozitif olmalıdır.");}
    }

    private PersonelYonetimDetayResponse detayResponseOlustur(
            Personel personel,
            List<LookUpResponse> yetenekler
    ) {
        String yoneticiId = personel.getYonetici() == null ? null : personel.getYonetici().getPersonelId();

        return new PersonelYonetimDetayResponse(
                personel.getPersonelId(),
                personel.getKullaniciAdi(),
                personel.getAdi(),
                personel.getSoyadi(),
                personel.getProfilFoto(),
                personel.getDogumTarihi(),
                personel.getePosta(),
                personel.getDepartman().getDepartmanId(),
                yoneticiId,
                personel.getIsGirisTarihi(),
                personel.getTelNo(),
                personel.getMezunOkul(),
                personel.getMezunBolum(),
                personel.getMezuniyetYil(),
                personel.getRol().getRolAdi(),
                personel.getCalisiyorMu(),
                yetenekler
        );
    }

    private PersonelYonetimListeResponse listeResponseOlustur(Personel personel) {
        return new PersonelYonetimListeResponse(
                personel.getPersonelId(),
                personel.getAdi(),
                personel.getSoyadi(),
                personel.getKullaniciAdi(),
                personel.getProfilFoto(),
                personel.getRol().getRolAdi(),
                personel.getDepartman().getDepartmanId(),
                personel.getDepartman().getDepartmanAdi(),
                personel.getCalisiyorMu()
        );
    }
}
