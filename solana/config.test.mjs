import assert from "node:assert/strict";
import test from "node:test";
import {TOTAL_SUPPLY, DECIMALS, tokenBaseUnits, configuredNetwork, assertMainnetApproval} from "./config.mjs";

test("fixed XBTC supply is exactly 10 billion with 6 decimals", () => {
  assert.equal(TOTAL_SUPPLY, 10_000_000_000n);
  assert.equal(DECIMALS, 6);
  assert.equal(tokenBaseUnits(), 10_000_000_000_000_000n);
});
test("overflow / malformed supplies rejected", () => {
  assert.throws(() => tokenBaseUnits(20_000_000_000n, 9));
  assert.throws(() => tokenBaseUnits(-1n, 6));
  assert.throws(() => tokenBaseUnits(5, 6));
});
test("only fixed official Solana RPC networks accepted", () => {
  assert.equal(configuredNetwork().name, "devnet");
  assert.equal(configuredNetwork("mainnet-beta").name, "mainnet-beta");
  assert.throws(() => configuredNetwork("https://random-server.invalid"));
});
test("mainnet requires an explicit typed authorization", () => {
  assert.doesNotThrow(() => assertMainnetApproval("devnet"));
  assert.throws(() => assertMainnetApproval("mainnet-beta"));
  assert.doesNotThrow(() => assertMainnetApproval("mainnet-beta", "I_AUTHORIZE_REAL_XBTC_MINT"));
});
