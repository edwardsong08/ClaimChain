import assert from "node:assert/strict";
import { canShowRescore } from "./claim-status.js";

assert.equal(canShowRescore("UNDER_REVIEW"), false);
assert.equal(canShowRescore("REVIEW"), false);
assert.equal(canShowRescore("SUBMITTED"), false);

assert.equal(canShowRescore("APPROVED"), true);
assert.equal(canShowRescore("REJECTED"), true);
