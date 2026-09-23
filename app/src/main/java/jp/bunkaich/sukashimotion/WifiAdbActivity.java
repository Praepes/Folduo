package jp.bunkaich.sukashimotion;

import android.app.*;
import android.content.Context;
import android.graphics.Color;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.net.wifi.WifiManager;
import android.os.*;
import android.view.*;
import android.widget.*;

import java.net.InetAddress;
import java.util.concurrent.*;

import jp.bunkaich.sukashimotion.adb.WifiAdbManager;
import io.github.muntashirakon.adb.AdbConnection;
import io.github.muntashirakon.adb.android.AdbMdns;

/**
 * Guides the user through on-device WiFi ADB pairing and Shizuku activation.
 * No PC required: the phone pairs with its own adbd, connects, and starts Shizuku.
 */
public final class WifiAdbActivity extends Activity {
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private TextView status;
    private EditText codeInput, portInput, connectPortInput;
    private Button pairButton, startButton;
    private WifiAdbManager adbManager;
    private NsdManager nsdManager;
    private WifiManager.MulticastLock multicastLock;
    private volatile String pairHost;
    private volatile int pairPort = -1, connectPort = -1;
    private NsdManager.DiscoveryListener pairDiscovery, connectDiscovery;

    @Override
    public void onCreate(Bundle saved) {
        super.onCreate(saved);
        adbManager = new WifiAdbManager(this);
        nsdManager = getSystemService(NsdManager.class);
        WifiManager wifi = getSystemService(WifiManager.class);
        multicastLock = wifi.createMulticastLock("folduo-mdns");
        multicastLock.setReferenceCounted(false);

        getWindow().setNavigationBarColor(Color.rgb(16, 23, 20));
        ScrollView scroll = new ScrollView(this);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(24), dp(28), dp(24), dp(40));
        scroll.addView(page);
        scroll.setOnApplyWindowInsetsListener((v, insets) -> {
            android.graphics.Insets i = insets.getInsets(
                    WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            v.setPadding(i.left, i.top, i.right, i.bottom);
            return insets;
        });

        label(page, getString(R.string.wifi_adb_title), 24, Color.WHITE);
        label(page, getString(R.string.wifi_adb_intro), 14, 0xffc5d3cd);

        label(page, getString(R.string.wifi_adb_step1), 17, 0xffb3eed4);
        label(page, getString(R.string.wifi_adb_step1_detail), 13, 0xffc5d3cd);

        label(page, getString(R.string.wifi_adb_step2), 17, 0xffb3eed4);

        status = label(page, getString(R.string.wifi_adb_waiting), 14, 0xffffdd88);

        codeInput = new EditText(this);
        codeInput.setHint(getString(R.string.wifi_adb_code_hint));
        codeInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        codeInput.setTextColor(Color.WHITE);
        codeInput.setHintTextColor(0xff808080);
        page.addView(codeInput, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout portRow = new LinearLayout(this);
        portRow.setOrientation(LinearLayout.HORIZONTAL);

        portInput = new EditText(this);
        portInput.setHint(getString(R.string.wifi_adb_pair_port));
        portInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        portInput.setTextColor(Color.WHITE);
        portInput.setHintTextColor(0xff808080);
        portRow.addView(portInput, new LinearLayout.LayoutParams(0, -2, 1));

        connectPortInput = new EditText(this);
        connectPortInput.setHint(getString(R.string.wifi_adb_connect_port));
        connectPortInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        connectPortInput.setTextColor(Color.WHITE);
        connectPortInput.setHintTextColor(0xff808080);
        connectPortInput.setText("5555");
        portRow.addView(connectPortInput, new LinearLayout.LayoutParams(0, -2, 1));
        page.addView(portRow, new LinearLayout.LayoutParams(-1, -2));

        label(page, getString(R.string.wifi_adb_port_note), 12, 0xff90a298);

        pairButton = new Button(this);
        pairButton.setText(getString(R.string.wifi_adb_pair));
        pairButton.setAllCaps(false);
        pairButton.setOnClickListener(v -> doPair());
        page.addView(pairButton, new LinearLayout.LayoutParams(-1, -2));

        label(page, getString(R.string.wifi_adb_step3), 17, 0xffb3eed4);

        startButton = new Button(this);
        startButton.setText(getString(R.string.wifi_adb_start_shizuku));
        startButton.setAllCaps(false);
        startButton.setEnabled(false);
        startButton.setOnClickListener(v -> doStartShizuku());
        page.addView(startButton, new LinearLayout.LayoutParams(-1, -2));

        Button back = new Button(this);
        back.setText(getString(R.string.back_settings));
        back.setAllCaps(false);
        back.setOnClickListener(v -> finish());
        page.addView(back, new LinearLayout.LayoutParams(-1, -2));

        setContentView(scroll);
    }

    @Override
    protected void onResume() {
        super.onResume();
        multicastLock.acquire();
        startDiscovery();
    }

    @Override
    protected void onPause() {
        stopDiscovery();
        if (multicastLock.isHeld()) multicastLock.release();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        worker.shutdownNow();
        super.onDestroy();
    }

    private void startDiscovery() {
        try {
            pairDiscovery = createDiscovery("_adb-tls-pairing._tcp", true);
            nsdManager.discoverServices("_adb-tls-pairing._tcp", NsdManager.PROTOCOL_DNS_SD, pairDiscovery);
        } catch (Exception e) { setStatus(getString(R.string.wifi_adb_mdns_fail)); }
        try {
            connectDiscovery = createDiscovery("_adb-tls-connect._tcp", false);
            nsdManager.discoverServices("_adb-tls-connect._tcp", NsdManager.PROTOCOL_DNS_SD, connectDiscovery);
        } catch (Exception ignored) {}
    }

    private void stopDiscovery() {
        try { if (pairDiscovery != null) nsdManager.stopServiceDiscovery(pairDiscovery); } catch (Exception ignored) {}
        try { if (connectDiscovery != null) nsdManager.stopServiceDiscovery(connectDiscovery); } catch (Exception ignored) {}
        pairDiscovery = null;
        connectDiscovery = null;
    }

    private NsdManager.DiscoveryListener createDiscovery(String type, boolean isPairing) {
        return new NsdManager.DiscoveryListener() {
            public void onDiscoveryStarted(String t) {}
            public void onDiscoveryStopped(String t) {}
            public void onStartDiscoveryFailed(String t, int e) {}
            public void onStopDiscoveryFailed(String t, int e) {}
            public void onServiceLost(NsdServiceInfo info) {}
            public void onServiceFound(NsdServiceInfo info) {
                nsdManager.resolveService(info, new NsdManager.ResolveListener() {
                    public void onResolveFailed(NsdServiceInfo i, int e) {}
                    public void onServiceResolved(NsdServiceInfo resolved) {
                        InetAddress host = resolved.getHost();
                        int port = resolved.getPort();
                        if (host == null || port <= 0) return;
                        String hostStr = host.getHostAddress();
                        main.post(() -> {
                            if (isPairing) {
                                pairHost = hostStr;
                                pairPort = port;
                                portInput.setText(String.valueOf(port));
                                setStatus(getString(R.string.wifi_adb_found_pair, port));
                            } else {
                                connectPort = port;
                                connectPortInput.setText(String.valueOf(port));
                            }
                        });
                    }
                });
            }
        };
    }

    private void doPair() {
        String code = codeInput.getText().toString().trim();
        if (code.length() != 6) {
            Toast.makeText(this, getString(R.string.wifi_adb_invalid_code), Toast.LENGTH_SHORT).show();
            return;
        }
        String host = pairHost != null ? pairHost : "127.0.0.1";
        int port;
        try { port = Integer.parseInt(portInput.getText().toString().trim()); }
        catch (NumberFormatException e) {
            Toast.makeText(this, getString(R.string.wifi_adb_invalid_port), Toast.LENGTH_SHORT).show();
            return;
        }

        pairButton.setEnabled(false);
        setStatus(getString(R.string.wifi_adb_pairing));

        worker.execute(() -> {
            try {
                adbManager.pair(host, port, code);
                main.post(() -> {
                    setStatus(getString(R.string.wifi_adb_paired));
                    startButton.setEnabled(true);
                    pairButton.setEnabled(true);
                });
            } catch (Exception e) {
                String msg = e.getMessage();
                main.post(() -> {
                    setStatus(getString(R.string.wifi_adb_pair_failed, msg));
                    pairButton.setEnabled(true);
                });
            }
        });
    }

    private void doStartShizuku() {
        String host = pairHost != null ? pairHost : "127.0.0.1";
        int port;
        try { port = Integer.parseInt(connectPortInput.getText().toString().trim()); }
        catch (NumberFormatException e) {
            Toast.makeText(this, getString(R.string.wifi_adb_invalid_port), Toast.LENGTH_SHORT).show();
            return;
        }

        startButton.setEnabled(false);
        setStatus(getString(R.string.wifi_adb_connecting));

        worker.execute(() -> {
            AdbConnection conn = null;
            try {
                conn = adbManager.connect(host, port);
                String result = WifiAdbManager.startShizuku(conn);
                main.post(() -> {
                    setStatus(getString(R.string.wifi_adb_shizuku_started));
                    startButton.setEnabled(true);
                    Toast.makeText(this, getString(R.string.wifi_adb_shizuku_started), Toast.LENGTH_LONG).show();
                });
            } catch (Exception e) {
                String msg = e.getMessage();
                main.post(() -> {
                    setStatus(getString(R.string.wifi_adb_connect_failed, msg));
                    startButton.setEnabled(true);
                });
            } finally {
                if (conn != null) try { conn.close(); } catch (Exception ignored) {}
            }
        });
    }

    private void setStatus(String text) { status.setText(text); }

    private int dp(int x) { return Math.round(x * getResources().getDisplayMetrics().density); }

    private TextView label(LinearLayout parent, String text, int size, int color) {
        TextView v = new TextView(this);
        v.setText(text);
        v.setTextSize(size);
        v.setTextColor(color);
        v.setPadding(0, dp(10), 0, dp(10));
        v.setLineSpacing(dp(3), 1);
        parent.addView(v);
        return v;
    }
}
