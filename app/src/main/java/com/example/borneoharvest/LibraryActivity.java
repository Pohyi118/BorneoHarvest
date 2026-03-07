package com.example.borneoharvest;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.util.UUID;

public class LibraryActivity extends AppCompatActivity {

    private LinearLayout feedContainer;
    private FirebaseFirestore db;
    private String myUserId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_library);

        feedContainer = findViewById(R.id.feedContainer);
        db = FirebaseFirestore.getInstance();

        // 1. LOAD THIS PHONE'S ID
        SharedPreferences prefs = getSharedPreferences("UserPrefs", MODE_PRIVATE);
        myUserId = prefs.getString("userId", null);
        if (myUserId == null) {
            myUserId = UUID.randomUUID().toString();
            prefs.edit().putString("userId", myUserId).apply();
        }

        Button btnGoToUpload = findViewById(R.id.btnGoToUpload);
        btnGoToUpload.setOnClickListener(v -> {
            Intent intent = new Intent(LibraryActivity.this, UploadActivity.class);
            startActivity(intent);
        });

        EditText editSearch = findViewById(R.id.editSearch);
        editSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                loadCommunityPosts(s.toString().toLowerCase());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        loadCommunityPosts("");
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadCommunityPosts("");
    }

    private void loadCommunityPosts(String searchQuery) {
        feedContainer.removeAllViews();

        db.collection("farming_techniques")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    for (DocumentSnapshot document : queryDocumentSnapshots) {
                        String docId = document.getId(); // The specific file name in the cloud
                        String title = document.getString("title");
                        String desc = document.getString("description");
                        String authorId = document.getString("authorId"); // Who wrote it?

                        if (title == null) title = "";
                        if (desc == null) desc = "";
                        if (authorId == null) authorId = "unknown";

                        if (searchQuery.isEmpty() || title.toLowerCase().contains(searchQuery) || desc.toLowerCase().contains(searchQuery)) {
                            drawPostOnScreen(title, desc, docId, authorId);
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(LibraryActivity.this, "Check your internet connection!", Toast.LENGTH_SHORT).show();
                });
    }

    private void drawPostOnScreen(String title, String description, String docId, String authorId) {
        CardView card = new CardView(this);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        cardParams.setMargins(0, 0, 0, 40);
        card.setLayoutParams(cardParams);
        card.setRadius(16f);
        card.setCardBackgroundColor(Color.parseColor("#1A211A"));
        card.setCardElevation(0f);

        LinearLayout innerLayout = new LinearLayout(this);
        innerLayout.setOrientation(LinearLayout.VERTICAL);
        innerLayout.setPadding(40, 40, 40, 40);

        TextView titleView = new TextView(this);
        titleView.setText("👨‍🌾 " + title);
        titleView.setTextColor(Color.WHITE);
        titleView.setTextSize(18f);
        titleView.setTypeface(null, android.graphics.Typeface.BOLD);
        titleView.setPadding(0, 0, 0, 16);

        TextView descView = new TextView(this);
        descView.setText(description);
        descView.setTextColor(Color.parseColor("#A0AAB2"));
        descView.setTextSize(14f);

        innerLayout.addView(titleView);
        innerLayout.addView(descView);

        // 2. THE DELETE BUTTON LOGIC
        // Only draw the delete button if this phone matches the author!
        if (myUserId.equals(authorId)) {
            Button btnDelete = new Button(this);
            btnDelete.setText("🗑️ Delete My Post");

            // Try to use a red color safely, fallback to standard red if it fails
            try {
                btnDelete.setTextColor(ContextCompat.getColor(this, R.color.alert_red_text));
            } catch (Exception e) {
                btnDelete.setTextColor(Color.RED);
            }

            btnDelete.setBackgroundColor(Color.TRANSPARENT);

            LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            btnParams.setMargins(0, 20, 0, 0);
            btnDelete.setLayoutParams(btnParams);

            // 3. ACTUALLY DELETE FROM CLOUD FIRESTORE
            btnDelete.setOnClickListener(v -> {
                db.collection("farming_techniques").document(docId)
                        .delete()
                        .addOnSuccessListener(aVoid -> {
                            Toast.makeText(this, "Post deleted", Toast.LENGTH_SHORT).show();
                            loadCommunityPosts(""); // Refresh the feed!
                        });
            });

            innerLayout.addView(btnDelete);
        }

        card.addView(innerLayout);
        feedContainer.addView(card);
    }
}