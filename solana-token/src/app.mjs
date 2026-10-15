import './polyfill.mjs';
import { Connection, Keypair, PublicKey, clusterApiUrl } from '@solana/web3.js';
import { getMint, getAccount, getAssociatedTokenAddressSync, MINT_SIZE, TokenAccountNotFoundError } from '@solana/spl-token';
import { SUPPLY, DECIMALS, issueTransaction, transferTransaction, recipientKey, parseAmount, formatAmount, signatureText } from './token.mjs';

const $ = id => document.getElementById(id);
const connection = new Connection(clusterApiUrl('devnet'), 'confirmed');
let wallet, owner, busy = false, uncertain = false, verifiedMint = null;
const pendingKey = 'xbtc-solana-devnet-pending-v1';
function say(message) { $('status').textContent = message; }
function controls() {
  $('connect').disabled = busy;
  $('wallet').disabled = busy;
  for (const id of ['issue','load','transfer']) $(id).disabled = busy || !owner || (uncertain && id !== 'load');
  $('mint').disabled = busy;
  $('recipient').disabled = busy;
  $('amount').disabled = busy;
  $('recover').disabled = busy;
}
function reset() { owner = null; verifiedMint = null; $('account').textContent = 'Reconnect wallet'; $('balance').textContent = 'No verified balance'; controls(); }
function provider() {
  const options = { solflare:window.solflare, backpack:window.backpack?.solana || window.backpack, phantom:window.phantom?.solana };
  return options[$('wallet').value];
}
async function guard() {
  if (!owner || !wallet?.publicKey?.equals(owner)) throw Error('Wallet account changed. Connect again.');
  // The RPC is fixed to devnet; signing cannot redirect submission to mainnet.
  if (await connection.getGenesisHash() !== 'EtWTRABZaYq6iMfeYKouRu166VU2xqa1') throw Error('Unexpected network. Devnet only.');
}
async function action(fn) {
  if (busy) return;
  busy = true; controls();
  try { await fn(); } catch (e) { say(e.message || 'Request failed. No success confirmed.'); }
  finally { busy = false; controls(); }
}
function explorer(signature) {
  $('transaction').href = 'https://explorer.solana.com/tx/' + encodeURIComponent(signature) + '?cluster=devnet';
  $('transaction').hidden = false;
}
async function submit(tx, mintSigner, mintAddress) {
  await guard();
  const latest = await connection.getLatestBlockhash('confirmed');
  tx.feePayer = owner;
  tx.recentBlockhash = latest.blockhash;
  if (mintSigner) tx.partialSign(mintSigner);
  const expected = tx.serializeMessage();
  say('Review the devnet transaction in your wallet.');
  const signed = await wallet.signTransaction(tx);
  if (!signed.serializeMessage().equals(expected)) throw Error('Wallet returned a changed transaction. Submission stopped.');
  await guard();
  // Save public recovery data before broadcast so an RPC timeout cannot lead to blind retries.
  const raw = signed.serialize();
  const record = {mint:mintAddress.toBase58(), blockhash:latest.blockhash, lastValidBlockHeight:latest.lastValidBlockHeight, signature:signatureText(signed.signature)};
  localStorage.setItem(pendingKey, JSON.stringify(record));
  uncertain = true;
  explorer(record.signature);
  const signature = await connection.sendRawTransaction(raw, {skipPreflight:false,maxRetries:3});
  if (signature !== record.signature) throw Error('RPC returned an unexpected signature. Check the saved transaction before retrying.');
  explorer(signature);
  say('Submitted on devnet; waiting for confirmation. Do not retry.');
  const confirmation = await connection.confirmTransaction({...latest,signature}, 'confirmed');
  if (confirmation.value.err) {
    localStorage.removeItem(pendingKey); uncertain = false;
    throw Error('Transaction failed on-chain: '+JSON.stringify(confirmation.value.err));
  }
  localStorage.removeItem(pendingKey); uncertain = false;
  return signature;
}
async function load() {
  await guard();
  const mint = new PublicKey($('mint').value.trim());
  const info = await getMint(connection, mint);
  if (info.decimals !== DECIMALS || info.mintAuthority !== null || info.freezeAuthority !== null || info.supply > SUPPLY) throw Error('This mint does not match the test token settings.');
  // Matching settings do not establish provenance; users must verify the saved mint address.
  let amount = 0n;
  try { amount = (await getAccount(connection, getAssociatedTokenAddressSync(mint, owner))).amount; }
  catch (e) { if (!(e instanceof TokenAccountNotFoundError)) throw e; }
  verifiedMint = mint;
  $('balance').textContent = formatAmount(amount) + ' test XBTC · associated token account';
  $('supply').textContent = 'Current on-chain supply: '+formatAmount(info.supply)+' · minting disabled';
  return amount;
}
$('connect').onclick = () => action(async () => {
  reset(); wallet = provider();
  if (!wallet?.connect || !wallet?.signTransaction) throw Error('Open this page inside the selected Solana wallet browser, or use its browser extension. No wallet secrets belong here.');
  await wallet.connect(); owner = wallet.publicKey;
  await guard();
  wallet.on?.('accountChanged', reset); wallet.on?.('disconnect', reset);
  $('account').textContent = owner.toBase58();
  say('Connected. Select devnet in your wallet. Devnet SOL is required for fees and account creation.');
});
$('issue').onclick = () => action(async () => {
  await guard();
  if ($('mint').value.trim()) throw Error('A mint address is already entered. Load it rather than creating a duplicate.');
  if (!confirm('Create a new Solana DEVNET token, issue 10 billion test XBTC to '+owner.toBase58()+', and permanently disable further minting?')) return;
  const mint = Keypair.generate();
  const tx = issueTransaction(owner,mint.publicKey,await connection.getMinimumBalanceForRentExemption(MINT_SIZE));
  // Only the new public mint address is persisted. Wallet keys stay in the wallet.
  $('mint').value = mint.publicKey.toBase58();
  localStorage.setItem('xbtc-solana-devnet-mint-v1', $('mint').value);
  const sig = await submit(tx,mint,mint.publicKey);
  await load();
  say('Confirmed on devnet: 10 billion test XBTC issued. Save the mint address. Transaction: '+sig);
});
$('load').onclick = () => action(async () => { await load(); say('Balance read from devnet. Confirm the mint address against your original issuance record.'); });
$('transfer').onclick = () => action(async () => {
  const balance = await load();
  const recipient = recipientKey($('recipient').value.trim());
  const amount = parseAmount($('amount').value.trim());
  if (amount > balance) throw Error('Insufficient test XBTC balance.');
  if (!confirm('Transfer '+formatAmount(amount)+' test XBTC to '+recipient.toBase58()+' on DEVNET?')) return;
  const sig = await submit(transferTransaction(owner,recipient,verifiedMint,amount),null,verifiedMint);
  await load(); say('Transfer confirmed on devnet: '+sig);
});
$('recover').onclick = () => action(async () => {
  const raw = localStorage.getItem(pendingKey);
  if (!raw) {uncertain = false; say('No pending transaction saved.'); return;}
  const record = JSON.parse(raw);
  $('mint').value = record.mint;
  if (record.signature) {
    explorer(record.signature);
    const status = (await connection.getSignatureStatuses([record.signature],{searchTransactionHistory:true})).value[0];
    if (status?.err || ['confirmed','finalized'].includes(status?.confirmationStatus)) {
      localStorage.removeItem(pendingKey); uncertain = false;
      say(status.err ? 'Transaction failed; check the explorer.' : 'Transaction confirmed. Load the balance to inspect the result.'); return;
    }
  }
  say('Submission outcome is unresolved. Check the mint and transaction in Solana Explorer before retrying. Issuance and transfers remain locked.');
});
try {
  $('mint').value = localStorage.getItem('xbtc-solana-devnet-mint-v1') || '';
  uncertain = !!localStorage.getItem(pendingKey);
  if (uncertain) say('A previous submission needs checking. Select Check pending transaction.');
} catch { say('Browser storage is unavailable. Enable it before issuing tokens.'); }
controls();
