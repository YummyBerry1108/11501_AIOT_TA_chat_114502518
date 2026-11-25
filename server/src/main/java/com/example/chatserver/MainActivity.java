package com.example.chatserver;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;

/** 第一頁：輸入 Server 名稱 */
public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.enableEdgeToEdge(getWindow());
        setContentView(R.layout.activity_main);
        Insets.apply(findViewById(R.id.root));

        EditText etName = findViewById(R.id.etName);
        Button btnConnect = findViewById(R.id.btnConnect);

        btnConnect.setOnClickListener(v -> {
            String name = etName.getText().toString().trim();
            if (name.isEmpty()) {
                Toast.makeText(this, "請輸入 Server 名稱", Toast.LENGTH_SHORT).show();
                return;
            }
            Intent intent = new Intent(this, ChatActivity.class);
            intent.putExtra(ChatActivity.EXTRA_NAME, name);
            startActivity(intent);
        });
    }
}
