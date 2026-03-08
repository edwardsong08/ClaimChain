export type ClaimDocument = {
  id: number;
  filename: string;
  contentType?: string | null;
  sniffedContentType?: string | null;
  sizeBytes?: number | null;
  status?: string | null;
  documentType?: string | null;
  extractionStatus?: string | null;
  extractedCharCount?: number | null;
  createdAt?: string | null;
};

export type UploadClaimDocumentRequest = {
  claimId: string;
  file: File;
  documentType: string;
};

export type UploadClaimDocumentResponse = {
  docId: number;
  status: string;
  filename: string;
  size: number;
  sniffedType: string;
};
