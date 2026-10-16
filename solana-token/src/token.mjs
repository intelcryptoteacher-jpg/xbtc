import { PublicKey, SystemProgram, Transaction } from '@solana/web3.js';
import { MINT_SIZE, TOKEN_PROGRAM_ID, getAssociatedTokenAddressSync, createInitializeMint2Instruction, createAssociatedTokenAccountIdempotentInstruction, createMintToCheckedInstruction, createSetAuthorityInstruction, createTransferCheckedInstruction, AuthorityType } from '@solana/spl-token';

export const DECIMALS = 9;
export const UNIT = 10n ** 9n;
export const SUPPLY = 10_000_000_000n * UNIT;
export function signatureText(bytes) {
  const alphabet = '123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz';
  let value = 0n, out = '', zeros = 0;
  for (const byte of bytes) value = value * 256n + BigInt(byte);
  for (const byte of bytes) { if (byte !== 0) break; zeros++; }
  while (value) { out = alphabet[Number(value % 58n)] + out; value /= 58n; }
  return '1'.repeat(zeros) + out;
}
export function parseAmount(text) {
  if (!/^(0|[1-9]\d*)(\.\d{1,9})?$/.test(text)) throw Error('Enter a decimal amount with at most nine decimal places.');
  const [whole, fraction = ''] = text.split('.');
  const value = BigInt(whole) * UNIT + BigInt(fraction.padEnd(9, '0'));
  if (value <= 0n || value > SUPPLY) throw Error('Amount must be positive and no greater than 10 billion XBTC.');
  return value;
}
export function formatAmount(value) {
  const fraction = (value % UNIT).toString().padStart(9, '0').replace(/0+$/, '');
  return (value / UNIT).toLocaleString('en-US') + (fraction ? '.' + fraction : '');
}
export function recipientKey(text) {
  let key;
  try { key = new PublicKey(text); } catch { throw Error('Enter a valid Solana wallet address. Bitcoin addresses cannot be used.'); }
  if (!PublicKey.isOnCurve(key.toBytes()) || key.equals(SystemProgram.programId)) throw Error('Use a regular Solana wallet address.');
  return key;
}
export function issueTransaction(owner, mint, rent) {
  const ata = getAssociatedTokenAddressSync(mint, owner);
  return new Transaction().add(
    SystemProgram.createAccount({ fromPubkey: owner, newAccountPubkey: mint, lamports: rent, space: MINT_SIZE, programId: TOKEN_PROGRAM_ID }),
    createInitializeMint2Instruction(mint, DECIMALS, owner, null),
    createAssociatedTokenAccountIdempotentInstruction(owner, ata, owner, mint),
    createMintToCheckedInstruction(mint, ata, owner, SUPPLY, DECIMALS),
    createSetAuthorityInstruction(mint, owner, AuthorityType.MintTokens, null)
  );
}
export function transferTransaction(owner, recipient, mint, amount) {
  if (amount <= 0n || amount > SUPPLY) throw Error('Invalid transfer amount.');
  const source = getAssociatedTokenAddressSync(mint, owner);
  const destination = getAssociatedTokenAddressSync(mint, recipient);
  return new Transaction().add(
    createAssociatedTokenAccountIdempotentInstruction(owner, destination, recipient, mint),
    createTransferCheckedInstruction(source, mint, destination, owner, amount, DECIMALS)
  );
}
