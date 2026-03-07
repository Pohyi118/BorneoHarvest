package com.example.borneoharvest;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.util.Base64;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.io.ByteArrayOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class DisasterActivity extends AppCompatActivity {

    private ActivityResultLauncher<Void> cameraLauncher;
    private LinearLayout reportContainer;

    private String selectedDisasterType = "";
    private String userDescription = "";
    private String currentLocation = ""; // Stores the user's GPS city

    private FirebaseFirestore db;
    private TextToSpeech textToSpeech; // Voice Engine
    private static final int CAMERA_PERMISSION_CODE = 102;

    // This forces Android to reset sound/popup permissions!
    private static final String CHANNEL_ID = "COMMUNITY_ALERTS_V2";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_disaster);

        db = FirebaseFirestore.getInstance();
        createNotificationChannel();

        // Initialize Voice Assistant with Fallback
        textToSpeech = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                int result = textToSpeech.setLanguage(new Locale("ms", "MY"));
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    textToSpeech.setLanguage(new Locale("id", "ID")); // Fallback
                }
            }
        });

        // Grab the GPS Location saved by MainActivity
        SharedPreferences prefs = getSharedPreferences("OfflineWeather", MODE_PRIVATE);
        currentLocation = prefs.getString("savedCity", "Kawasan Borneo");

        TextView tvTitle = findViewById(R.id.tvStatusTitle);
        TextView tvAdvice = findViewById(R.id.tvDisasterAdvice);
        Button btnReport = findViewById(R.id.btnReportDisaster);
        reportContainer = findViewById(R.id.reportContainer);

        int rain = getIntent().getIntExtra("RAIN_VAL", 0);

        // HACKATHON DEMO TOGGLE
        // rain = 95;

        if (rain > 80) {
            tvTitle.setText("AMARAN: Risiko Banjir (Tahap Bahaya)");
            tvTitle.setTextColor(Color.parseColor("#FF5252"));
            tvAdvice.setText("Amaran Hujan Lebat. Sila alihkan jentera ke tempat tinggi.");
        } else if (rain > 50) {
            tvTitle.setText("Status: Berjaga-jaga");
            tvTitle.setTextColor(Color.parseColor("#FFD93D"));
            tvAdvice.setText("Hujan sederhana. Pastikan sistem parit tidak tersumbat.");
        } else {
            tvTitle.setText("Status: Selamat");
            tvTitle.setTextColor(Color.parseColor("#4CAF50"));
            tvAdvice.setText("Tiada ancaman dikesan. Sesuai untuk aktiviti ladang.");
        }

        loadLiveCommunityReports();

        // SAFE CAMERA LOGIC
        cameraLauncher = registerForActivityResult(
                new ActivityResultContracts.TakePicturePreview(),
                bitmap -> {
                    try {
                        String currentTime = new SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(new Date());

                        if (bitmap != null) {
                            Bitmap tinyBitmap = Bitmap.createScaledBitmap(bitmap, 150, 150, true);
                            ByteArrayOutputStream baos = new ByteArrayOutputStream();
                            tinyBitmap.compress(Bitmap.CompressFormat.JPEG, 40, baos);
                            byte[] imageBytes = baos.toByteArray();
                            String base64ImageString = Base64.encodeToString(imageBytes, Base64.NO_WRAP);

                            uploadReportToFirestore("🚨 " + selectedDisasterType, userDescription, currentTime, currentLocation, base64ImageString);
                        } else {
                            Toast.makeText(DisasterActivity.this, "Gambar dibatalkan.", Toast.LENGTH_SHORT).show();
                        }
                    } catch (Exception e) {
                        Toast.makeText(this, "Ralat Kamera.", Toast.LENGTH_LONG).show();
                    }
                }
        );

        // SAFE BUTTON LOGIC
        btnReport.setOnClickListener(v -> {
            String[] options = {"Banjir Kilat 🌊", "Tanah Runtuh ⛰️", "Serangan Babi Hutan 🐗", "Pokok Tumbang 🌳"};

            new AlertDialog.Builder(DisasterActivity.this)
                    .setTitle("Pilih Jenis Bencana")
                    .setItems(options, (dialog, which) -> {
                        selectedDisasterType = options[which];

                        final EditText inputDescription = new EditText(DisasterActivity.this);
                        inputDescription.setHint("Nyatakan butiran kejadian...");
                        inputDescription.setPadding(60, 40, 60, 40);

                        new AlertDialog.Builder(DisasterActivity.this)
                                .setTitle("Bukti Bencana")
                                .setMessage("Lokasi dikesan: " + currentLocation + "\n\nSila muat naik foto kejadian.")
                                .setView(inputDescription)
                                .setPositiveButton("Buka Kamera", (d, w) -> {
                                    userDescription = inputDescription.getText().toString().trim();
                                    if (userDescription.isEmpty()) userDescription = "Laporan disahkan komuniti.";

                                    if (ContextCompat.checkSelfPermission(DisasterActivity.this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                                        cameraLauncher.launch(null);
                                    } else {
                                        ActivityCompat.requestPermissions(DisasterActivity.this, new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_CODE);
                                    }
                                })
                                .setNegativeButton("Hantar Tanpa Foto", (d, w) -> {
                                    try {
                                        userDescription = inputDescription.getText().toString().trim();
                                        if (userDescription.isEmpty()) userDescription = "Laporan disahkan komuniti.";

                                        String currentTime = new SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(new Date());
                                        uploadReportToFirestore("🚨 " + selectedDisasterType, userDescription + " (Tiada Foto)", currentTime, currentLocation, "NO_PHOTO");
                                    } catch (Exception e) {
                                        Toast.makeText(DisasterActivity.this, "Ralat Butang.", Toast.LENGTH_LONG).show();
                                    }
                                })
                                .show();
                    })
                    .show();
        });
    }

    // OFFLINE-FIRST FIRESTORE UPLOAD
    private void uploadReportToFirestore(String title, String description, String time, String location, String base64Image) {
        try {

            // TRIGGER SOUND, NOTIFICATION & UI *IMMEDIATELY* (OFFLINE-PROOF)

            triggerRealPopupNotification(title);
            addNewReportToScreen(title, description, time, location, true, !base64Image.equals("NO_PHOTO"));

            // BACKGROUND SYNC TO FIRESTORE
            Map<String, Object> report = new HashMap<>();
            report.put("title", title);
            report.put("description", description);
            report.put("time", time);
            report.put("location", location); // Saving location tag to cloud
            report.put("imageBase64", base64Image);
            report.put("timestamp", System.currentTimeMillis());

            db.collection("DisasterReports")
                    .add(report)
                    .addOnSuccessListener(documentReference -> {
                        Toast.makeText(DisasterActivity.this, "Berjaya disimpan di pangkalan utama!", Toast.LENGTH_SHORT).show();
                    })
                    .addOnFailureListener(e -> {
                        // Fails silently if offline, Firebase will auto-upload when internet returns
                    });
        } catch (Exception e) {
            Toast.makeText(this, "Ralat Sistem.", Toast.LENGTH_LONG).show();
        }
    }

    // SAFE FEED LOADER
    private void loadLiveCommunityReports() {
        try {
            db.collection("DisasterReports")
                    .orderBy("timestamp", Query.Direction.DESCENDING)
                    .get()
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            reportContainer.removeAllViews();
                            if (task.getResult().isEmpty()) {
                                addNewReportToScreen("✅ Komuniti Selamat", "Tiada laporan bencana aktif.", "Sistem Tuai Borneo", "Borneo", false, false);
                            } else {
                                for (QueryDocumentSnapshot document : task.getResult()) {
                                    String title = document.getString("title");
                                    String desc = document.getString("description");
                                    String time = document.getString("time");
                                    String loc = document.getString("location"); // Fetching location tag

                                    String imgString = document.getString("imageBase64");
                                    boolean hasPhoto = (imgString != null && !imgString.equals("NO_PHOTO"));

                                    addNewReportToScreen(
                                            title != null ? title : "Bencana",
                                            desc != null ? desc : "",
                                            time != null ? time : "",
                                            loc != null ? loc : "Lokasi Tidak Diketahui",
                                            false, hasPhoto);
                                }
                            }
                        }
                    });
        } catch (Exception e) {
            Toast.makeText(this, "Ralat Muat Turun.", Toast.LENGTH_LONG).show();
        }
    }

    // UI GENERATOR (NOW WITH LOCATION TAG)
    private void addNewReportToScreen(String title, String description, String time, String location, boolean isNew, boolean hasPhoto) {
        CardView card = new CardView(this);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        cardParams.setMargins(0, 0, 0, 30);
        card.setLayoutParams(cardParams);
        card.setRadius(16f);

        card.setCardBackgroundColor(isNew ? Color.parseColor("#3D2B2B") : Color.parseColor("#1A211A"));
        card.setCardElevation(0f);

        LinearLayout innerLayout = new LinearLayout(this);
        innerLayout.setOrientation(LinearLayout.VERTICAL);
        innerLayout.setPadding(40, 40, 40, 40);

        TextView titleView = new TextView(this);
        titleView.setText(title);
        titleView.setTextColor(Color.WHITE);
        titleView.setTextSize(16f);
        titleView.setTypeface(null, android.graphics.Typeface.BOLD);

        // Location Tag
        TextView locationView = new TextView(this);
        locationView.setText("📍 " + location);
        locationView.setTextColor(Color.parseColor("#FFD93D")); // Yellow accent for location
        locationView.setTextSize(13f);
        locationView.setPadding(0, 5, 0, 5);

        TextView descView = new TextView(this);
        descView.setText(description);
        descView.setTextColor(Color.parseColor("#A0AAB2"));
        descView.setTextSize(14f);
        descView.setPadding(0, 10, 0, 10);

        TextView timeView = new TextView(this);
        String timeText = "🕒 " + time;
        if (hasPhoto) timeText += "  |  📸 Foto Disahkan";
        timeView.setText(timeText);
        timeView.setTextColor(Color.parseColor("#7A8B99"));
        timeView.setTextSize(12f);

        innerLayout.addView(titleView);
        innerLayout.addView(locationView); // Added location to card
        innerLayout.addView(descView);
        innerLayout.addView(timeView);

        card.addView(innerLayout);
        if (isNew) reportContainer.addView(card, 0);
        else reportContainer.addView(card);
    }

    // SAFE NOTIFICATION TRIGGER WITH SOUND FORCED
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Using V2 bypasses Android's cache and forces the sound setting to be applied!
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "Laporan Komuniti Penting", NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("Notifikasi Kecemasan Bencana");
            channel.enableVibration(true); // Forces vibration
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }

    private void triggerRealPopupNotification(String title) {
        try {
            Intent intent = new Intent(this, DisasterActivity.class);
            PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE);

            NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.ic_dialog_alert)
                    .setContentTitle("🚨 AMARAN KOMUNITI!")
                    .setContentText("Kecemasan: " + title)
                    .setPriority(NotificationCompat.PRIORITY_MAX)
                    .setDefaults(NotificationCompat.DEFAULT_ALL) // Triggers system sound and vibration
                    .setAutoCancel(true)
                    .setContentIntent(pendingIntent);

            NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                notificationManager.notify((int) System.currentTimeMillis(), builder.build());
            }

            // Speak out loud when the notification pops!
            if (textToSpeech != null) {
                textToSpeech.speak("Amaran Komuniti Dikesan. " + title, TextToSpeech.QUEUE_FLUSH, null, null);
            }

            new AlertDialog.Builder(this)
                    .setTitle("✅ Laporan Disimpan!")
                    .setMessage("Notifikasi amaran sedang dihantar ke peranti berdekatan.")
                    .setPositiveButton("Tutup", null)
                    .show();
        } catch (Exception e) {
            Toast.makeText(this, "Ralat Notifikasi.", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onDestroy() {
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
        super.onDestroy();
    }
}