package com.josh.catalog.api;

import com.josh.catalog.service.SkillNotFoundException;
import com.josh.catalog.skill.InvalidSkillException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * FR-01 exception: "Skill is missing a name, description, or body: it's rejected
 * with an explanation; nothing partial is stored." This is the "explanation" part.
 * FR-03 exception: "The skill doesn't exist: the assistant returns a clear
 * 'not found.'"
 * MaxUploadSizeExceededException: phase2_design_specification.md Immediate
 * Fixes — enforces the "small, text-based artifact" assumption instead of
 * silently accepting oversized archives.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(InvalidSkillException.class)
    public ResponseEntity<Map<String, String>> handleInvalidSkill(InvalidSkillException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(SkillNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleSkillNotFound(SkillNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, String>> handleMaxUploadSizeExceeded(MaxUploadSizeExceededException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(Map.of("error", "Uploaded archive exceeds the maximum allowed size"));
    }
}
