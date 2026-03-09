package com.claimchain.backend.scoring;

import com.claimchain.backend.model.Claim;
import com.claimchain.backend.model.ClaimDocument;
import com.claimchain.backend.model.ClaimStatus;
import com.claimchain.backend.model.DebtorType;
import com.claimchain.backend.model.DisputeStatus;
import com.claimchain.backend.model.DocumentType;
import com.claimchain.backend.model.ExtractionStatus;
import com.claimchain.backend.model.Ruleset;
import com.claimchain.backend.model.RulesetStatus;
import com.claimchain.backend.model.RulesetType;
import com.claimchain.backend.repository.ClaimDocumentRepository;
import com.claimchain.backend.repository.ClaimRepository;
import com.claimchain.backend.repository.ClaimScoreRepository;
import com.claimchain.backend.repository.RulesetRepository;
import com.claimchain.backend.service.ClaimScoringPersistenceService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScoringEngineUnitTest {

    @Mock
    private ClaimRepository claimRepository;

    @Mock
    private ClaimDocumentRepository claimDocumentRepository;

    @Mock
    private RulesetRepository rulesetRepository;

    @Mock
    private ClaimScoreRepository claimScoreRepository;

    @Mock
    private ClaimScoringPersistenceService claimScoringPersistenceService;

    private ObjectMapper objectMapper;
    private ScoringEngine scoringEngine;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        scoringEngine = new ScoringEngine(
                claimRepository,
                claimDocumentRepository,
                claimScoreRepository,
                rulesetRepository,
                claimScoringPersistenceService,
                objectMapper
        );
    }

    @Test
    void scoreClaim_isDeterministicForSameInputs() throws Exception {
        Long claimId = 101L;
        Claim claim = buildApprovedClaim(claimId, DisputeStatus.NONE, new BigDecimal("2500.00"));
        List<ClaimDocument> documents = List.of(
                buildDocument(DocumentType.INVOICE, ExtractionStatus.SUCCEEDED),
                buildDocument(DocumentType.CONTRACT, ExtractionStatus.SUCCEEDED)
        );

        Ruleset ruleset = buildActiveRuleset(11L, 1, validScoringConfigV1());

        when(claimRepository.findById(claimId)).thenReturn(Optional.of(claim));
        when(claimDocumentRepository.findByClaimId(claimId)).thenReturn(documents);
        when(rulesetRepository.findFirstByTypeAndStatus(RulesetType.SCORING, RulesetStatus.ACTIVE))
                .thenReturn(Optional.of(ruleset));
        when(claimScoringPersistenceService.recordScoreRun(
                eq(claimId),
                eq(11L),
                eq(1),
                anyBoolean(),
                anyInt(),
                anyString(),
                any(),
                any(),
                any(),
                any(),
                anyString(),
                anyString(),
                eq(7L),
                eq(ScoringTrigger.APPROVAL)
        )).thenAnswer(invocation -> null);

        scoringEngine.scoreClaim(claimId, 7L, false);
        scoringEngine.scoreClaim(claimId, 7L, false);

        ArgumentCaptor<Boolean> eligibleCaptor = ArgumentCaptor.forClass(Boolean.class);
        ArgumentCaptor<Integer> scoreCaptor = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<String> gradeCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> explainabilityCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> featureSnapshotCaptor = ArgumentCaptor.forClass(String.class);

        verify(claimScoringPersistenceService, times(2)).recordScoreRun(
                eq(claimId),
                eq(11L),
                eq(1),
                eligibleCaptor.capture(),
                scoreCaptor.capture(),
                gradeCaptor.capture(),
                any(),
                any(),
                any(),
                any(),
                explainabilityCaptor.capture(),
                featureSnapshotCaptor.capture(),
                eq(7L),
                eq(ScoringTrigger.APPROVAL)
        );

        assertThat(eligibleCaptor.getAllValues().get(0)).isEqualTo(eligibleCaptor.getAllValues().get(1));
        assertThat(scoreCaptor.getAllValues().get(0)).isEqualTo(scoreCaptor.getAllValues().get(1));
        assertThat(gradeCaptor.getAllValues().get(0)).isEqualTo(gradeCaptor.getAllValues().get(1));
        assertThat(explainabilityCaptor.getAllValues().get(0)).isEqualTo(explainabilityCaptor.getAllValues().get(1));
        assertThat(featureSnapshotCaptor.getAllValues().get(0)).isEqualTo(featureSnapshotCaptor.getAllValues().get(1));

        JsonNode explainability = objectMapper.readTree(explainabilityCaptor.getAllValues().get(0));
        assertThat(explainability.path("contributions").isArray()).isTrue();
        assertThat(explainability.path("contributions").size()).isGreaterThan(0);
    }

    @Test
    void scoreClaim_selectsGradeBandFromTotalScore() {
        Long claimId = 202L;
        Claim claim = buildApprovedClaim(claimId, DisputeStatus.NONE, new BigDecimal("1200.00"));
        List<ClaimDocument> documents = List.of(buildDocument(DocumentType.INVOICE, ExtractionStatus.SUCCEEDED));
        Ruleset ruleset = buildActiveRuleset(22L, 3, gradeSelectionConfig());

        when(claimRepository.findById(claimId)).thenReturn(Optional.of(claim));
        when(claimDocumentRepository.findByClaimId(claimId)).thenReturn(documents);
        when(rulesetRepository.findFirstByTypeAndStatus(RulesetType.SCORING, RulesetStatus.ACTIVE))
                .thenReturn(Optional.of(ruleset));
        when(claimScoringPersistenceService.recordScoreRun(
                eq(claimId),
                eq(22L),
                eq(3),
                anyBoolean(),
                anyInt(),
                anyString(),
                any(),
                any(),
                any(),
                any(),
                anyString(),
                anyString(),
                eq(99L),
                eq(ScoringTrigger.APPROVAL)
        )).thenAnswer(invocation -> null);

        scoringEngine.scoreClaim(claimId, 99L, false);

        ArgumentCaptor<Integer> scoreCaptor = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<String> gradeCaptor = ArgumentCaptor.forClass(String.class);
        verify(claimScoringPersistenceService).recordScoreRun(
                eq(claimId),
                eq(22L),
                eq(3),
                eq(true),
                scoreCaptor.capture(),
                gradeCaptor.capture(),
                any(),
                any(),
                any(),
                any(),
                anyString(),
                anyString(),
                eq(99L),
                eq(ScoringTrigger.APPROVAL)
        );

        assertThat(scoreCaptor.getValue()).isEqualTo(83);
        assertThat(gradeCaptor.getValue()).isEqualTo("B");
    }

    @Test
    void scoreClaim_allowsInvoiceOnlyClaimWhenEligibilityDocsAreConfigured() {
        Long claimId = 207L;
        Claim claim = buildApprovedClaim(claimId, DisputeStatus.NONE, new BigDecimal("2500.00"));
        List<ClaimDocument> documents = List.of(buildDocument(DocumentType.INVOICE, ExtractionStatus.SUCCEEDED));
        Ruleset ruleset = buildActiveRuleset(27L, 2, validScoringConfigV1());

        when(claimRepository.findById(claimId)).thenReturn(Optional.of(claim));
        when(claimDocumentRepository.findByClaimId(claimId)).thenReturn(documents);
        when(rulesetRepository.findFirstByTypeAndStatus(RulesetType.SCORING, RulesetStatus.ACTIVE))
                .thenReturn(Optional.of(ruleset));
        when(claimScoringPersistenceService.recordScoreRun(
                eq(claimId),
                eq(27L),
                eq(2),
                anyBoolean(),
                anyInt(),
                anyString(),
                any(),
                any(),
                any(),
                any(),
                anyString(),
                anyString(),
                eq(17L),
                eq(ScoringTrigger.APPROVAL)
        )).thenAnswer(invocation -> null);

        scoringEngine.scoreClaim(claimId, 17L, false);

        ArgumentCaptor<Boolean> eligibleCaptor = ArgumentCaptor.forClass(Boolean.class);
        ArgumentCaptor<Integer> scoreCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(claimScoringPersistenceService).recordScoreRun(
                eq(claimId),
                eq(27L),
                eq(2),
                eligibleCaptor.capture(),
                scoreCaptor.capture(),
                anyString(),
                any(),
                any(),
                any(),
                any(),
                anyString(),
                anyString(),
                eq(17L),
                eq(ScoringTrigger.APPROVAL)
        );

        assertThat(eligibleCaptor.getValue()).isTrue();
        assertThat(scoreCaptor.getValue()).isGreaterThan(0);
    }

    @Test
    void scoreClaim_allowsContractOnlyClaimWhenEligibilityDocsAreConfigured() {
        Long claimId = 208L;
        Claim claim = buildApprovedClaim(claimId, DisputeStatus.NONE, new BigDecimal("2500.00"));
        List<ClaimDocument> documents = List.of(buildDocument(DocumentType.CONTRACT, ExtractionStatus.SUCCEEDED));
        Ruleset ruleset = buildActiveRuleset(28L, 2, validScoringConfigV1());

        when(claimRepository.findById(claimId)).thenReturn(Optional.of(claim));
        when(claimDocumentRepository.findByClaimId(claimId)).thenReturn(documents);
        when(rulesetRepository.findFirstByTypeAndStatus(RulesetType.SCORING, RulesetStatus.ACTIVE))
                .thenReturn(Optional.of(ruleset));
        when(claimScoringPersistenceService.recordScoreRun(
                eq(claimId),
                eq(28L),
                eq(2),
                anyBoolean(),
                anyInt(),
                anyString(),
                any(),
                any(),
                any(),
                any(),
                anyString(),
                anyString(),
                eq(18L),
                eq(ScoringTrigger.APPROVAL)
        )).thenAnswer(invocation -> null);

        scoringEngine.scoreClaim(claimId, 18L, false);

        ArgumentCaptor<Boolean> eligibleCaptor = ArgumentCaptor.forClass(Boolean.class);
        ArgumentCaptor<Integer> scoreCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(claimScoringPersistenceService).recordScoreRun(
                eq(claimId),
                eq(28L),
                eq(2),
                eligibleCaptor.capture(),
                scoreCaptor.capture(),
                anyString(),
                any(),
                any(),
                any(),
                any(),
                anyString(),
                anyString(),
                eq(18L),
                eq(ScoringTrigger.APPROVAL)
        );

        assertThat(eligibleCaptor.getValue()).isTrue();
        assertThat(scoreCaptor.getValue()).isGreaterThan(0);
    }

    @Test
    void scoreClaim_allowsLowExtractionReadinessWithoutForcingIneligible() {
        Long claimId = 209L;
        Claim claim = buildApprovedClaim(claimId, DisputeStatus.NONE, new BigDecimal("2500.00"));
        List<ClaimDocument> documents = List.of(
                buildDocument(DocumentType.INVOICE, ExtractionStatus.NOT_STARTED),
                buildDocument(DocumentType.CONTRACT, ExtractionStatus.NOT_STARTED)
        );
        Ruleset ruleset = buildActiveRuleset(29L, 2, validScoringConfigV1());

        when(claimRepository.findById(claimId)).thenReturn(Optional.of(claim));
        when(claimDocumentRepository.findByClaimId(claimId)).thenReturn(documents);
        when(rulesetRepository.findFirstByTypeAndStatus(RulesetType.SCORING, RulesetStatus.ACTIVE))
                .thenReturn(Optional.of(ruleset));
        when(claimScoringPersistenceService.recordScoreRun(
                eq(claimId),
                eq(29L),
                eq(2),
                anyBoolean(),
                anyInt(),
                anyString(),
                any(),
                any(),
                any(),
                any(),
                anyString(),
                anyString(),
                eq(19L),
                eq(ScoringTrigger.APPROVAL)
        )).thenAnswer(invocation -> null);

        scoringEngine.scoreClaim(claimId, 19L, false);

        ArgumentCaptor<Boolean> eligibleCaptor = ArgumentCaptor.forClass(Boolean.class);
        ArgumentCaptor<Integer> scoreCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(claimScoringPersistenceService).recordScoreRun(
                eq(claimId),
                eq(29L),
                eq(2),
                eligibleCaptor.capture(),
                scoreCaptor.capture(),
                anyString(),
                any(),
                any(),
                any(),
                any(),
                anyString(),
                anyString(),
                eq(19L),
                eq(ScoringTrigger.APPROVAL)
        );

        assertThat(eligibleCaptor.getValue()).isTrue();
        assertThat(scoreCaptor.getValue()).isGreaterThan(0);
    }

    @Test
    void scoreClaim_docReadyWithInvoiceEvidence_addsVisibleBonusAndOutscoresComparableNonInvoiceCase() throws Exception {
        Long invoiceClaimId = 210L;
        Long nonInvoiceClaimId = 211L;
        Claim invoiceClaim = buildApprovedClaim(invoiceClaimId, DisputeStatus.NONE, new BigDecimal("2500.00"));
        Claim nonInvoiceClaim = buildApprovedClaim(nonInvoiceClaimId, DisputeStatus.NONE, new BigDecimal("2500.00"));

        List<ClaimDocument> invoiceEvidenceDocs = List.of(
                buildDocument(DocumentType.INVOICE, ExtractionStatus.SUCCEEDED, 160),
                buildDocument(DocumentType.CONTRACT, ExtractionStatus.SUCCEEDED, 120)
        );
        List<ClaimDocument> nonInvoiceDocs = List.of(
                buildDocument(DocumentType.CONTRACT, ExtractionStatus.SUCCEEDED, 120),
                buildDocument(DocumentType.OTHER, ExtractionStatus.SUCCEEDED, 120)
        );

        Ruleset ruleset = buildActiveRuleset(31L, 2, validScoringConfigV1());

        when(claimRepository.findById(invoiceClaimId)).thenReturn(Optional.of(invoiceClaim));
        when(claimRepository.findById(nonInvoiceClaimId)).thenReturn(Optional.of(nonInvoiceClaim));
        when(claimDocumentRepository.findByClaimId(invoiceClaimId)).thenReturn(invoiceEvidenceDocs);
        when(claimDocumentRepository.findByClaimId(nonInvoiceClaimId)).thenReturn(nonInvoiceDocs);
        when(rulesetRepository.findFirstByTypeAndStatus(RulesetType.SCORING, RulesetStatus.ACTIVE))
                .thenReturn(Optional.of(ruleset));
        when(claimScoringPersistenceService.recordScoreRun(
                any(),
                eq(31L),
                eq(2),
                anyBoolean(),
                anyInt(),
                anyString(),
                any(),
                any(),
                any(),
                any(),
                anyString(),
                anyString(),
                isNull(),
                eq(ScoringTrigger.DOC_READY)
        )).thenAnswer(invocation -> null);

        scoringEngine.scoreClaim(invoiceClaimId, null, false, ScoringTrigger.DOC_READY);
        scoringEngine.scoreClaim(nonInvoiceClaimId, null, false, ScoringTrigger.DOC_READY);

        ArgumentCaptor<Long> claimIdCaptor = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<Integer> scoreCaptor = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<String> explainabilityCaptor = ArgumentCaptor.forClass(String.class);
        verify(claimScoringPersistenceService, times(2)).recordScoreRun(
                claimIdCaptor.capture(),
                eq(31L),
                eq(2),
                eq(true),
                scoreCaptor.capture(),
                anyString(),
                any(),
                any(),
                any(),
                any(),
                explainabilityCaptor.capture(),
                anyString(),
                isNull(),
                eq(ScoringTrigger.DOC_READY)
        );

        int invoiceScore = -1;
        int nonInvoiceScore = -1;
        String invoiceExplainability = null;
        String nonInvoiceExplainability = null;
        for (int i = 0; i < claimIdCaptor.getAllValues().size(); i++) {
            Long persistedClaimId = claimIdCaptor.getAllValues().get(i);
            if (invoiceClaimId.equals(persistedClaimId)) {
                invoiceScore = scoreCaptor.getAllValues().get(i);
                invoiceExplainability = explainabilityCaptor.getAllValues().get(i);
            } else if (nonInvoiceClaimId.equals(persistedClaimId)) {
                nonInvoiceScore = scoreCaptor.getAllValues().get(i);
                nonInvoiceExplainability = explainabilityCaptor.getAllValues().get(i);
            }
        }

        assertThat(invoiceScore).isGreaterThan(nonInvoiceScore);

        JsonNode invoiceContributions = objectMapper.readTree(invoiceExplainability).path("contributions");
        assertThat(invoiceContributions.toString()).contains("Invoice document extracted successfully");
        assertThat(invoiceContributions.toString()).contains("Invoice evidence corroborated by extracted contract");

        JsonNode nonInvoiceContributions = objectMapper.readTree(nonInvoiceExplainability).path("contributions");
        assertThat(nonInvoiceContributions.toString()).contains("Extraction healthy");
        assertThat(nonInvoiceContributions.toString()).doesNotContain("Invoice document extracted successfully");
    }

    @Test
    void scoreClaim_adminRescoreWithExtractedDocs_matchesDocReadyEvidenceBonuses() throws Exception {
        Long docReadyClaimId = 215L;
        Long adminRescoreClaimId = 216L;
        Claim docReadyClaim = buildApprovedClaim(docReadyClaimId, DisputeStatus.NONE, new BigDecimal("2500.00"));
        Claim adminRescoreClaim = buildApprovedClaim(adminRescoreClaimId, DisputeStatus.NONE, new BigDecimal("2500.00"));

        List<ClaimDocument> extractedEvidenceDocs = List.of(
                buildDocument(DocumentType.INVOICE, ExtractionStatus.SUCCEEDED, 180),
                buildDocument(DocumentType.CONTRACT, ExtractionStatus.SUCCEEDED, 140)
        );
        Ruleset ruleset = buildActiveRuleset(35L, 3, validScoringConfigV1());

        when(claimRepository.findById(docReadyClaimId)).thenReturn(Optional.of(docReadyClaim));
        when(claimRepository.findById(adminRescoreClaimId)).thenReturn(Optional.of(adminRescoreClaim));
        when(claimDocumentRepository.findByClaimId(docReadyClaimId)).thenReturn(extractedEvidenceDocs);
        when(claimDocumentRepository.findByClaimId(adminRescoreClaimId)).thenReturn(extractedEvidenceDocs);
        when(rulesetRepository.findFirstByTypeAndStatus(RulesetType.SCORING, RulesetStatus.ACTIVE))
                .thenReturn(Optional.of(ruleset));
        when(claimScoringPersistenceService.recordScoreRun(
                any(),
                eq(35L),
                eq(3),
                anyBoolean(),
                anyInt(),
                anyString(),
                any(),
                any(),
                any(),
                any(),
                anyString(),
                anyString(),
                any(),
                any()
        )).thenAnswer(invocation -> null);

        scoringEngine.scoreClaim(docReadyClaimId, null, false, ScoringTrigger.DOC_READY);
        scoringEngine.scoreClaim(adminRescoreClaimId, 77L, true, ScoringTrigger.ADMIN_RESCORE);

        ArgumentCaptor<Long> claimIdCaptor = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<Integer> scoreCaptor = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<String> explainabilityCaptor = ArgumentCaptor.forClass(String.class);
        verify(claimScoringPersistenceService, times(2)).recordScoreRun(
                claimIdCaptor.capture(),
                eq(35L),
                eq(3),
                eq(true),
                scoreCaptor.capture(),
                anyString(),
                any(),
                any(),
                any(),
                any(),
                explainabilityCaptor.capture(),
                anyString(),
                any(),
                any()
        );

        Integer docReadyScore = null;
        Integer adminRescoreScore = null;
        String docReadyExplainability = null;
        String adminRescoreExplainability = null;
        for (int i = 0; i < claimIdCaptor.getAllValues().size(); i++) {
            Long persistedClaimId = claimIdCaptor.getAllValues().get(i);
            if (docReadyClaimId.equals(persistedClaimId)) {
                docReadyScore = scoreCaptor.getAllValues().get(i);
                docReadyExplainability = explainabilityCaptor.getAllValues().get(i);
            } else if (adminRescoreClaimId.equals(persistedClaimId)) {
                adminRescoreScore = scoreCaptor.getAllValues().get(i);
                adminRescoreExplainability = explainabilityCaptor.getAllValues().get(i);
            }
        }

        assertThat(docReadyScore).isNotNull();
        assertThat(adminRescoreScore).isNotNull();
        assertThat(adminRescoreScore).isEqualTo(docReadyScore);

        JsonNode docReadyExplainabilityJson = objectMapper.readTree(docReadyExplainability);
        JsonNode adminRescoreExplainabilityJson = objectMapper.readTree(adminRescoreExplainability);
        assertThat(docReadyExplainabilityJson.path("trigger").asText()).isEqualTo("DOC_READY");
        assertThat(adminRescoreExplainabilityJson.path("trigger").asText()).isEqualTo("ADMIN_RESCORE");
        assertThat(docReadyExplainabilityJson.path("contributions").toString())
                .contains("Invoice document extracted successfully")
                .contains("Invoice evidence corroborated by extracted contract");
        assertThat(adminRescoreExplainabilityJson.path("contributions").toString())
                .contains("Invoice document extracted successfully")
                .contains("Invoice evidence corroborated by extracted contract");
    }

    @Test
    void scoreClaim_approvalWithoutDocs_doesNotApplyDocReadyInvoiceEvidenceBonus() throws Exception {
        Long claimId = 212L;
        Claim claim = buildApprovedClaim(claimId, DisputeStatus.NONE, new BigDecimal("2500.00"));
        Ruleset ruleset = buildActiveRuleset(32L, 2, validScoringConfigV1());

        when(claimRepository.findById(claimId)).thenReturn(Optional.of(claim));
        when(claimDocumentRepository.findByClaimId(claimId)).thenReturn(List.of());
        when(rulesetRepository.findFirstByTypeAndStatus(RulesetType.SCORING, RulesetStatus.ACTIVE))
                .thenReturn(Optional.of(ruleset));
        when(claimScoringPersistenceService.recordScoreRun(
                eq(claimId),
                eq(32L),
                eq(2),
                anyBoolean(),
                anyInt(),
                anyString(),
                any(),
                any(),
                any(),
                any(),
                anyString(),
                anyString(),
                eq(88L),
                eq(ScoringTrigger.APPROVAL)
        )).thenAnswer(invocation -> null);

        scoringEngine.scoreClaim(claimId, 88L, false, ScoringTrigger.APPROVAL);

        ArgumentCaptor<String> explainabilityCaptor = ArgumentCaptor.forClass(String.class);
        verify(claimScoringPersistenceService).recordScoreRun(
                eq(claimId),
                eq(32L),
                eq(2),
                eq(true),
                anyInt(),
                anyString(),
                any(),
                any(),
                any(),
                any(),
                explainabilityCaptor.capture(),
                anyString(),
                eq(88L),
                eq(ScoringTrigger.APPROVAL)
        );

        JsonNode contributions = objectMapper.readTree(explainabilityCaptor.getValue()).path("contributions");
        assertThat(contributions.toString()).doesNotContain("Invoice document extracted successfully");
    }

    @Test
    void scoreClaim_adminRescoreWithoutDocs_doesNotApplyDocReadyInvoiceEvidenceBonus() throws Exception {
        Long claimId = 218L;
        Claim claim = buildApprovedClaim(claimId, DisputeStatus.NONE, new BigDecimal("2500.00"));
        Ruleset ruleset = buildActiveRuleset(36L, 2, validScoringConfigV1());

        when(claimRepository.findById(claimId)).thenReturn(Optional.of(claim));
        when(claimDocumentRepository.findByClaimId(claimId)).thenReturn(List.of());
        when(rulesetRepository.findFirstByTypeAndStatus(RulesetType.SCORING, RulesetStatus.ACTIVE))
                .thenReturn(Optional.of(ruleset));
        when(claimScoringPersistenceService.recordScoreRun(
                eq(claimId),
                eq(36L),
                eq(2),
                anyBoolean(),
                anyInt(),
                anyString(),
                any(),
                any(),
                any(),
                any(),
                anyString(),
                anyString(),
                eq(91L),
                eq(ScoringTrigger.ADMIN_RESCORE)
        )).thenAnswer(invocation -> null);

        scoringEngine.scoreClaim(claimId, 91L, true, ScoringTrigger.ADMIN_RESCORE);

        ArgumentCaptor<String> explainabilityCaptor = ArgumentCaptor.forClass(String.class);
        verify(claimScoringPersistenceService).recordScoreRun(
                eq(claimId),
                eq(36L),
                eq(2),
                eq(true),
                anyInt(),
                anyString(),
                any(),
                any(),
                any(),
                any(),
                explainabilityCaptor.capture(),
                anyString(),
                eq(91L),
                eq(ScoringTrigger.ADMIN_RESCORE)
        );

        JsonNode explainability = objectMapper.readTree(explainabilityCaptor.getValue());
        assertThat(explainability.path("trigger").asText()).isEqualTo("ADMIN_RESCORE");
        assertThat(explainability.path("contributions").toString()).doesNotContain("Invoice document extracted successfully");
    }

    @Test
    void scoreClaim_docReadyCommercialInvoiceEvidence_landsInViableBandAndAboveComparableNoDocCase() {
        Long docReadyClaimId = 213L;
        Long noDocApprovalClaimId = 214L;
        Claim docReadyClaim = buildApprovedClaim(docReadyClaimId, DisputeStatus.NONE, new BigDecimal("1400.00"));
        Claim noDocClaim = buildApprovedClaim(noDocApprovalClaimId, DisputeStatus.NONE, new BigDecimal("1400.00"));

        List<ClaimDocument> docReadyDocuments = List.of(
                buildDocument(DocumentType.INVOICE, ExtractionStatus.SUCCEEDED, 180),
                buildDocument(DocumentType.CONTRACT, ExtractionStatus.SUCCEEDED, 140)
        );
        Ruleset ruleset = buildActiveRuleset(34L, 4, businessBandCalibrationConfig());

        when(claimRepository.findById(docReadyClaimId)).thenReturn(Optional.of(docReadyClaim));
        when(claimRepository.findById(noDocApprovalClaimId)).thenReturn(Optional.of(noDocClaim));
        when(claimDocumentRepository.findByClaimId(docReadyClaimId)).thenReturn(docReadyDocuments);
        when(claimDocumentRepository.findByClaimId(noDocApprovalClaimId)).thenReturn(List.of());
        when(rulesetRepository.findFirstByTypeAndStatus(RulesetType.SCORING, RulesetStatus.ACTIVE))
                .thenReturn(Optional.of(ruleset));
        when(claimScoringPersistenceService.recordScoreRun(
                any(),
                eq(34L),
                eq(4),
                anyBoolean(),
                anyInt(),
                anyString(),
                any(),
                any(),
                any(),
                any(),
                anyString(),
                anyString(),
                isNull(),
                any()
        )).thenAnswer(invocation -> null);

        scoringEngine.scoreClaim(docReadyClaimId, null, false, ScoringTrigger.DOC_READY);
        scoringEngine.scoreClaim(noDocApprovalClaimId, null, false, ScoringTrigger.APPROVAL);

        ArgumentCaptor<Long> claimIdCaptor = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<Integer> scoreCaptor = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<String> gradeCaptor = ArgumentCaptor.forClass(String.class);
        verify(claimScoringPersistenceService, times(2)).recordScoreRun(
                claimIdCaptor.capture(),
                eq(34L),
                eq(4),
                eq(true),
                scoreCaptor.capture(),
                gradeCaptor.capture(),
                any(),
                any(),
                any(),
                any(),
                anyString(),
                anyString(),
                isNull(),
                any()
        );

        Integer docReadyScore = null;
        Integer noDocScore = null;
        String docReadyGrade = null;
        for (int i = 0; i < claimIdCaptor.getAllValues().size(); i++) {
            Long persistedClaimId = claimIdCaptor.getAllValues().get(i);
            if (docReadyClaimId.equals(persistedClaimId)) {
                docReadyScore = scoreCaptor.getAllValues().get(i);
                docReadyGrade = gradeCaptor.getAllValues().get(i);
            } else if (noDocApprovalClaimId.equals(persistedClaimId)) {
                noDocScore = scoreCaptor.getAllValues().get(i);
            }
        }

        assertThat(docReadyScore).isNotNull();
        assertThat(noDocScore).isNotNull();
        assertThat(docReadyScore).isGreaterThan(noDocScore);
        assertThat(docReadyScore).isGreaterThanOrEqualTo(45);
        assertThat(List.of("A", "B", "C")).contains(docReadyGrade);
    }

    @Test
    void scoreClaim_marksIneligibleClaimAsZeroAndF() throws Exception {
        Long claimId = 303L;
        Claim claim = buildApprovedClaim(claimId, DisputeStatus.ACTIVE, new BigDecimal("2000.00"));
        List<ClaimDocument> documents = List.of(
                buildDocument(DocumentType.INVOICE, ExtractionStatus.SUCCEEDED),
                buildDocument(DocumentType.CONTRACT, ExtractionStatus.SUCCEEDED)
        );
        Ruleset ruleset = buildActiveRuleset(33L, 5, validScoringConfigV1());

        when(claimRepository.findById(claimId)).thenReturn(Optional.of(claim));
        when(claimDocumentRepository.findByClaimId(claimId)).thenReturn(documents);
        when(rulesetRepository.findFirstByTypeAndStatus(RulesetType.SCORING, RulesetStatus.ACTIVE))
                .thenReturn(Optional.of(ruleset));
        when(claimScoringPersistenceService.recordScoreRun(
                eq(claimId),
                eq(33L),
                eq(5),
                anyBoolean(),
                anyInt(),
                anyString(),
                any(),
                any(),
                any(),
                any(),
                anyString(),
                anyString(),
                eq(55L),
                eq(ScoringTrigger.APPROVAL)
        )).thenAnswer(invocation -> null);

        scoringEngine.scoreClaim(claimId, 55L, false);

        ArgumentCaptor<Boolean> eligibleCaptor = ArgumentCaptor.forClass(Boolean.class);
        ArgumentCaptor<Integer> scoreCaptor = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<String> gradeCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> explainabilityCaptor = ArgumentCaptor.forClass(String.class);
        verify(claimScoringPersistenceService).recordScoreRun(
                eq(claimId),
                eq(33L),
                eq(5),
                eligibleCaptor.capture(),
                scoreCaptor.capture(),
                gradeCaptor.capture(),
                eq(0),
                eq(0),
                eq(0),
                eq(0),
                explainabilityCaptor.capture(),
                anyString(),
                eq(55L),
                eq(ScoringTrigger.APPROVAL)
        );

        assertThat(eligibleCaptor.getValue()).isFalse();
        assertThat(scoreCaptor.getValue()).isEqualTo(0);
        assertThat(gradeCaptor.getValue()).isEqualTo("F");

        JsonNode explainability = objectMapper.readTree(explainabilityCaptor.getValue());
        assertThat(explainability.path("eligibleReasons").isArray()).isTrue();
        assertThat(explainability.path("eligibleReasons").toString()).contains("Active dispute");
    }

    @Test
    void autoScoreOnApprovalIfReady_scoresApprovedClaimEvenWhenReadinessInputsAreMissing() {
        Long claimId = 404L;
        Claim claim = buildApprovedClaim(claimId, DisputeStatus.NONE, new BigDecimal("1800.00"));
        Ruleset ruleset = buildActiveRuleset(44L, 1, validScoringConfigV1());

        when(rulesetRepository.findFirstByTypeAndStatus(RulesetType.SCORING, RulesetStatus.ACTIVE))
                .thenReturn(Optional.of(ruleset));
        when(claimRepository.findById(claimId)).thenReturn(Optional.of(claim));
        when(claimDocumentRepository.findByClaimId(claimId)).thenReturn(List.of());
        when(claimScoringPersistenceService.recordScoreRun(
                eq(claimId),
                eq(44L),
                eq(1),
                anyBoolean(),
                anyInt(),
                anyString(),
                any(),
                any(),
                any(),
                any(),
                anyString(),
                anyString(),
                eq(42L),
                eq(ScoringTrigger.APPROVAL)
        )).thenAnswer(invocation -> null);

        boolean scored = scoringEngine.autoScoreOnApprovalIfReady(claimId, 42L);

        assertThat(scored).isTrue();
        ArgumentCaptor<Boolean> eligibleCaptor = ArgumentCaptor.forClass(Boolean.class);
        ArgumentCaptor<Integer> scoreCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(claimScoringPersistenceService, times(1)).recordScoreRun(
                eq(claimId),
                eq(44L),
                eq(1),
                eligibleCaptor.capture(),
                scoreCaptor.capture(),
                anyString(),
                any(),
                any(),
                any(),
                any(),
                anyString(),
                anyString(),
                eq(42L),
                eq(ScoringTrigger.APPROVAL)
        );
        assertThat(eligibleCaptor.getValue()).isTrue();
        assertThat(scoreCaptor.getValue()).isGreaterThan(0);
    }

    @Test
    void autoScoreOnApprovalIfReady_doesNotScoreWhenClaimIsNotApproved() {
        Long claimId = 505L;
        Claim claim = buildApprovedClaim(claimId, DisputeStatus.NONE, new BigDecimal("1200.00"));
        claim.setStatus(ClaimStatus.UNDER_REVIEW);
        Ruleset ruleset = buildActiveRuleset(55L, 1, validScoringConfigV1());

        when(rulesetRepository.findFirstByTypeAndStatus(RulesetType.SCORING, RulesetStatus.ACTIVE))
                .thenReturn(Optional.of(ruleset));
        when(claimRepository.findById(claimId)).thenReturn(Optional.of(claim));

        boolean scored = scoringEngine.autoScoreOnApprovalIfReady(claimId, 9L);

        assertThat(scored).isFalse();
        verifyNoInteractions(claimScoringPersistenceService);
    }

    @Test
    void autoScoreOnApprovalIfReady_returnsFalseWhenNoActiveRulesetExists() {
        Long claimId = 606L;
        when(rulesetRepository.findFirstByTypeAndStatus(RulesetType.SCORING, RulesetStatus.ACTIVE))
                .thenReturn(Optional.empty());

        boolean scored = scoringEngine.autoScoreOnApprovalIfReady(claimId, 77L);

        assertThat(scored).isFalse();
        verifyNoInteractions(claimScoringPersistenceService);
    }

    private Claim buildApprovedClaim(Long id, DisputeStatus disputeStatus, BigDecimal currentAmount) {
        Claim claim = new Claim();
        claim.setId(id);
        claim.setStatus(ClaimStatus.APPROVED);
        claim.setDisputeStatus(disputeStatus);
        claim.setDebtorType(DebtorType.BUSINESS);
        claim.setJurisdictionState("NY");
        claim.setDateOfDefault(LocalDate.now().minusDays(100));
        claim.setCurrentAmount(currentAmount);
        claim.setAmountOwed(currentAmount);
        return claim;
    }

    private ClaimDocument buildDocument(DocumentType type, ExtractionStatus extractionStatus) {
        return buildDocument(type, extractionStatus, null);
    }

    private ClaimDocument buildDocument(DocumentType type, ExtractionStatus extractionStatus, Integer extractedCharCount) {
        ClaimDocument document = new ClaimDocument();
        document.setDocumentType(type);
        document.setExtractionStatus(extractionStatus);
        document.setExtractedCharCount(extractedCharCount);
        return document;
    }

    private Ruleset buildActiveRuleset(Long id, int version, String configJson) {
        Ruleset ruleset = new Ruleset();
        ruleset.setType(RulesetType.SCORING);
        ruleset.setStatus(RulesetStatus.ACTIVE);
        ruleset.setVersion(version);
        ruleset.setConfigJson(configJson);
        try {
            var idField = Ruleset.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(ruleset, id);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
        return ruleset;
    }

    private String validScoringConfigV1() {
        return """
                {
                  "eligibility": {
                    "requiredClaimStatus": "APPROVED",
                    "requiredDocTypes": ["INVOICE", "CONTRACT"],
                    "minExtractionSuccessRate": 0.6,
                    "blockActiveDisputes": true
                  },
                  "weights": {
                    "enforceability": 0.35,
                    "documentation": 0.30,
                    "collectability": 0.25,
                    "operationalRisk": 0.10
                  },
                  "gradeBands": [
                    {"grade":"A","minScore":85},
                    {"grade":"B","minScore":70},
                    {"grade":"C","minScore":55},
                    {"grade":"D","minScore":40},
                    {"grade":"F","minScore":0}
                  ],
                  "caps": {
                    "enforceabilityMax": 35,
                    "documentationMax": 30,
                    "collectabilityMax": 25,
                    "operationalRiskMax": 10
                  },
                  "rules": [
                    {"id":"JURISDICTION_KNOWN","group":"enforceability","when":{"jurisdictionKnown":true},"points":10,"reason":"Jurisdiction provided"},
                    {"id":"DEBT_AGE_RECENT","group":"enforceability","when":{"debtAgeDaysLte":180},"points":8,"reason":"Recent debt"},
                    {"id":"EXTRACTION_SUCCESS_HIGH","group":"documentation","when":{"extractionSuccessRateGte":0.8},"points":8,"reason":"Extraction healthy"},
                    {"id":"DEBTOR_BUSINESS","group":"collectability","when":{"debtorTypeEquals":"BUSINESS"},"points":6,"reason":"Business debtor"},
                    {"id":"AMOUNT_MID","group":"collectability","when":{"currentAmountBetween":[1000,4999]},"points":6,"reason":"Mid amount"},
                    {"id":"DOC_COUNT_LOW","group":"operationalRisk","when":{"docCountGte":2},"points":3,"reason":"Doc count manageable"}
                  ]
                }
                """;
    }

    private String gradeSelectionConfig() {
        return """
                {
                  "eligibility": {
                    "requiredClaimStatus": "APPROVED",
                    "requiredDocTypes": [],
                    "minExtractionSuccessRate": 0.0,
                    "blockActiveDisputes": false
                  },
                  "weights": {
                    "enforceability": 0.35,
                    "documentation": 0.30,
                    "collectability": 0.25,
                    "operationalRisk": 0.10
                  },
                  "gradeBands": [
                    {"grade":"A","minScore":85},
                    {"grade":"B","minScore":70},
                    {"grade":"C","minScore":55},
                    {"grade":"D","minScore":40},
                    {"grade":"F","minScore":0}
                  ],
                  "caps": {
                    "enforceabilityMax": 100,
                    "documentationMax": 100,
                    "collectabilityMax": 100,
                    "operationalRiskMax": 100
                  },
                  "rules": [
                    {"id":"GRADE_RULE","group":"enforceability","when":{"jurisdictionKnown":true},"points":72,"reason":"Grade threshold rule"}
                  ]
                }
                """;
    }

    private String businessBandCalibrationConfig() {
        return """
                {
                  "eligibility": {
                    "requiredClaimStatus": "APPROVED",
                    "requiredDocTypes": [],
                    "minExtractionSuccessRate": 0.0,
                    "blockActiveDisputes": false
                  },
                  "weights": {
                    "enforceability": 0.35,
                    "documentation": 0.30,
                    "collectability": 0.25,
                    "operationalRisk": 0.10
                  },
                  "gradeBands": [
                    {"grade":"A","minScore":75},
                    {"grade":"B","minScore":60},
                    {"grade":"C","minScore":45},
                    {"grade":"D","minScore":30},
                    {"grade":"F","minScore":0}
                  ],
                  "caps": {
                    "enforceabilityMax": 100,
                    "documentationMax": 100,
                    "collectabilityMax": 100,
                    "operationalRiskMax": 100
                  },
                  "rules": [
                    {"id":"JURISDICTION_KNOWN","group":"enforceability","when":{"jurisdictionKnown":true},"points":8,"reason":"Jurisdiction provided"},
                    {"id":"DEBT_AGE_RECENT","group":"enforceability","when":{"debtAgeDaysLte":365},"points":7,"reason":"Recent debt"},
                    {"id":"EXTRACTION_SUCCESS_HIGH","group":"documentation","when":{"extractionSuccessRateGte":0.8},"points":8,"reason":"Extraction healthy"},
                    {"id":"DEBTOR_BUSINESS","group":"collectability","when":{"debtorTypeEquals":"BUSINESS"},"points":6,"reason":"Business debtor"},
                    {"id":"AMOUNT_MID","group":"collectability","when":{"currentAmountBetween":[500,5000]},"points":5,"reason":"Commercial balance range"},
                    {"id":"DOC_COUNT_BASE","group":"operationalRisk","when":{"docCountGte":2},"points":3,"reason":"Supporting document depth"}
                  ]
                }
                """;
    }
}
