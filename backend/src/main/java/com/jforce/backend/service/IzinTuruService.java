package com.jforce.backend.service;

import com.jforce.backend.model.dto.response.IzinTuruLookupResponse;
import com.jforce.backend.repository.IzinTuruRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class IzinTuruService {

    private final IzinAktoruYetkiService izinAktoruYetkiService;
    private final IzinTuruRepository izinTuruRepository;

    public IzinTuruService(
            IzinAktoruYetkiService izinAktoruYetkiService,
            IzinTuruRepository izinTuruRepository
    ) {
        this.izinAktoruYetkiService = izinAktoruYetkiService;
        this.izinTuruRepository = izinTuruRepository;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('PERSONEL', 'YONETICI', 'HR', 'ADMIN')")
    public List<IzinTuruLookupResponse> izinTurleriniGetir(String aktorId) {
        izinAktoruYetkiService.lookupAktoruGetir(aktorId);

        return izinTuruRepository
                .findAllByOrderByIzinTuruAdiAscIzinTuruIdAsc()
                .stream()
                .map(tur -> new IzinTuruLookupResponse(
                        tur.getIzinTuruId(),
                        tur.getIzinTuruAdi(),
                        tur.isYillikHaktanDuserMi()
                ))
                .toList();
    }
}
