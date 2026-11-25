package com.example.chatclient;

import android.os.Bundle;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/** 第二頁：連上 Server、收發聊天訊息、可正常離開 */
public class ChatActivity extends AppCompatActivity {

    static final String EXTRA_NAME = "CLIENT_NAME";
    static final String EXTRA_IP = "SERVER_IP";
    static final String EXTRA_PORT = "SERVER_PORT";
    private static final int CONNECT_TIMEOUT_MS = 5000;

    private TextView tvMessages, tvOnline;
    private ScrollView scroll;
    private EditText etMessage;
    private Button btnSend;

    private String name;
    private volatile Socket socket;
    private volatile PrintWriter out;
    /** 所有「寫出」都在這條背景執行緒做（Android 不允許在 UI thread 做網路） */
    private final ExecutorService sender = Executors.newSingleThreadExecutor();
    /** 使用者自己按 LEAVE / 返回 → 之後的斷線例外都是預期中的，不要顯示錯誤 */
    private volatile boolean leaving = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.enableEdgeToEdge(getWindow());
        setContentView(R.layout.activity_chat);
        Insets.apply(findViewById(R.id.root));

        name = getIntent().getStringExtra(EXTRA_NAME);
        String ip = getIntent().getStringExtra(EXTRA_IP);
        int port = getIntent().getIntExtra(EXTRA_PORT, Protocol.PORT);

        TextView tvHello = findViewById(R.id.tvHello);
        tvMessages = findViewById(R.id.tvMessages);
        tvOnline = findViewById(R.id.tvOnline);
        scroll = findViewById(R.id.scroll);
        etMessage = findViewById(R.id.etMessage);
        btnSend = findViewById(R.id.btnSend);
        Button btnLeave = findViewById(R.id.btnLeave);

        tvHello.setText("Hi! " + name);
        btnSend.setEnabled(false);   // 連上之後才開放
        btnSend.setOnClickListener(v -> sendMessage());
        etMessage.setOnEditorActionListener((v, actionId, e) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) { sendMessage(); return true; }
            return false;
        });
        btnLeave.setOnClickListener(v -> leave());
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() { leave(); }
        });

        appendLine("Connecting to " + ip + ":" + port + " ...");
        new Thread(() -> connectAndListen(ip, port), "client-reader").start();
    }

    // ------------------------------------------------------------------ 連線 + 接收

    private void connectAndListen(String ip, int port) {
        Socket s = new Socket();
        try {
            s.connect(new InetSocketAddress(ip, port), CONNECT_TIMEOUT_MS);
        } catch (IOException e) {
            try { s.close(); } catch (IOException ignored) {}
            if (!leaving) {
                runOnUiThread(() -> {
                    Toast.makeText(this, "連線失敗：" + e.getMessage(), Toast.LENGTH_LONG).show();
                    finish();   // 回到第一頁
                });
            }
            return;
        }

        try {
            socket = s;
            out = new PrintWriter(new OutputStreamWriter(
                    s.getOutputStream(), StandardCharsets.UTF_8), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(
                    s.getInputStream(), StandardCharsets.UTF_8));

            if (leaving) {   // 連線途中就按了 LEAVE
                closeSocket();
                return;
            }
            send(new Protocol.Json().put2("type", Protocol.JOIN).put2("name", name).toString());
            runOnUiThread(() -> btnSend.setEnabled(true));

            String line;
            while ((line = in.readLine()) != null) {   // null = Server 關閉連線
                handleLine(line);
            }
            onDisconnected("Disconnected from server.");
        } catch (IOException e) {
            onDisconnected("Connection lost.");
        }
    }

    private void handleLine(String line) {
        JSONObject msg;
        try {
            msg = new JSONObject(line);
        } catch (JSONException e) {
            return;
        }
        switch (msg.optString("type")) {
            case Protocol.CHAT:
                appendLine(msg.optString("name") + ": " + msg.optString("text"));
                break;
            case Protocol.SYSTEM:
                appendLine("Server: " + msg.optString("text"));
                break;
            case Protocol.USERS:
                JSONArray arr = msg.optJSONArray("list");
                List<String> names = new ArrayList<>();
                if (arr != null) for (int i = 0; i < arr.length(); i++) names.add(arr.optString(i));
                runOnUiThread(() -> tvOnline.setText(
                        "Online (" + names.size() + "): " + String.join(", ", names)));
                break;
            case Protocol.CLOSE:
                appendLine("Server: " + msg.optString("text"));
                break;
        }
    }

    /** Server 關閉 / 網路斷掉：不閃退，只提示並停用送出 */
    private void onDisconnected(String reason) {
        closeSocket();
        if (leaving) return;   // 自己離開造成的，不用提示
        appendLine(reason);
        runOnUiThread(() -> {
            btnSend.setEnabled(false);
            tvOnline.setText("Offline");
        });
    }

    // ------------------------------------------------------------------ 傳送

    private void send(String line) {
        try {
            sender.execute(() -> {
                PrintWriter w = out;
                if (w != null) w.println(line);
            });
        } catch (RejectedExecutionException ignored) {
            // 已離開
        }
    }

    private void sendMessage() {
        String text = etMessage.getText().toString().trim();
        if (text.isEmpty() || out == null || leaving) return;
        etMessage.setText("");
        // 不先自己顯示，等 Server 廣播回來，所有人看到的順序才會一致
        send(new Protocol.Json().put2("type", Protocol.MSG).put2("text", text).toString());
    }

    // ------------------------------------------------------------------ 離開

    private void leave() {
        disconnect();
        finish();
    }

    private void disconnect() {
        if (leaving) return;
        leaving = true;
        try {
            sender.execute(() -> {
                PrintWriter w = out;
                if (w != null) w.println(new Protocol.Json().put2("type", Protocol.LEAVE).toString());
                closeSocket();   // reader thread 的 readLine() 會因此結束
            });
        } catch (RejectedExecutionException ignored) {}
        sender.shutdown();
    }

    private void closeSocket() {
        Socket s = socket;
        if (s != null) {
            try { s.close(); } catch (IOException ignored) {}
        }
    }

    @Override
    protected void onDestroy() {
        disconnect();
        super.onDestroy();
    }

    private void appendLine(String text) {
        runOnUiThread(() -> {
            tvMessages.append(text + "\n");
            scroll.post(() -> scroll.fullScroll(ScrollView.FOCUS_DOWN));
        });
    }
}
