package com.xbtc.exchange;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;

import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters;
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters;
import org.bouncycastle.crypto.signers.Ed25519Signer;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.math.BigInteger;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Persistent device-only Solana Ed25519 signer, explicitly DEVNET-ONLY.
 *
 * Android Keystore cannot natively produce portable Solana Ed25519 signatures on
 * general Android phones: instead it wraps a 32-byte Ed25519 seed with AES-GCM.
 * AES key use needs a recent screen-lock credential. The plaintext seed exists
 * in process memory for the brief signing operation and is then zeroed.
 *
 * No key / seed / transaction signing method is exposed to WebView JavaScript.
 * No cloud backup or export: losing device/app data LOSES THE TEST WALLET.
 * Not audited; NOT SAFE FOR MAINNET ASSETS.
 */
public final class SolanaWallet {
    private static final String KEY_ALIAS = "xbtc_devnet_wallet_aes_v1";
    private static final String STORE_FILE = "xbtc-devnet-wallet-v1.bin";
    private static final String PREFS = "xbtc_devnet_wallet_public";
    private final Context context;
    private final SharedPreferences prefs;
    private final SecureRandom random = new SecureRandom();

    public SolanaWallet(Context context) {
        this.context = context.getApplicationContext();
        prefs = this.context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private File encryptedFile() {
        return new File(context.getNoBackupFilesDir(), STORE_FILE);
    }
    public boolean exists() {
        return encryptedFile().exists();
    }
    public String publicAddress() {
        return prefs.getString("address", "");
    }
    public String mintAddress() {
        return prefs.getString("mint_address", "");
    }
    public String mintSignature() {
        return prefs.getString("mint_signature", "");
    }
    public void saveMintPending(String mint, String signature, String tokenAccount) {
        if (!prefs.edit().putString("mint_address", mint)
                .putString("mint_signature", signature)
                .putString("token_account", tokenAccount).commit()) {
            throw new IllegalStateException("Cannot persist mint request. Do not broadcast.");
        }
    }
    public String tokenAccount() {
        return prefs.getString("token_account", "");
    }

    private SecretKey aesKey(boolean create) throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        if (store.containsAlias(KEY_ALIAS)) {
            return ((KeyStore.SecretKeyEntry) store.getEntry(KEY_ALIAS, null)).getSecretKey();
        }
        if (!create) throw new IllegalStateException("Device encryption key missing. Wallet cannot be recovered.");
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setUserAuthenticationRequired(true)
                .setUserAuthenticationValidityDurationSeconds(60)
                .build());
        return generator.generateKey();
    }

    public void create() throws Exception {
        if (exists()) throw new IllegalStateException("Wallet already exists. Will never silently replace it.");
        byte[] seed = new byte[32];
        random.nextBytes(seed);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, aesKey(true));
            byte[] iv = cipher.getIV();
            byte[] ciphertext = cipher.doFinal(seed);
            if (iv.length != 12) throw new IllegalStateException("Unexpected AES-GCM nonce size");
            // Atomic creation in app-private, excluded-from-backup directory.
            File tmp = new File(context.getNoBackupFilesDir(), STORE_FILE + ".tmp");
            if (tmp.exists() && !tmp.delete()) throw new IllegalStateException("Cannot clean old temporary file");
            try (FileOutputStream out = new FileOutputStream(tmp)) {
                out.write(1); out.write(iv.length); out.write(iv); out.write(ciphertext);
                out.getFD().sync();
            }
            if (!tmp.renameTo(encryptedFile())) {
                tmp.delete();
                throw new IllegalStateException("Could not save wallet securely");
            }
            String address = publicKey(seed);
            if (!prefs.edit().putString("address", address).commit()) {
                throw new IllegalStateException("Could not persist public wallet address");
            }
        } finally {
            Arrays.fill(seed, (byte) 0);
        }
    }

    private byte[] decryptSeed() throws Exception {
        if (!exists()) throw new IllegalStateException("Create a devnet wallet first");
        byte[] all = new byte[(int) encryptedFile().length()];
        if (all.length < 30 || all.length > 256) throw new IllegalStateException("Invalid wallet file size");
        try (FileInputStream in = new FileInputStream(encryptedFile())) {
            int offset = 0, n;
            while (offset < all.length && (n = in.read(all, offset, all.length - offset)) != -1) {
                offset += n;
            }
            if (offset != all.length) throw new IllegalStateException("Incomplete encrypted wallet file");
        }
        if (all[0] != 1 || all[1] != 12) throw new IllegalStateException("Unrecognized wallet file format");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, aesKey(false), new GCMParameterSpec(128, all, 2, 12));
        byte[] decrypted = cipher.doFinal(all, 14, all.length - 14);
        if (decrypted.length != 32) {
            Arrays.fill(decrypted, (byte) 0);
            throw new IllegalStateException("Invalid decrypted wallet");
        }
        return decrypted;
    }

    public byte[] sign(byte[] message) throws Exception {
        if (message == null || message.length == 0 || message.length > 1232) {
            throw new IllegalArgumentException("Invalid Solana transaction message size");
        }
        byte[] seed = decryptSeed();
        try {
            Ed25519PrivateKeyParameters priv = new Ed25519PrivateKeyParameters(seed, 0);
            Ed25519Signer signer = new Ed25519Signer();
            signer.init(true, priv);
            signer.update(message, 0, message.length);
            byte[] signature = signer.generateSignature();
            if (signature.length != 64) throw new IllegalStateException("Invalid Ed25519 signature length");
            return signature;
        } finally {
            Arrays.fill(seed, (byte) 0);
        }
    }

    private static String publicKey(byte[] seed) {
        Ed25519PrivateKeyParameters priv = new Ed25519PrivateKeyParameters(seed, 0);
        Ed25519PublicKeyParameters pub = priv.generatePublicKey();
        return Base58.encode(pub.getEncoded());
    }

    public static final class EphemeralSigner {
        private final byte[] seed = new byte[32];
        public final byte[] publicKey;
        public final String address;
        public EphemeralSigner() {
            new SecureRandom().nextBytes(seed);
            Ed25519PrivateKeyParameters priv = new Ed25519PrivateKeyParameters(seed, 0);
            publicKey = priv.generatePublicKey().getEncoded();
            address = Base58.encode(publicKey);
        }
        public byte[] sign(byte[] message) throws Exception {
            Ed25519Signer e = new Ed25519Signer();
            e.init(true, new Ed25519PrivateKeyParameters(seed, 0));
            e.update(message, 0, message.length);
            return e.generateSignature();
        }
        public void destroy() { Arrays.fill(seed, (byte) 0); }
    }

    public static final class Base58 {
        private static final String ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz";
        private Base58() {}
        public static String encode(byte[] bytes) {
            BigInteger n = new BigInteger(1, bytes);
            StringBuilder text = new StringBuilder();
            while (n.signum() > 0) {
                BigInteger[] pair = n.divideAndRemainder(BigInteger.valueOf(58));
                text.append(ALPHABET.charAt(pair[1].intValue()));
                n = pair[0];
            }
            for (byte b : bytes) { if (b != 0) break; text.append('1'); }
            return text.reverse().toString();
        }
        public static byte[] decode32(String text) {
            if (text == null || text.length() < 32 || text.length() > 44) {
                throw new IllegalArgumentException("Invalid Solana base58 address");
            }
            BigInteger n = BigInteger.ZERO;
            for (int i = 0; i < text.length(); i++) {
                int digit = ALPHABET.indexOf(text.charAt(i));
                if (digit < 0) throw new IllegalArgumentException("Invalid base58 character");
                n = n.multiply(BigInteger.valueOf(58)).add(BigInteger.valueOf(digit));
            }
            byte[] input = n.toByteArray();
            int start = input.length > 1 && input[0] == 0 ? 1 : 0;
            int zeroes = 0;
            while (zeroes < text.length() && text.charAt(zeroes) == '1') zeroes++;
            int numberLength = input.length - start;
            if (numberLength + zeroes != 32) throw new IllegalArgumentException("Base58 is not a 32-byte public key");
            byte[] result = new byte[32];
            System.arraycopy(input, start, result, zeroes, numberLength);
            return result;
        }
    }
}
