package com.claimchain.backend.controller;

import com.claimchain.backend.dto.RulesetRequestDTO;
import com.claimchain.backend.dto.RulesetResponseDTO;
import com.claimchain.backend.model.Ruleset;
import com.claimchain.backend.model.RulesetType;
import com.claimchain.backend.service.RulesetService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/admin/rulesets")
@PreAuthorize("hasRole('ADMIN')")
public class AdminRulesetController {

    private final RulesetService rulesetService;

    public AdminRulesetController(RulesetService rulesetService) {
        this.rulesetService = rulesetService;
    }

    @GetMapping("/{type}/active")
    public ResponseEntity<RulesetResponseDTO> getActiveRuleset(@PathVariable RulesetType type) {
        Ruleset active = rulesetService.getActive(type)
                .orElseThrow(() -> new IllegalArgumentException("No ACTIVE ruleset found for type: " + type.name()));
        return ResponseEntity.ok(toResponse(active));
    }

    @GetMapping("/{type}")
    public ResponseEntity<List<RulesetResponseDTO>> listRulesets(@PathVariable RulesetType type) {
        List<RulesetResponseDTO> response = rulesetService.listByType(type)
                .stream()
                .map(this::toResponse)
                .toList();
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{type}/draft")
    public ResponseEntity<RulesetResponseDTO> createDraft(
            @PathVariable RulesetType type,
            @Valid @RequestBody RulesetRequestDTO request,
            Principal principal
    ) {
        Ruleset draft = rulesetService.createDraft(type, request.getConfigJson(), principal.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(draft));
    }

    @PostMapping("/{id}/activate")
    public ResponseEntity<RulesetResponseDTO> activateRuleset(@PathVariable Long id, Principal principal) {
        Ruleset activated = rulesetService.activate(id, principal.getName());
        return ResponseEntity.ok(toResponse(activated));
    }

    private RulesetResponseDTO toResponse(Ruleset ruleset) {
        RulesetResponseDTO dto = new RulesetResponseDTO();
        dto.setId(ruleset.getId());
        dto.setType(ruleset.getType() == null ? null : ruleset.getType().name());
        dto.setStatus(ruleset.getStatus() == null ? null : ruleset.getStatus().name());
        dto.setVersion(ruleset.getVersion());
        dto.setConfigJson(ruleset.getConfigJson());
        dto.setCreatedByUserId(ruleset.getCreatedByUser() == null ? null : ruleset.getCreatedByUser().getId());
        dto.setCreatedAt(ruleset.getCreatedAt());
        dto.setActivatedByUserId(ruleset.getActivatedByUser() == null ? null : ruleset.getActivatedByUser().getId());
        dto.setActivatedAt(ruleset.getActivatedAt());
        return dto;
    }
}
