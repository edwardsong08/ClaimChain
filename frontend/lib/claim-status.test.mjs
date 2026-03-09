import assert from "node:assert/strict";
import { canShowRescore, isReviewStateStatus } from "./claim-status.js";

assert.equal(isReviewStateStatus("UNDER_REVIEW"), true);
assert.equal(isReviewStateStatus("REVIEW"), true);
assert.equal(isReviewStateStatus("APPROVED"), false);
assert.equal(isReviewStateStatus("SUBMITTED"), false);

assert.equal(canShowRescore("UNDER_REVIEW"), false);
assert.equal(canShowRescore("REVIEW"), false);
assert.equal(canShowRescore("SUBMITTED"), false);

assert.equal(canShowRescore("APPROVED"), true);
assert.equal(canShowRescore("REJECTED"), true);
