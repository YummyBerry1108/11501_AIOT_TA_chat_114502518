package com.example.chatserver;

import android.os.Bundle;
import android.util.Log;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Executors;

/** 第二頁：開 ServerSocket，接受多個 Client，轉發聊天訊息 */
public class ChatActivity extends AppCompatActivity {

    static final String EXTRA_NAME = "SERVER_NAME";
    private static final String TAG = "ChatServer";

    private TextView tvMessages, tvOnline;
    private ScrollView scroll;
    private EditText etMessage;
    private Button btnSend;

    private String serverName;
    private volatile ServerSocket serverSocket;
    private final List<ClientHandler> clients = new CopyOnWriteArrayList<>();
    /** 所有「寫出」都丟到同一條背景執行緒，避免在 UI thread 做網路、也保證訊息順序 */
    private final ExecutorService sender = Executors.newSingleThreadExecutor();
    private volatile boolean closing = false;

    /** 每個連進來的 Client 各一個 */
    private static class ClientHandler {
        final Socket socket;
        final String address;   // ip:port
        PrintWriter out;
        volatile String name;   // join 之後才有

        ClientHandler(Socket socket) {
            this.socket = socket;
            this.address = socket.getInetAddress().getHostAddress() + ":" + socket.getPort();
        }

        void send(String line) {
            if (out != null) out.println(line);   // println 自帶換行 + autoFlush
        }

        void close() {
            try { socket.close(); } catch (IOException ignored) {}
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.enableEdgeToEdge(getWindow());
        setContentView(R.layout.activity_chat);
        Insets.apply(findViewById(R.id.root));

        serverName = getIntent().getStringExtra(EXTRA_NAME);
        if (serverName == null || serverName.isEmpty()) serverName = "Server";

        TextView tvHello = findViewById(R.id.tvHello);
        tvMessages = findViewById(R.id.tvMessages);
        tvOnline = findViewById(R.id.tvOnline);
        scroll = findViewById(R.id.scroll);
        etMessage = findViewById(R.id.etMessage);
        btnSend = findViewById(R.id.btnSend);
        Button btnLeave = findViewById(R.id.btnLeave);

        tvHello.setText("Hi! " + serverName);
        btnSend.setOnClickListener(v -> sendFromServer());
        etMessage.setOnEditorActionListener((v, actionId, e) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) { sendFromServer(); return true; }
            return false;
        });
        btnLeave.setOnClickListener(v -> shutdownAndLeave());
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() { shutdownAndLeave(); }
        });

        updateOnlineList();
        new Thread(this::acceptLoop, "accept").start();
    }

    // ------------------------------------------------------------------ 連線

    private void acceptLoop() {
        try {
            serverSocket = new ServerSocket();
            serverSocket.setReuseAddress(true);   // 剛關掉馬上重開也不會 "Address already in use"
            serverSocket.bind(new InetSocketAddress(Protocol.PORT));
        } catch (IOException e) {
            appendLine("Server failed to start: " + e.getMessage());
            return;
        }
        String ip = getLocalIpAddress();
        appendLine("Server started (" + (ip == null ? "?" : ip) + ":" + Protocol.PORT + ")");
        appendLine(serverName + " joined the chat room.");

        while (!closing) {
            try {
                Socket socket = serverSocket.accept();   // blocking，等待 Client
                ClientHandler h = new ClientHandler(socket);
                new Thread(() -> handleClient(h), "client-" + h.address).start();
            } catch (IOException e) {
                // serverSocket.close() 之後 accept() 會丟例外，屬正常結束
                if (!closing) Log.w(TAG, "accept failed", e);
            }
        }
    }

    private void handleClient(ClientHandler h) {
        try {
            h.out = new PrintWriter(new OutputStreamWriter(
                    h.socket.getOutputStream(), StandardCharsets.UTF_8), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(
                    h.socket.getInputStream(), StandardCharsets.UTF_8));

            String line;
            while ((line = in.readLine()) != null) {   // null = 對方關閉連線
                JSONObject msg;
                try {
                    msg = new JSONObject(line);
                } catch (JSONException e) {
                    continue;   // 忽略格式錯誤的行
                }
                String type = msg.optString("type");

                if (Protocol.JOIN.equals(type) && h.name == null) {
                    if (closing) break;
                    String name = msg.optString("name").trim();
                    h.name = name.isEmpty() ? "Guest" : name;
                    clients.add(h);
                    appendLine(h.name + " (" + h.address + ") connected.");
                    broadcast(Protocol.system("Welcome " + h.name + " join us!"));
                    broadcastUsers();
                } else if (h.name == null) {
                    continue;   // 還沒 join 的訊息不處理
                } else if (Protocol.MSG.equals(type)) {
                    String text = msg.optString("text");
                    appendLine(h.name + ": " + text);
                    broadcast(Protocol.chat(h.name, text));
                } else if (Protocol.LEAVE.equals(type)) {
                    break;
                }
            }
        } catch (IOException e) {
            // Client 斷線 / Server 關閉時會到這裡，不視為錯誤
        } finally {
            removeClient(h);
        }
    }

    /** Client 離開（正常 leave、斷線、被關閉）都走這裡，只會通知一次 */
    private void removeClient(ClientHandler h) {
        h.close();
        if (clients.remove(h) && !closing) {
            appendLine(h.name + " has left.");
            broadcast(Protocol.system(h.name + " has left."));
            broadcastUsers();
        }
    }

    // ------------------------------------------------------------------ 傳送

    private void broadcast(String line) {
        try {
            sender.execute(() -> {
                for (ClientHandler c : clients) c.send(line);
            });
        } catch (RejectedExecutionException ignored) {
            // Server 已關閉，不用再送
        }
    }

    private void broadcastUsers() {
        broadcast(Protocol.users(onlineNames()));
        updateOnlineList();
    }

    private void sendFromServer() {
        String text = etMessage.getText().toString().trim();
        if (text.isEmpty() || closing) return;
        etMessage.setText("");
        appendLine(serverName + ": " + text);
        broadcast(Protocol.chat(serverName, text));
    }

    // ------------------------------------------------------------------ 關閉

    private void shutdownAndLeave() {
        shutdown();
        finish();
    }

    private void shutdown() {
        if (closing) return;
        closing = true;
        btnSend.setEnabled(false);
        sender.execute(() -> {
            for (ClientHandler c : clients) {
                c.send(Protocol.close("Server closed. Please press 'LEAVE' button."));
            }
            for (ClientHandler c : clients) c.close();
            clients.clear();
            try {
                if (serverSocket != null) serverSocket.close();   // 停止 accept()
            } catch (IOException ignored) {}
        });
        sender.shutdown();   // 上面的工作做完後執行緒自動結束
    }

    @Override
    protected void onDestroy() {
        shutdown();   // 被系統關掉時也要把 Socket 收乾淨
        super.onDestroy();
    }

    // ------------------------------------------------------------------ UI

    private List<String> onlineNames() {
        List<String> names = new ArrayList<>();
        names.add(serverName + " (server)");
        for (ClientHandler c : clients) names.add(c.name);
        return names;
    }

    private void updateOnlineList() {
        List<String> names = onlineNames();
        runOnUiThread(() -> tvOnline.setText(
                "Online (" + names.size() + "): " + String.join(", ", names)));
    }

    private void appendLine(String text) {
        runOnUiThread(() -> {
            tvMessages.append(text + "\n");
            scroll.post(() -> scroll.fullScroll(ScrollView.FOCUS_DOWN));
        });
    }

    /** 取得本機 IPv4（不是 127.0.0.1） */
    static String getLocalIpAddress() {
        try {
            for (NetworkInterface intf : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                for (InetAddress addr : Collections.list(intf.getInetAddresses())) {
                    if (!addr.isLoopbackAddress() && addr instanceof Inet4Address) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (SocketException e) {
            Log.e(TAG, "getLocalIpAddress", e);
        }
        return null;
    }
}
