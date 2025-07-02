package com.claimchain.backend.controller;

import com.claimchain.backend.model.Claim;
import com.claimchain.backend.repository.ClaimRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/claims")
public class ClaimController {

    @Autowired
    private ClaimRepository claimRepository;

    @GetMapping
    public List<Claim> getAllClaims() {
        return claimRepository.findAll();
    }

    @PostMapping
    public Claim submitClaim(@RequestBody Claim claim) {
        return claimRepository.save(claim);
    }
}
