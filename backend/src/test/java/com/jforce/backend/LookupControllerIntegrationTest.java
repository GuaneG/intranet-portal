package com.jforce.backend;

import com.jforce.backend.model.entity.Departman;
import com.jforce.backend.model.entity.Personel;
import com.jforce.backend.model.entity.Rol;
import com.jforce.backend.model.enums.RolAdi;
import com.jforce.backend.repository.AuditLogRepository;
import com.jforce.backend.repository.DepartmanRepository;
import com.jforce.backend.repository.IzinBilgileriRepository;
import com.jforce.backend.repository.PersonelRepository;
import com.jforce.backend.repository.PersonelYetenekRepository;
import com.jforce.backend.repository.RefreshTokenRepository;
import com.jforce.backend.repository.RolRepository;
import com.jforce.backend.service.JWTService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.junit.jupiter.params.provider.EnumSource;


import java.time.LocalDate;
import java.util.stream.Stream;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class LookupControllerIntegrationTest
        extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JWTService jwtService;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private IzinBilgileriRepository izinBilgileriRepository;

    @Autowired
    private PersonelYetenekRepository personelYetenekRepository;

    @Autowired
    private PersonelRepository personelRepository;

    @Autowired
    private DepartmanRepository departmanRepository;

    @Autowired
    private RolRepository rolRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Rol personelRolu;
    private Departman aktorDepartmani;
    private Personel aktor;

    @BeforeEach
    void setUp() {
        auditLogRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        izinBilgileriRepository.deleteAll();
        personelYetenekRepository.deleteAll();
        personelRepository.deleteAll();
        departmanRepository.deleteAll();
        rolRepository.deleteAll();

        jdbcTemplate.update("DELETE FROM yetenek");

        personelRolu = rolKaydet(
                2,
                RolAdi.PERSONEL
        );

        aktorDepartmani = departmanKaydet("Yazılım");

        aktor = personelKaydet(
                "lookup.aktor",
                "Lookup",
                "Aktör",
                personelRolu,
                aktorDepartmani,
                true
        );
    }

    @Test
    void departmanlarAlfabetikDonerVeKullanilmayanDepartmanDahilEdilir()
            throws Exception {
        Departman finans = departmanKaydet("Finans");

        Departman bilgiTeknolojileri =
                departmanKaydet("Bilgi Teknolojileri");

        mockMvc.perform(get("/api/departments")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + token(
                                        aktor,
                                        RolAdi.PERSONEL
                                )
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(
                        jsonPath("$[0].id")
                                .value(
                                        bilgiTeknolojileri
                                                .getDepartmanId()
                                )
                )
                .andExpect(
                        jsonPath("$[0].ad")
                                .value("Bilgi Teknolojileri")
                )
                .andExpect(
                        jsonPath("$[1].id")
                                .value(finans.getDepartmanId())
                )
                .andExpect(
                        jsonPath("$[1].ad")
                                .value("Finans")
                )
                .andExpect(
                        jsonPath("$[2].id")
                                .value(
                                        aktorDepartmani
                                                .getDepartmanId()
                                )
                )
                .andExpect(
                        jsonPath("$[2].ad")
                                .value("Yazılım")
                )
                .andExpect(
                        jsonPath("$[0].departmanId")
                                .doesNotExist()
                )
                .andExpect(
                        jsonPath("$[0].departmanAdi")
                                .doesNotExist()
                );

        assertEquals(0L, auditLogRepository.count());
    }

    @Test
    void yeteneklerAlfabetikDonerVeKullanilmayanYetenekDahilEdilir()
            throws Exception {
        Integer javaId = yetenekOlustur("Java");
        Integer angularId = yetenekOlustur("Angular");
        Integer dockerId = yetenekOlustur("Docker");

        mockMvc.perform(get("/api/skills")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + token(
                                        aktor,
                                        RolAdi.PERSONEL
                                )
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(
                        jsonPath("$[0].id")
                                .value(angularId)
                )
                .andExpect(
                        jsonPath("$[0].ad")
                                .value("Angular")
                )
                .andExpect(
                        jsonPath("$[1].id")
                                .value(dockerId)
                )
                .andExpect(
                        jsonPath("$[1].ad")
                                .value("Docker")
                )
                .andExpect(
                        jsonPath("$[2].id")
                                .value(javaId)
                )
                .andExpect(
                        jsonPath("$[2].ad")
                                .value("Java")
                )
                .andExpect(
                        jsonPath("$[0].yetenekId")
                                .doesNotExist()
                )
                .andExpect(
                        jsonPath("$[0].yetenekAdi")
                                .doesNotExist()
                );

        assertEquals(0L, auditLogRepository.count());
    }

    @Test
    void yetenekYoksaBosListeDoner()
            throws Exception {
        mockMvc.perform(get("/api/skills")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + token(
                                        aktor,
                                        RolAdi.PERSONEL
                                )
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @ParameterizedTest
    @MethodSource("rollerVeEndpointler")
    void tumRollerIkiLookupEndpointineErisebilir(
            RolAdi rolAdi,
            String endpoint
    ) throws Exception {
        Rol rol = switch (rolAdi) {
            case ADMIN ->
                    rolKaydet(1, RolAdi.ADMIN);

            case PERSONEL ->
                    personelRolu;

            case YONETICI ->
                    rolKaydet(3, RolAdi.YONETICI);

            case HR ->
                    rolKaydet(4, RolAdi.HR);
        };

        aktor.setRol(rol);
        aktor = personelRepository.saveAndFlush(aktor);

        mockMvc.perform(get(endpoint)
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + token(
                                        aktor,
                                        rolAdi
                                )
                        ))
                .andExpect(status().isOk());
    }

    static Stream<Arguments> rollerVeEndpointler() {
        return Stream.of(RolAdi.values())
                .flatMap(rolAdi ->
                        Stream.of(
                                        "/api/departments",
                                        "/api/skills"
                                )
                                .map(endpoint ->
                                        Arguments.of(
                                                rolAdi,
                                                endpoint
                                        )
                                )
                );
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/departments",
            "/api/skills"
    })
    void tokenOlmadanLookupGoruntulenemez(
            String endpoint
    ) throws Exception {
        mockMvc.perform(get(endpoint))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/departments",
            "/api/skills"
    })
    void tokendakiPersonelBulunamazsa404Doner(
            String endpoint
    ) throws Exception {
        String token = jwtService.generateToken(
                "veritabaninda-olmayan-id",
                RolAdi.PERSONEL
        );

        mockMvc.perform(get(endpoint)
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + token
                        ))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(
                        jsonPath("$.message")
                                .value("Personel bulunamadı.")
                );
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/departments",
            "/api/skills"
    })
    void pasifPrincipalLookupGoruntuleyemez(
            String endpoint
    ) throws Exception {
        String token = token(
                aktor,
                RolAdi.PERSONEL
        );

        aktor.setCalisiyorMu(false);
        aktor = personelRepository.saveAndFlush(aktor);

        mockMvc.perform(get(endpoint)
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + token
                        ))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    private Rol rolKaydet(
            int rolId,
            RolAdi rolAdi
    ) {
        Rol rol = new Rol();
        rol.setRolId(rolId);
        rol.setRolAdi(rolAdi);

        return rolRepository.save(rol);
    }

    private Departman departmanKaydet(String ad) {
        Departman departman = new Departman();
        departman.setDepartmanAdi(ad);

        return departmanRepository.save(departman);
    }

    private Personel personelKaydet(
            String kullaniciAdi,
            String ad,
            String soyad,
            Rol rol,
            Departman departman,
            boolean aktif
    ) {
        Personel personel = new Personel();
        personel.setKullaniciAdi(kullaniciAdi);
        personel.setParolaHash("test-hash");
        personel.setAdi(ad);
        personel.setSoyadi(soyad);
        personel.setDogumTarihi(
                LocalDate.of(1995, 1, 1)
        );
        personel.setePosta(
                kullaniciAdi + "@test.local"
        );
        personel.setDepartman(departman);
        personel.setRol(rol);
        personel.setCalisiyorMu(aktif);
        personel.setIsGirisTarihi(
                LocalDate.of(2022, 1, 1)
        );
        personel.setYillikIzinHakki(20);
        personel.setProfilFoto("def_pfp.png");

        return personelRepository.saveAndFlush(personel);
    }

    private Integer yetenekOlustur(
            String yetenekAdi
    ) {
        jdbcTemplate.update(
                """
                INSERT INTO yetenek (yetenek_adi)
                VALUES (?)
                """,
                yetenekAdi
        );

        return jdbcTemplate.queryForObject(
                """
                SELECT yetenek_id
                FROM yetenek
                WHERE yetenek_adi = ?
                """,
                Integer.class,
                yetenekAdi
        );
    }

    private String token(
            Personel personel,
            RolAdi rolAdi
    ) {
        return jwtService.generateToken(
                personel.getPersonelId(),
                rolAdi
        );
    }
}