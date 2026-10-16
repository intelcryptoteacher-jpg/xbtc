package com.xbtc.exchange;

import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Fixed, allowlisted Solana DEVNET-only transactions, generated natively.
 * No arbitrary transaction signing or mainnet RPC.
 *
 * This implementation is a development integration, NOT an audited wallet.
 */
public final class SolanaDevnet {
    private static final String RPC = "https://api.devnet.solana.com";
    private static final String SYSTEM = "11111111111111111111111111111111";
    private static final String TOKEN = "TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA";
    private static final long XBTC_AMOUNT_BASE = 10_000_000_000_000_000L;
    private static final int MINT_SPACE = 82;
    private static final int TOKEN_ACCOUNT_SPACE = 165;

    private SolanaDevnet() {}

    private static Object rpc(String method, JSONArray params) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(RPC).openConnection();
        c.setRequestMethod("POST");
        c.setRequestProperty("Content-Type", "application/json");
        c.setConnectTimeout(12000);
        c.setReadTimeout(20000);
        c.setDoOutput(true);
        try {
            JSONObject request = new JSONObject().put("jsonrpc", "2.0").put("id", 1)
                    .put("method", method).put("params", params);
            byte[] body = request.toString().getBytes(StandardCharsets.UTF_8);
            try (OutputStream out = c.getOutputStream()) { out.write(body); }
            InputStream stream = c.getResponseCode() >= 400 ? c.getErrorStream() : c.getInputStream();
            if (stream == null) throw new IllegalStateException("RPC has no response body");
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            try (InputStream in = stream) {
                int read;
                while ((read = in.read(buf)) != -1) {
                    if (bytes.size() + read > 256000) throw new IllegalStateException("RPC response too large");
                    bytes.write(buf, 0, read);
                }
            }
            JSONObject response = new JSONObject(new String(bytes.toByteArray(), StandardCharsets.UTF_8));
            if (!response.isNull("error") && response.has("error")) {
                throw new IllegalStateException("Devnet RPC error: " + response.getJSONObject("error").optString("message", "Unknown"));
            }
            if (!response.has("result")) throw new IllegalStateException("Devnet RPC returned no result");
            return response.get("result");
        } finally { c.disconnect(); }
    }
    public static long balance(String address) throws Exception {
        JSONObject result = (JSONObject) rpc("getBalance", new JSONArray()
                .put(address).put(new JSONObject().put("commitment", "confirmed")));
        return result.getLong("value");
    }
    public static String airdropOneSol(String address) throws Exception {
        // Devnet faucet can be rate limited. Do not automate retries.
        return (String) rpc("requestAirdrop", new JSONArray()
                .put(address).put(1_000_000_000L)
                .put(new JSONObject().put("commitment", "confirmed")));
    }
    private static long rent(int space) throws Exception {
        return ((Number) rpc("getMinimumBalanceForRentExemption", new JSONArray().put(space))).longValue();
    }

    private static byte[] u32(int i) {
        return ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(i).array();
    }
    private static byte[] u64(long i) {
        return ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(i).array();
    }
    private static byte[] join(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] p : parts) out.write(p, 0, p.length);
        return out.toByteArray();
    }
    private static byte[] shortvec(int n) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        do {
            int b = n & 127;
            n >>>= 7;
            out.write(n > 0 ? b | 128 : b);
        } while (n > 0);
        return out.toByteArray();
    }
    private static byte[] inst(int programId, int[] accounts, byte[] data) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(programId);
        out.write(shortvec(accounts.length), 0, shortvec(accounts.length).length);
        for (int a : accounts) out.write(a);
        byte[] dataLength = shortvec(data.length);
        out.write(dataLength, 0, dataLength.length);
        out.write(data, 0, data.length);
        return out.toByteArray();
    }
    private static byte[] systemCreate(int destination, long lamports, int space, byte[] ownerProgram) {
        return inst(3, new int[] {0, destination},
                join(u32(0), u64(lamports), u64(space), ownerProgram));
    }

    /**
     * This one transaction creates the SPL mint account, creates a standalone SPL
     * token account owned by the persistent wallet, issues EXACTLY 10B XBTC to
     * that account, and uses the same on-device wallet as mint authority.
     *
     * Account keys are fixed in canonical order:
     * 0 payer/authority (signed writable)
     * 1 new mint (signed writable)
     * 2 new token account (signed writable)
     * 3 System Program (unsigned read-only)
     * 4 SPL Token Program (unsigned read-only).
     *
     * The mint can be frozen only if we provide a freeze authority; we set NONE.
     * Mint authority is retained until separately and explicitly revoked.
     */
    public static MintSubmission mintTenBillion(SolanaWallet wallet) throws Exception {
        if (!wallet.exists() || wallet.publicAddress().isEmpty()) {
            throw new IllegalStateException("Create a wallet before minting");
        }
        if (!wallet.mintAddress().isEmpty()) {
            throw new IllegalStateException("Mint already submitted on this device; check its on-chain state. Never silently double-mint.");
        }
        SolanaWallet.EphemeralSigner mint = new SolanaWallet.EphemeralSigner();
        SolanaWallet.EphemeralSigner account = new SolanaWallet.EphemeralSigner();
        try {
            byte[] walletPublic = SolanaWallet.Base58.decode32(wallet.publicAddress());
            byte[] systemPublic = SolanaWallet.Base58.decode32(SYSTEM);
            byte[] tokenProgram = SolanaWallet.Base58.decode32(TOKEN);
            long rentMint = rent(MINT_SPACE);
            long rentAccount = rent(TOKEN_ACCOUNT_SPACE);
            if (balance(wallet.publicAddress()) < rentMint + rentAccount + 100_000) {
                throw new IllegalStateException("Not enough devnet SOL for mint accounts. Use Request test SOL first.");
            }
            JSONObject blockhashResult = (JSONObject) rpc("getLatestBlockhash", new JSONArray()
                    .put(new JSONObject().put("commitment", "confirmed")));
            byte[] blockhash = SolanaWallet.Base58.decode32(
                    blockhashResult.getJSONObject("value").getString("blockhash"));
            byte[] initMint2 = join(
                    new byte[] {20, 6}, walletPublic,
                    new byte[] {0}); // COption freezeAuthority: None (one-byte token encoding)
            byte[] initAccount3 = join(new byte[] {18}, walletPublic);
            byte[] mintChecked = join(new byte[] {14}, u64(XBTC_AMOUNT_BASE), new byte[] {6});
            byte[][] instructions = {
                systemCreate(1, rentMint, MINT_SPACE, tokenProgram),
                inst(4, new int[] {1}, initMint2),
                systemCreate(2, rentAccount, TOKEN_ACCOUNT_SPACE, tokenProgram),
                inst(4, new int[] {2, 1}, initAccount3),
                inst(4, new int[] {1, 2, 0}, mintChecked)
            };

            ByteArrayOutputStream message = new ByteArrayOutputStream();
            message.write(new byte[] {3, 0, 2}); // 3 required signers; 2 read-only unsigned accounts
            message.write(shortvec(5)); // 5 static keys
            for (byte[] key : new byte[][] {walletPublic, mint.publicKey, account.publicKey,
                    systemPublic, tokenProgram}) message.write(key);
            message.write(blockhash);
            message.write(shortvec(instructions.length));
            for (byte[] instruction : instructions) message.write(instruction);
            byte[] msg = message.toByteArray();
            byte[] signed = join(shortvec(3), wallet.sign(msg), mint.sign(msg), account.sign(msg), msg);
            if (signed.length > 1232) throw new IllegalStateException("Solana transaction exceeds size limit");

            // Durable, non-secret local record BEFORE broadcast. If network send fails,
            // the UI must check this request's status, NOT submit a second mint.
            String expectedSignature = SolanaWallet.Base58.encode(Arrays.copyOfRange(signed, 1, 65));
            wallet.saveMintPending(mint.address, expectedSignature, account.address);
            String signedTransaction = Base64.encodeToString(signed, Base64.NO_WRAP);
            String actualSignature = (String) rpc("sendTransaction", new JSONArray()
                    .put(signedTransaction)
                    .put(new JSONObject().put("encoding", "base64")
                            .put("preflightCommitment", "confirmed")
                            .put("skipPreflight", false)));
            if (!expectedSignature.equals(actualSignature)) {
                throw new IllegalStateException("Unexpected Solana transaction signature. Review saved mint record.");
            }
            return new MintSubmission(mint.address, account.address, actualSignature);
        } finally {
            mint.destroy();
            account.destroy();
        }
    }

    public static String signatureStatus(String signature) throws Exception {
        JSONObject result = (JSONObject) rpc("getSignatureStatuses", new JSONArray()
                .put(new JSONArray().put(signature))
                .put(new JSONObject().put("searchTransactionHistory", true)));
        JSONArray values = result.getJSONArray("value");
        if (values.isNull(0)) return "Not yet found on devnet. The transaction may have expired or be pending.";
        JSONObject value = values.getJSONObject(0);
        if (!value.isNull("err")) return "Failed on-chain: " + value.get("err");
        return value.optString("confirmationStatus", "Pending confirmation");
    }
    public static String supplyCheck(String mint) throws Exception {
        // Query the real SPL Token Program ownership before trusting total supply.
        JSONObject accountInfo = (JSONObject) rpc("getAccountInfo", new JSONArray()
                .put(mint).put(new JSONObject().put("encoding", "base64")
                        .put("commitment", "confirmed")));
        if (accountInfo.isNull("value")) return "Mint account does not exist on devnet yet.";
        String owner = accountInfo.getJSONObject("value").getString("owner");
        if (!TOKEN.equals(owner)) return "Address is not a standard SPL Token mint.";
        JSONObject value = ((JSONObject) rpc("getTokenSupply", new JSONArray()
                .put(mint).put(new JSONObject().put("commitment", "confirmed")))).getJSONObject("value");
        boolean expected = "10000000000000000".equals(value.getString("amount"))
                && value.getInt("decimals") == 6;
        return "Confirmed on devnet: " + value.getString("uiAmountString") + " tokens; " +
                (expected ? "exactly 10 billion XBTC units issued." : "supply differs from planned 10 billion.");
    }
    public static final class MintSubmission {
        public final String mintAddress;
        public final String tokenAccount;
        public final String signature;
        public MintSubmission(String mintAddress, String tokenAccount, String signature) {
            this.mintAddress = mintAddress;
            this.tokenAccount = tokenAccount;
            this.signature = signature;
        }
    }
}
