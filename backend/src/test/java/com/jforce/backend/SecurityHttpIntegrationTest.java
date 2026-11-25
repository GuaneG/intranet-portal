package com.jforce.backend;

import com.jforce.backend.model.entity.Departman;
import com.jforce.backend.model.entity.Personel;
import com.jforce.backend.model.entity.Rol;
import com.jforce.backend.model.enums.RolAdi;
import com.jforce.backend.repository.DepartmanRepository;
import com.jforce.backend.repository.PersonelRepository;
import com.jforce.backend.repository.RolRepository;
import com.jforce.backend.service.JWTService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@Import(SecurityHttpIntegrationTest.TestController.class)
@AutoConfigureMockMvc
public class SecurityHttpIntegrationTest extends AbstractIntegrationTest{
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JWTService jwtService;
    @Autowired
    private PersonelRepository personelRepository;
    @Autowired
    private DepartmanRepository departmanRepository;
    @Autowired
    private RolRepository rolRepository;

    @Test
    void tokenOlmadanKorumaliEndpointeIstekAtildigindaOrtakErrorResponseIle401Doner()
            throws Exception {

        mockMvc.perform(get("/api/dashboard/ozet"))
                .andExpect(status().isUnauthorized())
                .andExpect(content()
                        .contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message")
                        .value("Kimlik doğrulaması gerekli."))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.path")
                        .value("/api/dashboard/ozet"));
    }
    @RestController
    static class TestController {
        @GetMapping("/test/security/admin")
        @PreAuthorize("hasRole('ADMIN')")
        public String sadeceAdmin() {
            return "ok";
        }
    }

    @Test
    void personelRoluAdminEndpointineIstekAtincaOrtakErrorResponseIle403Doner()
            throws Exception {

        String accessToken = jwtService.generateToken(
                "test-personel-id",
                RolAdi.PERSONEL
        );

        mockMvc.perform(get("/test/security/admin")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + accessToken
                        ))
                .andExpect(status().isForbidden())
                .andExpect(content()
                        .contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message")
                        .value("Bu işlem için yetkiniz bulunmuyor."))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.path")
                        .value("/test/security/admin"));
    }

    @Test
    void ekipmanEndpointleriTokenOlmadan401Doner() throws Exception {
        for (EkipmanRouteCase route : ekipmanGetRotalari().toList()) {
            mockMvc.perform(get(route.path()))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.message")
                            .value("Kimlik doğrulaması gerekli."));
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("ekipmanGetRotalari")
    void ekipmanEndpointlerindeJwtRolMatrisiUygulanir(EkipmanRouteCase route)
            throws Exception {
        for (RolAdi rol : RolAdi.values()) {
            String token = jwtService.generateToken(
                    UUID.randomUUID().toString(),
                    rol
            );

            mockMvc.perform(get(route.path())
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(route.allowedRoles().contains(rol)
                            ? status().isNotFound()
                            : status().isForbidden());
        }
    }

    @Test
    void ekipmanEndpointindeJwtVeDbRoluUyusmazsa403Doner() throws Exception {
        Rol hrRolu = new Rol();
        hrRolu.setRolId(9901);
        hrRolu.setRolAdi(RolAdi.HR);
        rolRepository.saveAndFlush(hrRolu);

        Departman departman = new Departman();
        departman.setDepartmanAdi("Security Ekipman Test");
        departman = departmanRepository.saveAndFlush(departman);

        Personel aktor = personelKaydet(departman, hrRolu);

        try {
            String personelRolundekiToken = jwtService.generateToken(
                    aktor.getPersonelId(),
                    RolAdi.PERSONEL
            );

            mockMvc.perform(get("/api/equipment-types")
                            .header(
                                    HttpHeaders.AUTHORIZATION,
                                    "Bearer " + personelRolundekiToken
                            ))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.status").value(403))
                    .andExpect(jsonPath("$.message")
                            .value("Bu işlem için yetkiniz bulunmuyor."));
        } finally {
            personelRepository.deleteById(aktor.getPersonelId());
            departmanRepository.deleteById(departman.getDepartmanId());
            rolRepository.deleteById(hrRolu.getRolId());
        }
    }

    private Personel personelKaydet(Departman departman, Rol rol) {
        Personel personel = new Personel();
        personel.setKullaniciAdi("security.ekipman.hr");
        personel.setParolaHash("test-hash");
        personel.setAdi("Security");
        personel.setSoyadi("Ekipman");
        personel.setDogumTarihi(LocalDate.of(1990, 1, 1));
        personel.setePosta("security.ekipman.hr@test.local");
        personel.setDepartman(departman);
        personel.setRol(rol);
        personel.setCalisiyorMu(true);
        personel.setIsGirisTarihi(LocalDate.of(2020, 1, 1));
        personel.setYillikIzinHakki(14);
        personel.setProfilFoto("def_pfp.png");
        personel.setParolaDegistirilmeliMi(false);
        return personelRepository.saveAndFlush(personel);
    }

    private static Stream<EkipmanRouteCase> ekipmanGetRotalari() {
        String talepId = "11111111-1111-1111-1111-111111111111";
        return Stream.of(
                new EkipmanRouteCase(
                        "aktif ekipman tipleri - tum roller",
                        "/api/equipment-types",
                        Set.of(RolAdi.PERSONEL, RolAdi.YONETICI,
                                RolAdi.HR, RolAdi.ADMIN)
                ),
                new EkipmanRouteCase(
                        "kendi ekipman talepleri - personel ve hr",
                        "/api/equipment-requests/my",
                        Set.of(RolAdi.PERSONEL, RolAdi.HR)
                ),
                new EkipmanRouteCase(
                        "takim ekipman talepleri - yonetici",
                        "/api/equipment-requests/team",
                        Set.of(RolAdi.YONETICI)
                ),
                new EkipmanRouteCase(
                        "uygun ekipmanlar - yonetici ve admin",
                        "/api/equipment-requests/" + talepId
                                + "/available-equipment",
                        Set.of(RolAdi.YONETICI, RolAdi.ADMIN)
                ),
                new EkipmanRouteCase(
                        "admin ekipman talepleri - admin",
                        "/api/admin/equipment-requests",
                        Set.of(RolAdi.ADMIN)
                ),
                new EkipmanRouteCase(
                        "admin ekipman tipleri - admin",
                        "/api/admin/equipment-types",
                        Set.of(RolAdi.ADMIN)
                ),
                new EkipmanRouteCase(
                        "admin ekipman envanteri - admin",
                        "/api/admin/equipment",
                        Set.of(RolAdi.ADMIN)
                ),
                new EkipmanRouteCase(
                        "kendi zimmetleri - tum roller",
                        "/api/assignments/my",
                        Set.of(RolAdi.PERSONEL, RolAdi.YONETICI,
                                RolAdi.HR, RolAdi.ADMIN)
                ),
                new EkipmanRouteCase(
                        "admin zimmetleri - admin",
                        "/api/admin/assignments",
                        Set.of(RolAdi.ADMIN)
                )
        );
    }

    private record EkipmanRouteCase(
            String ad,
            String path,
            Set<RolAdi> allowedRoles
    ) {
        @Override
        public String toString() {
            return ad;
        }
    }
}
