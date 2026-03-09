package com.example.borneoharvest;

import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

public class MatchResultsActivity extends AppCompatActivity {

    private LinearLayout containerResults;
    private TextView tvMatchSubtitle;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_match_results);

        containerResults = findViewById(R.id.containerResults);
        tvMatchSubtitle = findViewById(R.id.tvMatchSubtitle);
        db = FirebaseFirestore.getInstance();

        // Get the crop type the farmer typed in the popup
        String userCrop = getIntent().getStringExtra("CROP_TYPE");

        if (userCrop != null && !userCrop.isEmpty()) {
            tvMatchSubtitle.setText("Padanan bantuan untuk: " + userCrop.toUpperCase());
            searchIncentives(userCrop);
        } else {
            tvMatchSubtitle.setText("Ralat memuatkan profil tanaman.");
        }
    }

    private void searchIncentives(String cropType) {
        // Search Firebase for EVERYTHING matching this crop
        db.collection("Subsidies")
                .whereEqualTo("targetCrop", cropType)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        if (task.getResult().isEmpty()) {
                            showEmptyState();
                        } else {
                            for (QueryDocumentSnapshot document : task.getResult()) {
                                String title = document.getString("title");
                                String provider = document.getString("provider");
                                String amount = document.getString("amount");
                                String type = document.getString("type");
                                String category = document.getString("category"); // Kerajaan Pusat, Negeri, Bank

                                addResultCard(title, provider, amount, type, category);
                            }
                        }
                    } else {
                        Toast.makeText(this, "Gagal menyambung ke pangkalan data.", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    // Dynamically builds the UI cards with color-coded categories
    private void addResultCard(String title, String provider, String amount, String type, String category) {
        CardView card = new CardView(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, 32);
        card.setLayoutParams(params);
        card.setRadius(20f);
        card.setCardElevation(6f);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(40, 40, 40, 40);

        // --- THE CATEGORY BADGE (Federal, State, or Bank) ---
        TextView tvCategory = new TextView(this);
        if (category == null) category = "Bantuan Umum";
        tvCategory.setText(" " + category.toUpperCase() + " ");
        tvCategory.setTextSize(12f);
        tvCategory.setTypeface(null, Typeface.BOLD);
        tvCategory.setPadding(16, 8, 16, 8);
        tvCategory.setGravity(Gravity.CENTER);

        // Color coding the badge based on the category
        if (category.toLowerCase().contains("pusat") || category.toLowerCase().contains("persekutuan")) {
            tvCategory.setBackgroundColor(Color.parseColor("#FFD54F")); // Yellow for Federal
            tvCategory.setTextColor(Color.BLACK);
        } else if (category.toLowerCase().contains("negeri")) {
            tvCategory.setBackgroundColor(Color.parseColor("#4FC3F7")); // Blue for State
            tvCategory.setTextColor(Color.BLACK);
        } else if (category.toLowerCase().contains("bank")) {
            tvCategory.setBackgroundColor(Color.parseColor("#81C784")); // Green for Bank Loans
            tvCategory.setTextColor(Color.BLACK);
        } else {
            tvCategory.setBackgroundColor(Color.LTGRAY);
            tvCategory.setTextColor(Color.BLACK);
        }

        LinearLayout.LayoutParams badgeParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        badgeParams.setMargins(0, 0, 0, 16);
        tvCategory.setLayoutParams(badgeParams);

        // --- TITLE ---
        TextView tvTitle = new TextView(this);
        tvTitle.setText(title != null ? title : "Tiada Tajuk");
        tvTitle.setTextSize(20f);
        tvTitle.setTextColor(Color.BLACK);
        tvTitle.setTypeface(null, Typeface.BOLD);
        tvTitle.setPadding(0, 0, 0, 8);

        // --- PROVIDER ---
        TextView tvProvider = new TextView(this);
        tvProvider.setText("🏢 Agensi: " + (provider != null ? provider : "-"));
        tvProvider.setTextSize(14f);
        tvProvider.setTextColor(Color.parseColor("#555555"));

        // --- TYPE (Grant vs Loan) ---
        TextView tvType = new TextView(this);
        tvType.setText("📋 Jenis: " + (type != null ? type : "-"));
        tvType.setTextSize(14f);
        tvType.setTextColor(Color.parseColor("#555555"));

        // --- AMOUNT ---
        TextView tvAmount = new TextView(this);
        tvAmount.setText("💰 " + (amount != null ? amount : "-"));
        tvAmount.setTextColor(Color.parseColor("#D32F2F")); // Red to make money pop
        tvAmount.setPadding(0, 16, 0, 0);
        tvAmount.setTextSize(16f);
        tvAmount.setTypeface(null, Typeface.BOLD);

        // Assemble the card
        layout.addView(tvCategory);
        layout.addView(tvTitle);
        layout.addView(tvProvider);
        layout.addView(tvType);
        layout.addView(tvAmount);

        card.addView(layout);
        containerResults.addView(card);
    }

    private void showEmptyState() {
        TextView empty = new TextView(this);
        empty.setText("Tiada insentif kerajaan atau pinjaman bank ditemui untuk tanaman ini pada masa kini.");
        empty.setPadding(32, 32, 32, 32);
        empty.setGravity(Gravity.CENTER);
        containerResults.addView(empty);
    }
}
