// Immutable XBTC token configuration; no keys, wallets, or fees are stored here.
export const TOKEN_NAME = "XBTC";
export const TOTAL_SUPPLY = 10_000_000_000n;
export const DECIMALS = 6;
export const NETWORKS = Object.freeze({
  devnet: "https://api.devnet.solana.com",
  "mainnet-beta": "https://api.mainnet-beta.solana.com",
});
export function tokenBaseUnits(supply = TOTAL_SUPPLY, decimals = DECIMALS) {
  if (typeof supply !== "bigint" || supply < 0n || !Number.isInteger(decimals) || decimals < 0 || decimals > 9) {
    throw new Error("Invalid supply or token decimals");
  }
  const raw = supply * 10n ** BigInt(decimals);
  if (raw > (1n << 64n) - 1n) throw new Error("SPL u64 mint amount overflow");
  return raw;
}
export function configuredNetwork(value) {
  const name = value || "devnet";
  if (!Object.hasOwn(NETWORKS, name)) throw new Error("XBTC_NETWORK must be devnet or mainnet-beta");
  return { name, rpc: NETWORKS[name] };
}
export function assertMainnetApproval(network, approval) {
  if (network === "mainnet-beta" && approval !== "I_AUTHORIZE_REAL_XBTC_MINT") {
    throw new Error("Mainnet deployment requires XBTC_MAINNET_APPROVED=I_AUTHORIZE_REAL_XBTC_MINT");
  }
}
