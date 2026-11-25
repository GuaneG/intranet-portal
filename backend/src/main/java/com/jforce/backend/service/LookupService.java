package com.jforce.backend.service;

import com.jforce.backend.exception.KaynakBulunamadiException;
import com.jforce.backend.model.dto.response.LookUpResponse;
import com.jforce.backend.model.entity.Personel;
import com.jforce.backend.repository.DepartmanRepository;
import com.jforce.backend.repository.PersonelRepository;
import com.jforce.backend.repository.YetenekRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class LookupService {

    private final PersonelRepository personelRepository;
    private final DepartmanRepository departmanRepository;
    private final YetenekRepository yetenekRepository;

    public LookupService(PersonelRepository personelRepository, DepartmanRepository departmanRepository, YetenekRepository yetenekRepository) {
        this.personelRepository = personelRepository;
        this.departmanRepository = departmanRepository;
        this.yetenekRepository = yetenekRepository;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("isAuthenticated()")
    public List<LookUpResponse> departmanlariGetir(String aktorId) {
        aktifPersoneliDogrula(aktorId);

        return departmanRepository.findAllByOrderByDepartmanAdiAscDepartmanIdAsc()
                .stream()
                .map(departman ->
                        new LookUpResponse(
                                departman.getDepartmanId(),
                                departman.getDepartmanAdi()
                        )
                )
                .toList();
    }

    @Transactional(readOnly = true)
    @PreAuthorize("isAuthenticated()")
    public List<LookUpResponse> yetenekleriGetir(String aktorId) {
        aktifPersoneliDogrula(aktorId);

        return yetenekRepository.findAllByOrderByYetenekAdiAscYetenekIdAsc()
                .stream()
                .map(yetenek ->
                        new LookUpResponse(
                                yetenek.getYetenekId(),
                                yetenek.getYetenekAdi()
                        )
                )
                .toList();
    }

    private void aktifPersoneliDogrula(String aktorId) {
        Personel aktor = personelRepository.findById(aktorId).orElseThrow(() -> new KaynakBulunamadiException("Personel bulunamadı."));

        if (!Boolean.TRUE.equals(aktor.getCalisiyorMu())) {
            throw new AccessDeniedException("Pasif personel lookup verilerini görüntüleyemez.");
        }
    }
}