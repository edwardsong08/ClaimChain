package com.claimchain.backend;

import com.claimchain.backend.model.Claim;
import com.claimchain.backend.model.Role;
import com.claimchain.backend.model.User;
import com.claimchain.backend.model.VerificationStatus;
import com.claimchain.backend.repository.ClaimRepository;
import com.claimchain.backend.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class ClaimOptimisticLockingIntegrationTest {

    @Autowired
    private ClaimRepository claimRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void claimVersion_incrementsOnUpdate() {
        User provider = createServiceProvider();
        Claim created = createClaim(provider);

        assertThat(created.getVersion()).isNotNull();
        assertThat(created.getVersion()).isEqualTo(0L);

        created.setServiceDescription("Updated service description");
        Claim updated = claimRepository.saveAndFlush(created);

        assertThat(updated.getVersion()).isEqualTo(1L);
    }

    @Test
    void staleWrite_throwsOptimisticLockingException() {
        User provider = createServiceProvider();
        Claim created = createClaim(provider);

        Claim claimA = claimRepository.findById(created.getId()).orElseThrow();
        Claim claimB = claimRepository.findById(created.getId()).orElseThrow();

        claimA.setServiceDescription("First update wins");
        Claim savedA = claimRepository.saveAndFlush(claimA);
        assertThat(savedA.getVersion()).isEqualTo(1L);

        claimB.setServiceDescription("Stale update should fail");
        assertThrows(
                ObjectOptimisticLockingFailureException.class,
                () -> claimRepository.saveAndFlush(claimB)
        );
    }

    private User createServiceProvider() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        User user = new User();
        user.setName("Optimistic Lock Provider " + suffix);
        user.setEmail("provider-" + suffix + "@claim-optimistic-lock-test.local");
        user.setPassword("test-password-hash");
        user.setRole(Role.SERVICE_PROVIDER);
        user.setVerificationStatus(VerificationStatus.APPROVED);
        user.setVerifiedAt(Instant.now());
        return userRepository.save(user);
    }

    private Claim createClaim(User owner) {
        Claim claim = new Claim();
        claim.setUser(owner);
        claim.setClientName("Optimistic Lock Client");
        claim.setClientContact("client@example.com");
        claim.setClientAddress("100 Lock St");
        claim.setDebtType("CONSUMER");
        claim.setContactHistory("Contacted via email");
        claim.setDateOfDefault(LocalDate.of(2026, 1, 15));
        claim.setServiceDescription("Initial service description");
        return claimRepository.saveAndFlush(claim);
    }
}
