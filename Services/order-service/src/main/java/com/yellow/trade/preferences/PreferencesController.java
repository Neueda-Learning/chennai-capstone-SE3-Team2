package com.yellow.trade.preferences;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The customer's own preferences, as openapi/preferences.yaml describes. Under
 * /api/v1/, so the token filter has verified the token; the service decides
 * whether this caller may reach this account.
 */
@RestController
@RequestMapping("/api/v1/accounts")
@Validated
public class PreferencesController {

    private final PreferenceService preferences;

    public PreferencesController(PreferenceService preferences) {
        this.preferences = preferences;
    }

    @GetMapping("/{id}/preferences")
    public Preferences get(@PathVariable("id") @Min(1) long accountId) {
        return preferences.get(accountId);
    }

    @PutMapping("/{id}/preferences")
    public Preferences save(@PathVariable("id") @Min(1) long accountId, @Valid @RequestBody PreferencesUpdate update) {
        return preferences.save(accountId, update);
    }
}
