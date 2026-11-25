package com.example.chatclient;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;

/** 第一頁：輸入 Client 名稱、Server IP、Port */
public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.enableEdgeToEdge(getWindow());
        setContentView(R.layout.activity_main);
        Insets.apply(findViewById(R.id.root));

        EditText etName = findViewById(R.id.etName);
        EditText etIp = findViewById(R.id.etIp);
        EditText etPort = findViewById(R.id.etPort);
        Button btnConnect = findViewById(R.id.btnConnect);

        btnConnect.setOnClickListener(v -> {
            String name = etName.getText().toString().trim();
            String ip = etIp.getText().toString().trim();
            int port;
            try {
                port = Integer.parseInt(etPort.getText().toString().trim());
            } catch (NumberFormatException e) {
                port = -1;
            }
            if (name.isEmpty() || ip.isEmpty()) {
                Toast.makeText(this, "請輸入名稱與 IP", Toast.LENGTH_SHORT).show();
                return;
            }
            if (port < 1 || port > 65535) {
                Toast.makeText(this, "Port 必須介於 1 ~ 65535", Toast.LENGTH_SHORT).show();
                return;
            }
            Intent intent = new Intent(this, ChatActivity.class);
            intent.putExtra(ChatActivity.EXTRA_NAME, name);
            intent.putExtra(ChatActivity.EXTRA_IP, ip);
            intent.putExtra(ChatActivity.EXTRA_PORT, port);
            startActivity(intent);
        });
    }
}
