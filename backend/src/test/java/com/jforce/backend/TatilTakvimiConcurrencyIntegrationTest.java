package com.jforce.backend;

import com.jforce.backend.exception.KaynakCakismasiException;
import com.jforce.backend.model.dto.request.TatilKaydetRequest;
import com.jforce.backend.model.entity.Departman;
import com.jforce.backend.model.entity.Personel;
import com.jforce.backend.model.entity.Rol;
import com.jforce.backend.model.entity.TatilTakvimiKilit;
import com.jforce.backend.model.enums.RolAdi;
import com.jforce.backend.model.enums.TatilGunSuresi;
import com.jforce.backend.model.enums.TatilKategori;
import com.jforce.backend.repository.AuditLogRepository;
import com.jforce.backend.repository.DepartmanRepository;
import com.jforce.backend.repository.PersonelRepository;
import com.jforce.backend.repository.RolRepository;
import com.jforce.backend.repository.TatilRepository;
import com.jforce.backend.repository.TatilTakvimiKilitRepository;
import com.jforce.backend.service.TatilTakvimiService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class TatilTakvimiConcurrencyIntegrationTest
        extends AbstractIntegrationTest {

    @Autowired
    private TatilTakvimiService tatilTakvimiService;

    @Autowired
    private TatilRepository tatilRepository;

    @MockitoSpyBean
    private TatilTakvimiKilitRepository kilitRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private PersonelRepository personelRepository;

    @Autowired
    private DepartmanRepository departmanRepository;

    @Autowired
    private RolRepository rolRepository;

    private Personel hr;

    @BeforeEach
    void setUp() {
        auditLogRepository.deleteAll();
        personelRepository.deleteAll();
        departmanRepository.deleteAll();
        rolRepository.deleteAll();
        tatilRepository.deleteAll();

        if (!kilitRepository.existsById(1)) {
            kilitRepository.saveAndFlush(new TatilTakvimiKilit(1));
        }

        Rol hrRolu = new Rol();
        hrRolu.setRolId(4);
        hrRolu.setRolAdi(RolAdi.HR);
        hrRolu = rolRepository.saveAndFlush(hrRolu);

        Departman departman = new Departman();
        departman.setDepartmanAdi("İnsan Kaynakları");
        departman = departmanRepository.saveAndFlush(departman);

        Personel personel = new Personel();
        personel.setKullaniciAdi("paralel.hr");
        personel.setParolaHash("test-hash");
        personel.setAdi("Paralel");
        personel.setSoyadi("HR");
        personel.setDogumTarihi(LocalDate.of(1990, 1, 1));
        personel.setePosta("paralel.hr@test.local");
        personel.setDepartman(departman);
        personel.setRol(hrRolu);
        personel.setCalisiyorMu(true);
        personel.setIsGirisTarihi(LocalDate.of(2020, 1, 1));
        personel.setYillikIzinHakki(20);
        personel.setProfilFoto("def_pfp.png");
        hr = personelRepository.saveAndFlush(personel);
    }

    @Test
    void paralelCakisanIkiTatilGrubundanYalnizBiriKaydedilir()
            throws Exception {
        CountDownLatch ikiIstekHazir = new CountDownLatch(2);
        CountDownLatch birlikteBasla = new CountDownLatch(1);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Sonuc> ilk = executor.submit(istek(
                    LocalDate.of(2026, 11, 10),
                    LocalDate.of(2026, 11, 14),
                    ikiIstekHazir,
                    birlikteBasla
            ));
            Future<Sonuc> ikinci = executor.submit(istek(
                    LocalDate.of(2026, 11, 12),
                    LocalDate.of(2026, 11, 16),
                    ikiIstekHazir,
                    birlikteBasla
            ));

            assertTrue(
                    ikiIstekHazir.await(5, TimeUnit.SECONDS),
                    "İki paralel istek başlangıç noktasına ulaşamadı."
            );
            birlikteBasla.countDown();

            List<Sonuc> sonuclar = List.of(
                    ilk.get(10, TimeUnit.SECONDS),
                    ikinci.get(10, TimeUnit.SECONDS)
            );

            assertEquals(1, sonuclar.stream()
                    .filter(sonuc -> sonuc == Sonuc.BASARILI)
                    .count());
            assertEquals(1, sonuclar.stream()
                    .filter(sonuc -> sonuc == Sonuc.CAKISMA)
                    .count());
            assertEquals(1L, tatilRepository.count());
            assertEquals(1L, auditLogRepository.count());
            verify(kilitRepository, times(2)).takvimiKilitle();
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private java.util.concurrent.Callable<Sonuc> istek(
            LocalDate baslangic,
            LocalDate bitis,
            CountDownLatch hazir,
            CountDownLatch basla
    ) {
        return () -> {
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(new UsernamePasswordAuthenticationToken(
                    hr.getPersonelId(),
                    null,
                    List.of(new SimpleGrantedAuthority("ROLE_HR"))
            ));
            SecurityContextHolder.setContext(context);

            try {
                hazir.countDown();
                assertTrue(
                        basla.await(5, TimeUnit.SECONDS),
                        "Paralel istekler birlikte başlatılamadı."
                );
                tatilTakvimiService.tatilOlustur(
                        hr.getPersonelId(),
                        new TatilKaydetRequest(
                                "Paralel Tatil",
                                baslangic,
                                bitis,
                                TatilKategori.SIRKET,
                                TatilGunSuresi.TAM_GUN
                        )
                );
                return Sonuc.BASARILI;
            } catch (KaynakCakismasiException exception) {
                return Sonuc.CAKISMA;
            } finally {
                SecurityContextHolder.clearContext();
            }
        };
    }

    private enum Sonuc {
        BASARILI,
        CAKISMA
    }
}
