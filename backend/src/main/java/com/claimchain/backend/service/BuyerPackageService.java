package com.claimchain.backend.service;

import com.claimchain.backend.model.AnonymizedClaimView;
import com.claimchain.backend.model.Package;
import com.claimchain.backend.model.PackageStatus;
import com.claimchain.backend.model.Purchase;
import com.claimchain.backend.model.PurchaseStatus;
import com.claimchain.backend.model.Role;
import com.claimchain.backend.repository.AnonymizedClaimViewRepository;
import com.claimchain.backend.repository.BuyerEntitlementRepository;
import com.claimchain.backend.repository.PackageRepository;
import com.claimchain.backend.repository.PurchaseRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class BuyerPackageService {

    private final PackageRepository packageRepository;
    private final AnonymizedClaimViewRepository anonymizedClaimViewRepository;
    private final BuyerEntitlementRepository buyerEntitlementRepository;
    private final PurchaseRepository purchaseRepository;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public BuyerPackageService(
            PackageRepository packageRepository,
            AnonymizedClaimViewRepository anonymizedClaimViewRepository,
            BuyerEntitlementRepository buyerEntitlementRepository,
            PurchaseRepository purchaseRepository,
            AuditService auditService,
            ObjectMapper objectMapper
    ) {
        this.packageRepository = packageRepository;
        this.anonymizedClaimViewRepository = anonymizedClaimViewRepository;
        this.buyerEntitlementRepository = buyerEntitlementRepository;
        this.purchaseRepository = purchaseRepository;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<Package> listListedPackages() {
        return packageRepository.findByStatusOrderByCreatedAtDesc(PackageStatus.LISTED);
    }

    @Transactional(readOnly = true)
    public ListedPackageDetail getListedPackageWithAnonymizedViews(Long packageId) {
        if (packageId == null) {
            throw new PackageService.PackageNotFoundException("Package not found.");
        }

        Package packageEntity = packageRepository.findById(packageId)
                .orElseThrow(() -> new PackageService.PackageNotFoundException("Package not found."));

        if (packageEntity.getStatus() != PackageStatus.LISTED) {
            throw new PackageService.PackageNotFoundException("Package not found.");
        }

        List<AnonymizedClaimView> anonymizedViews = anonymizedClaimViewRepository
                .findByPackageIdOrderByScoreTotalDesc(packageId);

        return new ListedPackageDetail(packageEntity, anonymizedViews);
    }

    @Transactional(readOnly = true)
    public List<PurchasedPackageSummary> listPurchasedPackages(Long buyerUserId) {
        if (buyerUserId == null) {
            return List.of();
        }

        List<Purchase> paidPurchases = purchaseRepository
                .findByBuyerUserIdAndStatusOrderByUpdatedAtDesc(buyerUserId, PurchaseStatus.PAID);
        Map<Long, PurchasedPackageSummary> uniqueByPackageId = new LinkedHashMap<>();

        for (Purchase purchase : paidPurchases) {
            Package packageEntity = purchase.getPackageEntity();
            Long packageId = packageEntity == null ? null : packageEntity.getId();
            if (packageId == null || uniqueByPackageId.containsKey(packageId)) {
                continue;
            }
            if (!buyerEntitlementRepository.existsByPackageEntityIdAndBuyerUserId(packageId, buyerUserId)) {
                continue;
            }
            uniqueByPackageId.put(
                    packageId,
                    new PurchasedPackageSummary(packageEntity, purchase.getUpdatedAt())
            );
        }

        return List.copyOf(uniqueByPackageId.values());
    }

    @Transactional(readOnly = true)
    public PurchasedPackageDetail getPurchasedPackageWithAnonymizedViews(Long packageId, Long buyerUserId) {
        if (packageId == null || buyerUserId == null) {
            throw new PackageService.PackageNotFoundException("Package not found.");
        }
        if (!buyerEntitlementRepository.existsByPackageEntityIdAndBuyerUserId(packageId, buyerUserId)) {
            throw new PackageService.PackageNotFoundException("Package not found.");
        }

        Package packageEntity = packageRepository.findById(packageId)
                .orElseThrow(() -> new PackageService.PackageNotFoundException("Package not found."));
        List<AnonymizedClaimView> anonymizedViews = anonymizedClaimViewRepository
                .findByPackageIdOrderByScoreTotalDesc(packageId);
        Instant purchasedAt = purchaseRepository
                .findTopByBuyerUserIdAndPackageEntityIdAndStatusOrderByUpdatedAtDesc(
                        buyerUserId,
                        packageId,
                        PurchaseStatus.PAID
                )
                .map(Purchase::getUpdatedAt)
                .orElse(null);

        return new PurchasedPackageDetail(packageEntity, anonymizedViews, purchasedAt);
    }

    @Transactional
    public EntitledPackageExport exportEntitledPackage(Long packageId, Long buyerUserId) {
        if (packageId == null || buyerUserId == null) {
            throw new PackageService.PackageNotFoundException("Package not found.");
        }
        if (!buyerEntitlementRepository.existsByPackageEntityIdAndBuyerUserId(packageId, buyerUserId)) {
            throw new PackageService.PackageNotFoundException("Package not found.");
        }

        Package packageEntity = packageRepository.findById(packageId)
                .orElseThrow(() -> new PackageService.PackageNotFoundException("Package not found."));
        List<AnonymizedClaimView> anonymizedViews = anonymizedClaimViewRepository
                .findByPackageIdOrderByScoreTotalDesc(packageId);

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("buyerUserId", buyerUserId);
        metadata.put("packageId", packageId);
        metadata.put("claimCount", anonymizedViews.size());
        auditService.record(
                buyerUserId,
                Role.COLLECTION_AGENCY.name(),
                "PACKAGE_EXPORTED",
                "PACKAGE",
                packageId,
                toJson(metadata)
        );

        return new EntitledPackageExport(packageEntity, anonymizedViews);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize package export audit metadata.", ex);
        }
    }

    public static class ListedPackageDetail {
        private final Package packageEntity;
        private final List<AnonymizedClaimView> anonymizedViews;

        public ListedPackageDetail(Package packageEntity, List<AnonymizedClaimView> anonymizedViews) {
            this.packageEntity = packageEntity;
            this.anonymizedViews = anonymizedViews;
        }

        public Package getPackageEntity() {
            return packageEntity;
        }

        public List<AnonymizedClaimView> getAnonymizedViews() {
            return anonymizedViews;
        }
    }

    public static class EntitledPackageExport {
        private final Package packageEntity;
        private final List<AnonymizedClaimView> anonymizedViews;

        public EntitledPackageExport(Package packageEntity, List<AnonymizedClaimView> anonymizedViews) {
            this.packageEntity = packageEntity;
            this.anonymizedViews = anonymizedViews;
        }

        public Package getPackageEntity() {
            return packageEntity;
        }

        public List<AnonymizedClaimView> getAnonymizedViews() {
            return anonymizedViews;
        }
    }

    public static class PurchasedPackageSummary {
        private final Package packageEntity;
        private final Instant purchasedAt;

        public PurchasedPackageSummary(Package packageEntity, Instant purchasedAt) {
            this.packageEntity = packageEntity;
            this.purchasedAt = purchasedAt;
        }

        public Package getPackageEntity() {
            return packageEntity;
        }

        public Instant getPurchasedAt() {
            return purchasedAt;
        }
    }

    public static class PurchasedPackageDetail {
        private final Package packageEntity;
        private final List<AnonymizedClaimView> anonymizedViews;
        private final Instant purchasedAt;

        public PurchasedPackageDetail(
                Package packageEntity,
                List<AnonymizedClaimView> anonymizedViews,
                Instant purchasedAt
        ) {
            this.packageEntity = packageEntity;
            this.anonymizedViews = anonymizedViews;
            this.purchasedAt = purchasedAt;
        }

        public Package getPackageEntity() {
            return packageEntity;
        }

        public List<AnonymizedClaimView> getAnonymizedViews() {
            return anonymizedViews;
        }

        public Instant getPurchasedAt() {
            return purchasedAt;
        }
    }
}
