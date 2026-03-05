package com.claimchain.backend.controller;

import com.claimchain.backend.dto.AnonymizedClaimViewResponseDTO;
import com.claimchain.backend.dto.BuyerCheckoutResponseDTO;
import com.claimchain.backend.dto.BuyerPackageDetailResponseDTO;
import com.claimchain.backend.dto.BuyerPackageSummaryResponseDTO;
import com.claimchain.backend.model.AnonymizedClaimView;
import com.claimchain.backend.model.Package;
import com.claimchain.backend.model.User;
import com.claimchain.backend.repository.UserRepository;
import com.claimchain.backend.service.BuyerPackageService;
import com.claimchain.backend.service.PurchaseService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;
import java.util.Locale;

@RestController
@RequestMapping("/api/buyer")
public class BuyerController {

    private final BuyerPackageService buyerPackageService;
    private final PurchaseService purchaseService;
    private final UserRepository userRepository;

    public BuyerController(
            BuyerPackageService buyerPackageService,
            PurchaseService purchaseService,
            UserRepository userRepository
    ) {
        this.buyerPackageService = buyerPackageService;
        this.purchaseService = purchaseService;
        this.userRepository = userRepository;
    }

    @GetMapping("/packages")
    @PreAuthorize("hasRole('COLLECTION_AGENCY')")
    public ResponseEntity<List<BuyerPackageSummaryResponseDTO>> listListedPackages() {
        List<BuyerPackageSummaryResponseDTO> response = buyerPackageService.listListedPackages()
                .stream()
                .map(this::toBuyerPackageSummary)
                .toList();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/packages/{id}")
    @PreAuthorize("hasRole('COLLECTION_AGENCY')")
    public ResponseEntity<BuyerPackageDetailResponseDTO> getListedPackage(@PathVariable Long id) {
        BuyerPackageService.ListedPackageDetail detail = buyerPackageService.getListedPackageWithAnonymizedViews(id);
        BuyerPackageDetailResponseDTO response = toBuyerPackageDetail(detail);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/packages/{id}/checkout")
    @PreAuthorize("hasRole('COLLECTION_AGENCY')")
    public ResponseEntity<BuyerCheckoutResponseDTO> checkoutPackage(
            @PathVariable Long id,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            Principal principal
    ) {
        Long buyerUserId = requirePrincipalUserId(principal);
        PurchaseService.CheckoutResult result = purchaseService.createCheckout(id, buyerUserId, idempotencyKey);
        BuyerCheckoutResponseDTO response = new BuyerCheckoutResponseDTO();
        response.setPurchaseId(result.getPurchaseId());
        response.setCheckoutSessionId(result.getCheckoutSessionId());
        response.setCheckoutUrl(result.getCheckoutUrl());
        return ResponseEntity.ok(response);
    }

    private BuyerPackageSummaryResponseDTO toBuyerPackageSummary(Package packageEntity) {
        BuyerPackageSummaryResponseDTO dto = new BuyerPackageSummaryResponseDTO();
        dto.setId(packageEntity.getId());
        dto.setTotalClaims(packageEntity.getTotalClaims());
        dto.setTotalFaceValue(packageEntity.getTotalFaceValue());
        dto.setCreatedAt(packageEntity.getCreatedAt());
        return dto;
    }

    private BuyerPackageDetailResponseDTO toBuyerPackageDetail(BuyerPackageService.ListedPackageDetail detail) {
        Package packageEntity = detail.getPackageEntity();
        List<AnonymizedClaimViewResponseDTO> claims = detail.getAnonymizedViews().stream()
                .map(this::toAnonymizedClaimViewResponse)
                .toList();

        BuyerPackageDetailResponseDTO dto = new BuyerPackageDetailResponseDTO();
        dto.setId(packageEntity.getId());
        dto.setTotalClaims(packageEntity.getTotalClaims());
        dto.setTotalFaceValue(packageEntity.getTotalFaceValue());
        dto.setCreatedAt(packageEntity.getCreatedAt());
        dto.setClaims(claims);
        return dto;
    }

    private AnonymizedClaimViewResponseDTO toAnonymizedClaimViewResponse(AnonymizedClaimView view) {
        AnonymizedClaimViewResponseDTO dto = new AnonymizedClaimViewResponseDTO();
        dto.setClaimId(view.getClaim() == null ? null : view.getClaim().getId());
        dto.setJurisdictionState(view.getJurisdictionState());
        dto.setDebtorType(view.getDebtorType());
        dto.setClaimType(view.getClaimType());
        dto.setDisputeStatus(view.getDisputeStatus());
        dto.setDebtAgeDays(view.getDebtAgeDays());
        dto.setAmountBand(view.getAmountBand());
        dto.setScoreTotal(view.getScoreTotal());
        dto.setGrade(view.getGrade());
        dto.setExtractionSuccessRate(view.getExtractionSuccessRate());
        dto.setDocTypesPresent(view.getDocTypesPresent());
        return dto;
    }

    private Long requirePrincipalUserId(Principal principal) {
        String normalizedEmail = principal == null || principal.getName() == null
                ? null
                : principal.getName().trim().toLowerCase(Locale.ROOT);
        User user = normalizedEmail == null ? null : userRepository.findByEmail(normalizedEmail);
        if (user == null) {
            throw new IllegalArgumentException("Buyer user not found.");
        }
        return user.getId();
    }
}
