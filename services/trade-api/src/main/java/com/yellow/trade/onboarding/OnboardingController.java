package com.yellow.trade.onboarding;

import com.yellow.trade.dto.ErrorResponse;
import com.yellow.trade.onboarding.OnboardingService.DuplicateApplicationException;
import com.yellow.trade.onboarding.OnboardingService.InvalidApplicationException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The one public route that creates data. It sits outside /api/v1 on purpose:
 * the JWT filter covers /api/v1/* only, so this needs no exception carved into
 * it, and "everything under /api/v1 is authenticated" stays true.
 *
 * A new customer and a duplicate get the same 202 with the same body, so the
 * route cannot be used to find out who holds an account. Nothing personal is
 * logged.
 */
@RestController
@RequestMapping("/onboarding")
public class OnboardingController {

    private static final Logger log = LoggerFactory.getLogger(OnboardingController.class);

    private final OnboardingService onboarding;
    private final ApplicationRateLimiter limiter;

    public OnboardingController(OnboardingService onboarding, ApplicationRateLimiter limiter) {
        this.onboarding = onboarding;
        this.limiter = limiter;
    }

    @PostMapping("/applications")
    public ResponseEntity<?> apply(@Valid @RequestBody ApplicationRequest request, HttpServletRequest http) {
        if (!limiter.tryAcquire(http.getRemoteAddr())) {
            log.warn("RATE-429: a caller passed the onboarding limit");
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(new ErrorResponse("RATE-429", "Too many applications. Try again later."));
        }
        try {
            long clientId = onboarding.apply(request);
            log.info("application accepted: client {} created, KYC pending", clientId);
        } catch (DuplicateApplicationException e) {
            log.info("application not created: {}", e.getMessage());
        } catch (InvalidApplicationException e) {
            log.warn("VAL-422: application refused: {}", e.getMessage());
            return ResponseEntity.unprocessableEntity().body(new ErrorResponse("VAL-422", "Invalid input"));
        }
        return ResponseEntity.accepted().body(ApplicationReceived.INSTANCE);
    }
}
