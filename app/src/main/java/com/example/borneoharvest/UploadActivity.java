package com.example.borneoharvest;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class UploadActivity extends AppCompatActivity {

    private String myUserId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_upload);

        EditText editTitle = findViewById(R.id.editTitle);
        EditText editDescription = findViewById(R.id.editDescription);
        CardView btnPublish = findViewById(R.id.btnPublish);

        FirebaseFirestore db = FirebaseFirestore.getInstance();

        // 1. GENERATE OR GRAB THE PHONE'S UNIQUE ID
        SharedPreferences prefs = getSharedPreferences("UserPrefs", MODE_PRIVATE);
        myUserId = prefs.getString("userId", null);
        if (myUserId == null) {
            myUserId = UUID.randomUUID().toString();
            prefs.edit().putString("userId", myUserId).apply();
        }

        btnPublish.setOnClickListener(v -> {
            String title = editTitle.getText().toString().trim();
            String description = editDescription.getText().toString().trim();

            if (title.isEmpty() || description.isEmpty()) {
                Toast.makeText(UploadActivity.this, "Sila isi semua ruangan!", Toast.LENGTH_SHORT).show();
                return;
            }

            // NEW: SHOW LOADING DIALOG
            ProgressDialog progressDialog = new ProgressDialog(this);
            progressDialog.setMessage("Sedang memuat naik teknik anda...");
            progressDialog.setCancelable(false); // User cannot click away until it finishes or fails
            progressDialog.show();

            Map<String, Object> technique = new HashMap<>();
            technique.put("title", title);
            technique.put("description", description);
            technique.put("timestamp", System.currentTimeMillis());
            technique.put("authorId", myUserId);

            db.collection("farming_techniques")
                    .add(technique)
                    .addOnSuccessListener(documentReference -> {
                        progressDialog.dismiss();
                        Toast.makeText(UploadActivity.this, "Berjaya dikongsi!", Toast.LENGTH_SHORT).show();
                        finish(); // Transfer back to community page
                    })
                    .addOnFailureListener(e -> {
                        progressDialog.dismiss();

                        // NEW: OFFLINE POPUP NOTIFICATION
                        new AlertDialog.Builder(UploadActivity.this)
                                .setTitle("Luar Talian 🌐")
                                .setMessage("Tiada internet dikesan. Teknik anda telah disimpan dan akan dimuat naik secara automatik apabila talian pulih.")
                                .setPositiveButton("Kembali", (dialog, which) -> {
                                    finish(); // Automatically transfer user back to the forum page
                                })
                                .setCancelable(false)
                                .show();
                    });
        });
    }
}