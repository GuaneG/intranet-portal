package com.jforce.backend.exception;

import com.jforce.backend.model.dto.response.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;


import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

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

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        ErrorResponse body = new ErrorResponse(
                400,
                "Geçersiz istek",
                LocalDateTime.now(),
                request.getRequestURI());
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
