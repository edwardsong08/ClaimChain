package com.claimchain.backend.controller;

import com.claimchain.backend.dto.AnonymizedClaimViewResponseDTO;
import com.claimchain.backend.dto.BuyerPackageDetailResponseDTO;
import com.claimchain.backend.dto.BuyerPackageSummaryResponseDTO;
import com.claimchain.backend.model.AnonymizedClaimView;
import com.claimchain.backend.model.Package;
import com.claimchain.backend.service.BuyerPackageService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/buyer")
public class BuyerController {

    private final BuyerPackageService buyerPackageService;

    public BuyerController(BuyerPackageService buyerPackageService) {
        this.buyerPackageService = buyerPackageService;
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
}
