package com.jforce.backend;

import com.jforce.backend.exception.GlobalExceptionHandler;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new OptimisticLockTestController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void optimisticLockCakismasiGuvenli409Doner() throws Exception {
        mockMvc.perform(get("/test/optimistic-lock"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value(
                        "Kayıt başka bir işlem tarafından güncellendi. Lütfen bilgileri yenileyip tekrar deneyin."
                ))
                .andExpect(jsonPath("$.path").value("/test/optimistic-lock"));
    }

    @Test
    void queryConstraintIhlaliGuvenli400Doner() throws Exception {
        String hamViolationMesaji =
                "kendiTalepleriniGetir.page: must be greater than or equal to 0";

        String responseBody = mockMvc.perform(get("/test/constraint-violation"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Geçersiz istek"))
                .andExpect(jsonPath("$.path").value(
                        "/test/constraint-violation"
                ))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertFalse(responseBody.contains(hamViolationMesaji));
        assertFalse(responseBody.contains("kendiTalepleriniGetir.page"));
    }

    @Test
    void unrelatedRuntimeHatasiGenericGuvenli500OlarakKalir()
            throws Exception {
        String hamRuntimeMesaji =
                "SQL constraint ve dahili servis ayrıntısı sızmamalıdır.";

        String responseBody = mockMvc.perform(get("/test/runtime-error"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.message").value(
                        "Beklenmeyen bir hata oluştu."
                ))
                .andExpect(jsonPath("$.path").value("/test/runtime-error"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertFalse(responseBody.contains(hamRuntimeMesaji));
    }

    @Test
    void beklenmeyenPersistenceHatasiGenericGuvenli500Doner()
            throws Exception {
        String hamPersistenceMesaji =
                "Duplicate entry ve dahili SQL constraint ayrıntısı";

        String responseBody = mockMvc.perform(get("/test/persistence-error"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.message").value(
                        "Beklenmeyen bir hata oluştu."
                ))
                .andExpect(jsonPath("$.path").value("/test/persistence-error"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertFalse(responseBody.contains(hamPersistenceMesaji));
        assertFalse(responseBody.contains("Duplicate entry"));
        assertFalse(responseBody.contains("constraint"));
    }

    @RestController
    private static class OptimisticLockTestController {

        @GetMapping("/test/optimistic-lock")
        void optimisticLockCakismasi() {
            throw new OptimisticLockingFailureException(
                    "SQL ve entity ayrıntısı istemciye sızmamalıdır."
            );
        }

        @GetMapping("/test/constraint-violation")
        void constraintViolation() {
            throw new ConstraintViolationException(
                    "kendiTalepleriniGetir.page: must be greater than or equal to 0",
                    Set.of()
            );
        }

        @GetMapping("/test/runtime-error")
        void runtimeError() {
            throw new RuntimeException(
                    "SQL constraint ve dahili servis ayrıntısı sızmamalıdır."
            );
        }

        @GetMapping("/test/persistence-error")
        void persistenceError() {
            throw new DataIntegrityViolationException(
                    "Duplicate entry ve dahili SQL constraint ayrıntısı"
            );
        }
    }
}
