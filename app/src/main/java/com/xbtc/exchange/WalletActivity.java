package com.xbtc.exchange;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.KeyguardManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * NATIVE UI: The web marketplace can open this screen, but cannot sign or
 * submit anything through a Javascript bridge. All modifications are restricted
 * to the devnet token mint action and require explicit confirmation + Android
 * screen-lock authentication. There are no arbitrary-sign or mainnet actions.
 */
public class WalletActivity extends Activity {
    private static final int AUTH_REQUEST = 4401;
    private SolanaWallet wallet;
    private TextView status, account, minted;
    private Button createButton, faucetButton, mintButton, checkButton;
    private Runnable afterUnlock;
    private boolean busy;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        wallet = new SolanaWallet(this);
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.rgb(11, 19, 32));
        LinearLayout panel = new LinearLayout(this);
        panel.setPadding(26, 28, 26, 40);
        panel.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(panel);

        title(panel, "XBTC · Secure test wallet", 25, 0xffe7bf77);
        note(panel, "Solana DEVNET ONLY. Stable wallet address on this device. " +
                "Android screen-lock confirmation is required before creating the wallet or signing the XBTC mint. " +
                "This is a development prototype, not audited mainnet custody.");
        account = note(panel, "");
        minted = note(panel, "");

        createButton = button(panel, "1 · Create built-in Solana test wallet", () ->
                confirm("Create test wallet", "This generates a new Solana Ed25519 key encrypted by the Android Keystore. " +
                        "If you lose or uninstall this app, you will lose the wallet. Use DEVNET only.", () ->
                        unlock(() -> background("Creating wallet", () -> {
                            wallet.create();
                            return "Created permanent test wallet address on this device.";
                        }))));
        button(panel, "Copy my public wallet address", () -> {
            if (wallet.publicAddress().isEmpty()) { report("Create a wallet first."); return; }
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(ClipData.newPlainText("Solana devnet address", wallet.publicAddress()));
            report("Public address copied. Never share a private key.");
        });
        button(panel, "Check devnet SOL balance", () -> {
            if (!wallet.exists()) { report("Create your wallet first."); return; }
            background("Checking SOL balance", () -> {
                long lamports = SolanaDevnet.balance(wallet.publicAddress());
                return "Devnet SOL balance: " + (lamports / 1_000_000_000.0) + " SOL (test only).";
            });
        });
        faucetButton = button(panel, "2 · Request free devnet test SOL", () -> {
            if (!wallet.exists()) { report("Create a wallet first."); return; }
            confirm("Request devnet SOL", "Request 1 TEST SOL from Solana's public devnet faucet. " +
                    "It is not real money. Faucet rate limits can block this request.", () ->
                    background("Requesting devnet test SOL", () ->
                            "Devnet airdrop submitted: " + SolanaDevnet.airdropOneSol(wallet.publicAddress()) +
                                    ". Use Check balance to confirm."));
        });
        mintButton = button(panel, "3 · Sign & mint 10,000,000,000 XBTC (TEST)", () -> {
            if (!wallet.exists()) { report("Create a wallet first."); return; }
            if (!wallet.mintAddress().isEmpty()) {
                report("This wallet has an existing mint record. Inspect on-chain status. No double-minting.");
                return;
            }
            confirm("Sign a REAL DEVNET transaction",
                    "Mint exactly 10,000,000,000 XBTC at 6 decimals, in one Solana DEVNET transaction. " +
                    "Creates a token mint and account. Transaction fees and rent are paid with TEST SOL. " +
                    "The wallet remains mint authority; this is NOT yet a permanently capped supply. " +
                    "Approve with your device PIN/password? No MAINNET transfer is possible in this app.",
                    () -> unlock(() -> background("Signing and sending devnet mint", () -> {
                        SolanaDevnet.MintSubmission tx = SolanaDevnet.mintTenBillion(wallet);
                        return "Broadcast submitted on DEVNET. Mint: " + tx.mintAddress +
                                "\nSignature: " + tx.signature +
                                "\nCheck supply for final confirmation.";
                    })));
        });
        checkButton = button(panel, "4 · Verify XBTC mint and supply on-chain", () -> {
            if (wallet.mintAddress().isEmpty()) { report("No mint submitted from this device."); return; }
            background("Verifying public blockchain", () -> {
                String result = SolanaDevnet.supplyCheck(wallet.mintAddress());
                String stateText = SolanaDevnet.signatureStatus(wallet.mintSignature());
                return result + "\nMint transaction: " + stateText +
                        "\nhttps://explorer.solana.com/address/" + wallet.mintAddress() + "?cluster=devnet";
            });
        });
        status = note(panel, "No real XBTC has been minted from this screen yet.");
        note(panel, "SECURITY: A fixed signer means your wallet keeps the SAME PUBLIC ADDRESS. " +
                "It does not mean signatures are automatic or preapproved. Every real signing action " +
                "must be reviewed and approved by you.");
        note(panel, "NO RECOVERY BACKUP is implemented. If you reset or lose this phone, " +
                "you may lose control of the devnet mint authority. Do not use this build for real funds.");
        button(panel, "Return to exchange", this::finish);
        setContentView(scroll);
        render();
    }

    private void title(LinearLayout parent, String text, int sp, int color) {
        TextView tv = new TextView(this);
        tv.setText(text); tv.setTextSize(sp); tv.setTextColor(color);
        tv.setPadding(0, 8, 0, 14);
        parent.addView(tv);
    }
    private TextView note(LinearLayout parent, String value) {
        TextView v = new TextView(this);
        v.setText(value); v.setTextSize(17); v.setTextColor(0xfff0f4fa);
        v.setPadding(0, 12, 0, 18);
        v.setTextIsSelectable(true);
        parent.addView(v);
        return v;
    }
    private Button button(LinearLayout parent, String text, Runnable click) {
        Button b = new Button(this);
        b.setAllCaps(false);
        b.setText(text);
        b.setTextSize(17);
        b.setOnClickListener(v -> click.run());
        parent.addView(b);
        return b;
    }
    private void report(String value) {
        if (status != null) status.setText(value);
    }
    private void render() {
        if (account != null) account.setText(wallet.exists()
                ? "Your DEVNET public address:\n" + wallet.publicAddress()
                : "Wallet not created. No Solana public address yet.");
        if (minted != null) minted.setText(wallet.mintAddress().isEmpty()
                ? "XBTC mint: not created."
                : "Submitted mint address:\n" + wallet.mintAddress() + "\n" +
                  "On-chain supply is NOT confirmed until verified.");
        if (createButton != null) createButton.setEnabled(!busy && !wallet.exists());
        if (faucetButton != null) faucetButton.setEnabled(!busy && wallet.exists());
        if (mintButton != null) mintButton.setEnabled(!busy && wallet.exists() && wallet.mintAddress().isEmpty());
        if (checkButton != null) checkButton.setEnabled(!busy && !wallet.mintAddress().isEmpty());
    }
    private void confirm(String title, String message, Runnable action) {
        if (busy) return;
        new AlertDialog.Builder(this).setTitle(title).setMessage(message)
                .setNegativeButton("Cancel", (d,w) -> {})
                .setPositiveButton("I approve", (d,w) -> action.run()).show();
    }
    private void unlock(Runnable action) {
        KeyguardManager guard = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
        if (guard == null || !guard.isDeviceSecure()) {
            report("Set up an Android screen-lock PIN/password before enabling an in-app wallet.");
            return;
        }
        Intent prompt = guard.createConfirmDeviceCredentialIntent("XBTC wallet security",
                "Confirm screen lock to allow this one wallet operation");
        if (prompt == null) { report("Device authentication unavailable. No signing performed."); return; }
        afterUnlock = action;
        startActivityForResult(prompt, AUTH_REQUEST);
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == AUTH_REQUEST) {
            Runnable action = afterUnlock; afterUnlock = null;
            if (result == RESULT_OK && action != null) action.run();
            else report("Authentication cancelled. No wallet signing performed.");
        }
    }
    private interface Task { String execute() throws Exception; }
    private void background(String label, Task task) {
        if (busy) return;
        busy = true; report(label + "…"); render();
        new Thread(() -> {
            String message;
            try { message = task.execute(); }
            catch (Exception ex) { message = "Stopped: " + ex.getMessage(); }
            final String result = message;
            runOnUiThread(() -> { busy = false; render(); report(result); });
        }, "XBTC-devnet-operator").start();
    }
}
