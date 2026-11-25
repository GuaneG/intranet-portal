package com.jforce.backend.exception;

import com.jforce.backend.model.dto.response.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.dao.OptimisticLockingFailureException;


import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;


import java.time.LocalDateTime;


//Tüm controller'ların exception handler kayıt defteri,sadece controller'a gelen isteklerin hatalarını yakalar.
// her exception handler için ex.getMessageYazıp ham exception message döndürme iç detay sızdırabilir.
@RestControllerAdvice
public class GlobalExceptionHandler {

    //ResponseEntity class for; body + status code
    @ExceptionHandler({BadCredentialsException.class})
    public ResponseEntity<ErrorResponse> handleBadCredentials(BadCredentialsException ex, HttpServletRequest request) {
        ErrorResponse body = new ErrorResponse(
                401,
                ex.getMessage(),
                LocalDateTime.now(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
    }

    @ExceptionHandler({AccessDeniedException.class})
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        ErrorResponse body = new ErrorResponse(
                403,
                "Bu işlem için yetkiniz bulunmuyor.",
                LocalDateTime.now(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
    }

    @ExceptionHandler({GecersizParolaException.class})
    public ResponseEntity<ErrorResponse> handleGecersizParola(GecersizParolaException ex, HttpServletRequest request) {
        ErrorResponse body = new ErrorResponse(
                400,
                ex.getMessage(),
                LocalDateTime.now(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler({GecersizIstekException.class})
    public ResponseEntity<ErrorResponse> handleGecersizIstek(GecersizIstekException ex, HttpServletRequest request) {
        ErrorResponse body = new ErrorResponse(
                400,
                ex.getMessage(),
                LocalDateTime.now(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler({DosyaCokBuyukException.class, MaxUploadSizeExceededException.class})
    public ResponseEntity<ErrorResponse> handleDosyaCokBuyuk(
            RuntimeException ex,
            HttpServletRequest request
    ) {
        ErrorResponse body = new ErrorResponse(
                413,
                "Profil fotoğrafı en fazla 2 MB olabilir.",
                LocalDateTime.now(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(body);
    }

    @ExceptionHandler(DesteklenmeyenDosyaTuruException.class)
    public ResponseEntity<ErrorResponse> handleDesteklenmeyenDosyaTuru(
            DesteklenmeyenDosyaTuruException ex,
            HttpServletRequest request
    ) {
        ErrorResponse body = new ErrorResponse(
                415,
                "Yalnız JPEG veya PNG profil fotoğrafı yüklenebilir.",
                LocalDateTime.now(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).body(body);
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ErrorResponse> handleMissingRequestPart(
            MissingServletRequestPartException ex,
            HttpServletRequest request
    ) {
        ErrorResponse body = new ErrorResponse(
                400,
                "Geçersiz istek",
                LocalDateTime.now(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse>
    handleMissingRequestParameter(MissingServletRequestParameterException ex, HttpServletRequest request) {
        ErrorResponse body = new ErrorResponse(
                400,
                "Geçersiz istek",
                LocalDateTime.now(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler({KaynakCakismasiException.class})
    public ResponseEntity<ErrorResponse> handleKaynakCakismasi(KaynakCakismasiException ex, HttpServletRequest request) {
        ErrorResponse body = new ErrorResponse(
                409,
                ex.getMessage(),
                LocalDateTime.now(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLockingFailure(
            OptimisticLockingFailureException ex,
            HttpServletRequest request
    ) {
        ErrorResponse body = new ErrorResponse(
                409,
                "Kayıt başka bir işlem tarafından güncellendi. Lütfen bilgileri yenileyip tekrar deneyin.",
                LocalDateTime.now(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(ParolaDegisikligiGerekliException.class)
    public ResponseEntity<ErrorResponse> handleParolaDegisikligiGerekli(
            ParolaDegisikligiGerekliException ex,
            HttpServletRequest request
    ) {
        ErrorResponse body = new ErrorResponse(
                428,
                ex.getMessage(),
                LocalDateTime.now(),
                request.getRequestURI()
        );

        return ResponseEntity.status(428).body(body);
    }

    @ExceptionHandler({RateLimitExceededException.class})
    public ResponseEntity<ErrorResponse> handleRateLimitExceeded(RateLimitExceededException ex, HttpServletRequest request) {
        ErrorResponse body = new ErrorResponse(
                429,
                ex.getMessage(),
                LocalDateTime.now(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(body);
    }

    @ExceptionHandler({InvalidRefreshTokenException.class})
    public ResponseEntity<ErrorResponse> handleInvalidRefreshToken(InvalidRefreshTokenException ex, HttpServletRequest request) {
        ErrorResponse body = new ErrorResponse(
                401,
                ex.getMessage(),
                LocalDateTime.now(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
    }


    @ExceptionHandler({NoResourceFoundException.class})
    public ResponseEntity<ErrorResponse> handleNoResourceFound(NoResourceFoundException ex, HttpServletRequest request) {
        ErrorResponse body = new ErrorResponse(
                404,
                "Böyle bir kaynak bulunamadı.",
                LocalDateTime.now(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    @ExceptionHandler({KaynakBulunamadiException.class})
    public ResponseEntity<ErrorResponse> handleException(KaynakBulunamadiException ex, HttpServletRequest request) {
        ErrorResponse body = new ErrorResponse(
                404,
                ex.getMessage(),
                LocalDateTime.now(),
                request.getRequestURI()
        );
        return  ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        ErrorResponse body = new ErrorResponse(
                400,
                "Geçersiz istek",
                LocalDateTime.now(),
                request.getRequestURI());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(
            ConstraintViolationException ex,
            HttpServletRequest request
    ) {
        ErrorResponse body = new ErrorResponse(
                400,
                "Geçersiz istek",
                LocalDateTime.now(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        ErrorResponse body = new ErrorResponse(
                400,
                "Geçersiz istek",
                LocalDateTime.now(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleHttpMessageNotReadable(HttpMessageNotReadableException ex, HttpServletRequest request) {
        ErrorResponse body = new ErrorResponse(
                400,
                "Geçersiz istek",
                LocalDateTime.now(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    //Fallback method
    @ExceptionHandler({Exception.class})
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex, HttpServletRequest request) {
        ErrorResponse body = new ErrorResponse(
                500,
                "Beklenmeyen bir hata oluştu.",
                LocalDateTime.now(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}
