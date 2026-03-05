package com.claimchain.backend.service;

import com.claimchain.backend.model.AnonymizedClaimView;
import com.claimchain.backend.model.Package;
import com.claimchain.backend.model.PackageStatus;
import com.claimchain.backend.repository.AnonymizedClaimViewRepository;
import com.claimchain.backend.repository.PackageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class BuyerPackageService {

    private final PackageRepository packageRepository;
    private final AnonymizedClaimViewRepository anonymizedClaimViewRepository;

    public BuyerPackageService(
            PackageRepository packageRepository,
            AnonymizedClaimViewRepository anonymizedClaimViewRepository
    ) {
        this.packageRepository = packageRepository;
        this.anonymizedClaimViewRepository = anonymizedClaimViewRepository;
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

        // Step 9: insert buyer entitlement checks here before returning package data.
        if (packageEntity.getStatus() != PackageStatus.LISTED) {
            throw new PackageService.PackageNotFoundException("Package not found.");
        }

        List<AnonymizedClaimView> anonymizedViews = anonymizedClaimViewRepository
                .findByPackageIdOrderByScoreTotalDesc(packageId);

        return new ListedPackageDetail(packageEntity, anonymizedViews);
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
}
