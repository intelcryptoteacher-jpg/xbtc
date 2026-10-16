import test from 'node:test';
import assert from 'node:assert/strict';
import {Keypair} from '@solana/web3.js';
import {decodeMintToCheckedInstruction,decodeSetAuthorityInstruction,decodeTransferCheckedInstruction,decodeInitializeMint2Instruction} from '@solana/spl-token';
import {SUPPLY,issueTransaction,transferTransaction,parseAmount,formatAmount,recipientKey,signatureText} from '../src/token.mjs';
test('public transaction signature encoding preserves leading zeros',()=>{
  assert.equal(signatureText(new Uint8Array([0,0,1])),'112');
  assert.equal(signatureText(new Uint8Array([58])),'21');
  const key=Keypair.generate().publicKey;
  assert.equal(signatureText(key.toBytes()),key.toBase58());
});
test('10 billion at nine decimals fits u64 without precision loss',()=>{
  assert.equal(SUPPLY,10000000000000000000n); assert.ok(SUPPLY<2n**64n);
  assert.equal(parseAmount('10000000000'),SUPPLY);
  assert.equal(parseAmount('1.000000001'),1000000001n);
  assert.equal(formatAmount(SUPPLY),'10,000,000,000');
  for(const v of ['0','-1','1e9','1.0000000001','10000000001','NaN',''])assert.throws(()=>parseAmount(v));
});
test('issuance atomically creates, mints and removes mint authority',()=>{
  const owner=Keypair.generate().publicKey,mint=Keypair.generate().publicKey;
  const tx=issueTransaction(owner,mint,1461600);
  assert.equal(tx.instructions.length,5);
  const init=decodeInitializeMint2Instruction(tx.instructions[1]);
  assert.equal(init.data.decimals,9);assert.equal(init.data.freezeAuthority,null);
  const issue=decodeMintToCheckedInstruction(tx.instructions[3]);
  assert.equal(issue.data.amount,SUPPLY);assert.equal(issue.data.decimals,9);
  assert.ok(issue.keys.authority.pubkey.equals(owner));
  const revoke=decodeSetAuthorityInstruction(tx.instructions[4]);
  assert.equal(revoke.data.newAuthority,null);assert.equal(revoke.data.authorityType,0);
  tx.feePayer=owner;tx.recentBlockhash=Keypair.generate().publicKey.toBase58();
  assert.ok(tx.serialize({requireAllSignatures:false,verifySignatures:false}).length<=1232);
});
test('transfer creates recipient ATA and transfers exact base units',()=>{
  const owner=Keypair.generate().publicKey,recipient=Keypair.generate().publicKey,mint=Keypair.generate().publicKey;
  const tx=transferTransaction(owner,recipient,mint,1000000001n);
  assert.equal(tx.instructions.length,2);
  const transfer=decodeTransferCheckedInstruction(tx.instructions[1]);
  assert.equal(transfer.data.amount,1000000001n);assert.equal(transfer.data.decimals,9);
  assert.ok(transfer.keys.owner.pubkey.equals(owner));assert.ok(transfer.keys.mint.pubkey.equals(mint));
  assert.ok(recipientKey(recipient.toBase58()).equals(recipient));
  assert.throws(()=>recipientKey('bc1p982yufj5nnrpzukt5ahn5sfj0ew7rnwc3cauhfyqdr4fyccgp8uqu6v8un'));
});
