/**
 * XBTC operator CLI. Runs only on the operator's own machine.
 * No signing keys in GitHub, the Android WebView, the API or environment variables.
 *
 * Steps: plan -> create -> mint -> status -> revoke (optional, irreversible).
 * Solana devnet is the default; mainnet requires an explicit opt-in string.
 */
import { readFile, writeFile, mkdir, rename } from "node:fs/promises";
import { homedir } from "node:os";
import { resolve, join } from "node:path";
import { Connection, Keypair, PublicKey } from "@solana/web3.js";
import {
  AuthorityType, createMint, getMint, getOrCreateAssociatedTokenAccount,
  mintToChecked, setAuthority, TOKEN_PROGRAM_ID,
} from "@solana/spl-token";
import {
  TOKEN_NAME, TOTAL_SUPPLY, DECIMALS, tokenBaseUnits,
  configuredNetwork, assertMainnetApproval,
} from "./config.mjs";

const command = process.argv[2] || "plan";
const { name: network, rpc } = configuredNetwork(process.env.XBTC_NETWORK);
const recordDirectory = resolve(process.env.XBTC_LOCAL_DIR || join(homedir(), ".xbtc-solana", network));
const recordFile = join(recordDirectory, "mint-record.json");
const mintSignerFile = join(recordDirectory, "mint-account-keypair.json");
const walletPath = process.env.XBTC_OPERATOR_KEYPAIR;

function explorer(address) {
  return `https://explorer.solana.com/address/${address}?cluster=${network === "devnet" ? "devnet" : "mainnet-beta"}`;
}
function output(data) {
  console.log(JSON.stringify(data, (_key, value) => typeof value === "bigint" ? value.toString() : value, 2));
}
async function readJson(path) {
  try { return JSON.parse(await readFile(path, "utf8")); }
  catch (err) { if (err.code === "ENOENT") return null; throw err; }
}
async function loadOperator() {
  if (!walletPath) throw new Error("Set XBTC_OPERATOR_KEYPAIR to the path of YOUR local Solana CLI keypair file (not a seed phrase).");
  const data = await readJson(resolve(walletPath));
  if (!Array.isArray(data) || data.length !== 64 || data.some(n => !Number.isInteger(n) || n < 0 || n > 255)) {
    throw new Error("Operator keypair must be a 64-byte Solana CLI keypair JSON file");
  }
  return Keypair.fromSecretKey(Uint8Array.from(data));
}
async function prepareDirectory() {
  await mkdir(recordDirectory, { recursive: true, mode: 0o700 });
}
async function loadRecord() {
  const record = await readJson(recordFile);
  if (!record) throw new Error(`No XBTC mint record. First run create. Expected: ${recordFile}`);
  if (record.network !== network || record.decimals !== DECIMALS ||
      record.supply !== TOTAL_SUPPLY.toString()) {
    throw new Error("Local mint record does not match this network or XBTC supply configuration");
  }
  return record;
}
async function confirmOnChainMint(connection, record) {
  const mint = new PublicKey(record.mint);
  const state = await getMint(connection, mint, "confirmed", TOKEN_PROGRAM_ID);
  if (state.decimals !== DECIMALS) throw new Error("On-chain token decimals differ from XBTC parameters");
  return { mint, state };
}
async function run() {
  if (!["plan", "create", "mint", "status", "revoke"].includes(command)) {
    throw new Error("Use: node xbtc.mjs plan|create|mint|status|revoke");
  }
  if (command === "plan") {
    output({ token: TOKEN_NAME, network, rpc, supplyTokens: TOTAL_SUPPLY.toString(),
      decimals: DECIMALS, amountBaseUnits: tokenBaseUnits().toString(),
      recordDirectory, keypairConfigured: Boolean(walletPath),
      next: "Use 'create' to deploy a real SPL mint, then 'mint' to issue exactly 10 billion tokens. Nothing has been sent." });
    return;
  }
  if (["create", "mint", "revoke"].includes(command)) {
    assertMainnetApproval(network, process.env.XBTC_MAINNET_APPROVED);
  }
  const connection = new Connection(rpc, "confirmed");
  // This is a real RPC request, not evidence that a new token has been created.
  const genesisHash = await connection.getGenesisHash();
  if (command === "status") {
    const record = await readJson(recordFile);
    if (!record) {
      output({ network, connected: true, genesisHash, tokenMintCreated: false, note: "No local mint record" });
      return;
    }
    if (record.network !== network) throw new Error("Mint record belongs to a different network");
    const { state } = await confirmOnChainMint(connection, record);
    output({ network, connected: true, genesisHash, mintAddress: record.mint,
      supplyBaseUnits: state.supply.toString(), supplyTokens: (state.supply / 10n ** BigInt(DECIMALS)).toString(),
      decimals: state.decimals, mintAuthority: state.mintAuthority?.toBase58() || null,
      supplyVerified: state.supply === tokenBaseUnits(), explorer: explorer(record.mint) });
    return;
  }
  const payer = await loadOperator();
  if (command === "create") {
    await prepareDirectory();
    let record = await readJson(recordFile);
    if (!record) {
      // Persist mint signer *before* sending so a failed/retried run can't silently
      // generate a second mint. Both files are private and outside the repository.
      let mintKeypair;
      const savedSigner = await readJson(mintSignerFile);
      if (savedSigner) {
        mintKeypair = Keypair.fromSecretKey(Uint8Array.from(savedSigner));
      } else {
        mintKeypair = Keypair.generate();
        await writeFile(mintSignerFile, JSON.stringify(Array.from(mintKeypair.secretKey)), { flag: "wx", mode: 0o600 });
      }
      record = { network, mint: mintKeypair.publicKey.toBase58(),
        operator: payer.publicKey.toBase58(), supply: TOTAL_SUPPLY.toString(), decimals: DECIMALS };
      await writeFile(recordFile, JSON.stringify(record, null, 2) + "\n", { flag: "wx", mode: 0o600 });
    }
    if (record.operator !== payer.publicKey.toBase58() || record.network !== network) {
      throw new Error("Existing mint record is associated with a different operator or network");
    }
    if (record.supply !== TOTAL_SUPPLY.toString() || record.decimals !== DECIMALS) {
      throw new Error("Existing mint record has unexpected supply or decimal settings");
    }
    const signer = await readJson(mintSignerFile);
    if (!signer) throw new Error("Mint signer file missing; do not create a replacement mint");
    const mintKeypair = Keypair.fromSecretKey(Uint8Array.from(signer));
    if (mintKeypair.publicKey.toBase58() !== record.mint) throw new Error("Mint signer differs from saved public record");
    const info = await connection.getAccountInfo(mintKeypair.publicKey, "confirmed");
    if (info) {
      await confirmOnChainMint(connection, record);
      output({ network, alreadyCreated: true, mint: record.mint, explorer: explorer(record.mint) });
      return;
    }
    const address = await createMint(connection, payer, payer.publicKey, null, DECIMALS,
      mintKeypair, { commitment: "confirmed" }, TOKEN_PROGRAM_ID);
    output({ network, created: true, mint: address.toBase58(), supplyTokens: "0",
      mintAuthority: payer.publicKey.toBase58(), explorer: explorer(address.toBase58()),
      next: "Run mint after verifying the mint address. No XBTC has been issued yet." });
    return;
  }
  const record = await loadRecord();
  if (record.operator !== payer.publicKey.toBase58()) throw new Error("Operator wallet does not match mint authority record");
  const { mint, state } = await confirmOnChainMint(connection, record);
  if (command === "mint") {
    if (!state.mintAuthority?.equals(payer.publicKey)) throw new Error("This wallet is not the current mint authority");
    if (state.supply !== 0n) {
      throw new Error(`Refusing to mint again: on-chain supply already ${state.supply} base units. Check status before doing anything else.`);
    }
    const tokenAccount = await getOrCreateAssociatedTokenAccount(connection, payer, mint,
      payer.publicKey, false, "confirmed", { commitment: "confirmed" }, TOKEN_PROGRAM_ID);
    const signature = await mintToChecked(connection, payer, mint, tokenAccount.address,
      payer, tokenBaseUnits(), DECIMALS, [], { commitment: "confirmed" }, TOKEN_PROGRAM_ID);
    const verified = await getMint(connection, mint, "confirmed", TOKEN_PROGRAM_ID);
    if (verified.supply !== tokenBaseUnits()) {
      throw new Error("Transaction submitted but expected supply not yet confirmed. Run status; do not retry blindly.");
    }
    output({ minted: true, network, mintAddress: record.mint, tokenAccount: tokenAccount.address.toBase58(),
      supplyTokens: TOTAL_SUPPLY.toString(), transactionSignature: signature, explorer: explorer(record.mint),
      mintAuthorityStillActive: true, next: "If you want a permanent fixed supply, review and explicitly run revoke." });
    return;
  }
  // Revocation is permanently irreversible and requires a second, separate opt-in.
  if (process.env.XBTC_REVOKE_APPROVED !== "I_ACCEPT_PERMANENT_REVOCATION") {
    throw new Error("Permanent revocation requires XBTC_REVOKE_APPROVED=I_ACCEPT_PERMANENT_REVOCATION");
  }
  if (state.supply !== tokenBaseUnits()) throw new Error("Refusing to revoke minting authority before exact 10B supply is verified");
  if (!state.mintAuthority?.equals(payer.publicKey)) throw new Error("Operator is not active mint authority");
  const signature = await setAuthority(connection, payer, mint, payer,
    AuthorityType.MintTokens, null, [], { commitment: "confirmed" }, TOKEN_PROGRAM_ID);
  const verified = await getMint(connection, mint, "confirmed", TOKEN_PROGRAM_ID);
  if (verified.mintAuthority !== null) throw new Error("Authority revocation was submitted but is not yet confirmed; run status.");
  output({ network, mintAddress: record.mint, irrevocablyFixedSupply: true,
    supplyTokens: TOTAL_SUPPLY.toString(), transactionSignature: signature, explorer: explorer(record.mint) });
}
run().catch(err => { console.error("XBTC Solana action stopped:", err.message); process.exitCode = 1; });
