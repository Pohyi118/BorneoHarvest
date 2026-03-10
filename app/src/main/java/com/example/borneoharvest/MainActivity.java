package com.example.borneoharvest;

import android.Manifest;
import android.app.AlertDialog;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.location.Address;
import android.location.Geocoder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.example.borneoharvest.ml.Model;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.messaging.FirebaseMessaging;

import org.json.JSONArray;
import org.json.JSONObject;
import org.tensorflow.lite.DataType;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private ActivityResultLauncher<Uri> cameraLauncher;
    private ActivityResultLauncher<Intent> voiceLauncher;
    private FusedLocationProviderClient fusedLocationClient;
    private Uri imageUri;

    private static final int LOCATION_PERMISSION_CODE = 100;
    private static final int CAMERA_PERMISSION_CODE = 101;

    private TextToSpeech textToSpeech;
    private int globalRainProb = 0;
    private FirebaseFirestore db;
    private long appStartTime;

    private static final String CHANNEL_ID = "COMMUNITY_ALERTS_V2";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        db = FirebaseFirestore.getInstance();
        appStartTime = System.currentTimeMillis();
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        setupAuthAndMessaging();
        initUI();
        initTextToSpeech();
        setupMLDoctor();
        setupVoiceAssistant();

        // Start Location Logic
        checkPermissionAndGetLocation();
        listenForNewDisasterReports();
    }

    private void initTextToSpeech() {
        textToSpeech = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                int result = textToSpeech.setLanguage(new Locale("ms", "MY"));
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    textToSpeech.setLanguage(new Locale("id", "ID"));
                }
            }
        });
    }

    private void checkPermissionAndGetLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_CODE);
        } else {
            fetchDeviceLocation();
        }
    }

    private void fetchDeviceLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return;

        // Force a fresh location update instead of just looking at the old cache
        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener(this, location -> {
                    if (location != null) {
                        fetchLiveWeatherData(location.getLatitude(), location.getLongitude());
                        fetchRiverData(location.getLatitude(), location.getLongitude());
                    } else {
                        // If GPS is stuck, default to Kuching
                        fetchLiveWeatherData(1.5533, 110.3592);
                        fetchRiverData(1.5533, 110.3592);
                    }
                })
                .addOnFailureListener(e -> {
                    fetchLiveWeatherData(1.5533, 110.3592);
                    fetchRiverData(1.5533, 110.3592);
                });
    }

    private void fetchLiveWeatherData(double lat, double lon) {
        Geocoder geocoder = new Geocoder(this, Locale.getDefault());
        try {
            List<Address> addresses = geocoder.getFromLocation(lat, lon, 1);
            if (addresses != null && !addresses.isEmpty()) {
                String city = addresses.get(0).getLocality();
                if (city == null) city = addresses.get(0).getAdminArea();
                TextView tvLoc = findViewById(R.id.tvLocation);
                if (tvLoc != null) tvLoc.setText("📍 " + city);
            }
        } catch (Exception e) {
            TextView tvLoc = findViewById(R.id.tvLocation);
            if (tvLoc != null) tvLoc.setText("📍 Lokasi Dikesan");
        }

        String url = "https://api.open-meteo.com/v1/forecast?latitude=" + lat + "&longitude=" + lon + "&current=temperature_2m,precipitation&timezone=auto";
        Volley.newRequestQueue(this).add(new JsonObjectRequest(Request.Method.GET, url, null, response -> {
            try {
                JSONObject cur = response.getJSONObject("current");
                int temp = (int) Math.round(cur.getDouble("temperature_2m"));
                globalRainProb = (int) (cur.optDouble("precipitation", 0.0) * 10);

                TextView tvTemp = findViewById(R.id.tvTemp);
                TextView tvRain = findViewById(R.id.tvRainProb);

                if (tvTemp != null) tvTemp.setText(temp + "°C ☁️");
                if (tvRain != null) tvRain.setText("🌧️ " + globalRainProb + "%");
            } catch (Exception e) { e.printStackTrace(); }
        }, null));
    }

    private void fetchRiverData(double lat, double lon) {
        String url = "https://flood-api.open-meteo.com/v1/flood?latitude=" + lat + "&longitude=" + lon + "&daily=river_discharge&forecast_days=1";
        Volley.newRequestQueue(this).add(new JsonObjectRequest(Request.Method.GET, url, null, response -> {
            try {
                double d = response.getJSONObject("daily").getJSONArray("river_discharge").getDouble(0);
                if (d > 500.0) sendDisasterNotification("AMARAN BANJIR!", "Paras sungai tinggi.");
            } catch (Exception e) { e.printStackTrace(); }
        }, null));
    }

    private void askGemini(String prompt, GeminiCallback callback) {
        String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3-flash-preview:generateContent?key=" + BuildConfig.GEMINI_API_KEY;

        try {
            JSONObject jsonBody = new JSONObject()
                    .put("contents", new JSONArray()
                            .put(new JSONObject().put("parts", new JSONArray()
                                    .put(new JSONObject().put("text", prompt)))));

            JsonObjectRequest request = new JsonObjectRequest(Request.Method.POST, url, jsonBody,
                    response -> {
                        try {
                            String result = response.getJSONArray("candidates")
                                    .getJSONObject(0).getJSONObject("content")
                                    .getJSONArray("parts").getJSONObject(0)
                                    .getString("text").replace("**", "");
                            callback.onSuccess(result);
                        } catch (Exception e) { callback.onError("Ralat Format AI."); }
                    },
                    error -> callback.onError("Tiada Internet. Menggunakan Mod Luar Talian.")
            );

            // FIX: Extended timeout to 30 seconds so AI Doktor doesn't trigger "Offline" prematurely
            request.setRetryPolicy(new DefaultRetryPolicy(
                    30000,
                    DefaultRetryPolicy.DEFAULT_MAX_RETRIES,
                    DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));

            Volley.newRequestQueue(this).add(request);
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void setupVoiceAssistant() {
        voiceLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                ArrayList<String> matches = result.getData().getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                if (matches != null && !matches.isEmpty()) {
                    ProgressDialog pd = new ProgressDialog(this);
                    pd.setMessage("🤖 Memproses suara...");
                    pd.show();

                    String prompt = "Anda adalah Suara AI Tani. Jawab dalam 2 ayat pendek Bahasa Melayu: " + matches.get(0);
                    askGemini(prompt, new GeminiCallback() {
                        @Override
                        public void onSuccess(String result) {
                            pd.dismiss();
                            if(textToSpeech != null) textToSpeech.speak(result, TextToSpeech.QUEUE_FLUSH, null, "AI_VOICE");
                            showResultDialog("🤖 Pembantu Tani", result);
                        }
                        @Override
                        public void onError(String msg) {
                            pd.dismiss();
                            Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        });
    }

    private void setupMLDoctor() {
        cameraLauncher = registerForActivityResult(new ActivityResultContracts.TakePicture(), isSuccess -> {
            if (isSuccess && imageUri != null) {
                try {
                    Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), imageUri);
                    Bitmap scaled = Bitmap.createScaledBitmap(bitmap, 224, 224, true);

                    Model model = Model.newInstance(this);
                    TensorBuffer input = TensorBuffer.createFixedSize(new int[]{1, 224, 224, 3}, DataType.FLOAT32);
                    TensorImage ti = new TensorImage(DataType.FLOAT32);
                    ti.load(scaled);
                    input.loadBuffer(ti.getBuffer());

                    Model.Outputs outputs = model.process(input);
                    float[] conf = outputs.getOutputFeature0AsTensorBuffer().getFloatArray();
                    model.close();

                    int maxPos = conf[0] > conf[1] ? 0 : 1;
                    String diag = (maxPos == 0) ? "Pokok Sihat" : "Penyakit Bintik Perang";

                    ProgressDialog pd = new ProgressDialog(this);
                    pd.setMessage("🤖 AI sedang menganalisis...");
                    pd.show();

                    askGemini("Berikan 3 langkah rawatan organik untuk " + diag + " dalam Bahasa Melayu.", new GeminiCallback() {
                        @Override
                        public void onSuccess(String result) {
                            pd.dismiss();
                            if(textToSpeech != null) textToSpeech.speak(result, TextToSpeech.QUEUE_FLUSH, null, "DR_VOICE");
                            showResultDialog("🩺 Diagnosis: " + diag, result);
                        }
                        @Override
                        public void onError(String msg) {
                            pd.dismiss();
                            String offlineCure = diag.equals("Penyakit Bintik Perang") ? "Sembur racun kulat organik." : "Teruskan penjagaan biasa.";
                            if(textToSpeech != null) textToSpeech.speak(offlineCure, TextToSpeech.QUEUE_FLUSH, null, "DR_VOICE");
                            showResultDialog("🩺 Status (Mod Luar Talian)", diag + "\n\n" + offlineCure);
                        }
                    });
                } catch (Exception e) { e.printStackTrace(); }
            }
        });
    }

    private void initUI() {
        View btnAgriMatch = findViewById(R.id.btnAgriMatch);
        if (btnAgriMatch != null) btnAgriMatch.setOnClickListener(v -> showAgriMatchDialog());

        View btnLibrary = findViewById(R.id.btnLibrary);
        if (btnLibrary != null) btnLibrary.setOnClickListener(v -> startActivity(new Intent(this, LibraryActivity.class)));

        View btnDisasterCenter = findViewById(R.id.btnDisasterCenter);
        if (btnDisasterCenter != null) btnDisasterCenter.setOnClickListener(v -> openDisasterCenter());

        View btnVoiceAsk = findViewById(R.id.btnVoiceAsk);
        if (btnVoiceAsk != null) btnVoiceAsk.setOnClickListener(v -> {
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ms-MY");
            voiceLauncher.launch(intent);
        });

        View btnScan = findViewById(R.id.btnScan);
        if (btnScan != null) {
            btnScan.setOnClickListener(v -> {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                    try {
                        File imagePath = new File(getCacheDir(), "images");
                        if (!imagePath.exists()) imagePath.mkdirs();

                        File file = new File(imagePath, "temp_plant.jpg");
                        imageUri = FileProvider.getUriForFile(this, getPackageName() + ".provider", file);
                        cameraLauncher.launch(imageUri);
                    } catch (Exception e) {
                        Toast.makeText(this, "Ralat Kamera: Sila periksa Manifest FileProvider.", Toast.LENGTH_LONG).show();
                        Log.e("CAMERA_ERROR", e.getMessage());
                    }
                } else {
                    ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_CODE);
                }
            });
        }
    }

    private void showResultDialog(String title, String msg) {
        new AlertDialog.Builder(this).setTitle(title).setMessage(msg).setPositiveButton("OK", null).show();
    }

    private void setupAuthAndMessaging() {
        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() == null) auth.signInAnonymously();
        FirebaseMessaging.getInstance().subscribeToTopic("all_farmers");
        createNotificationChannel();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Alerts", NotificationManager.IMPORTANCE_HIGH);
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }

    private void openDisasterCenter() {
        Intent intent = new Intent(this, DisasterActivity.class);
        intent.putExtra("RAIN_VAL", globalRainProb);
        startActivity(intent);
    }

    private void listenForNewDisasterReports() {
        db.collection("DisasterReports").whereGreaterThan("timestamp", appStartTime)
                .addSnapshotListener((snapshots, e) -> {
                    if (snapshots != null) {
                        for (DocumentChange dc : snapshots.getDocumentChanges()) {
                            if (dc.getType() == DocumentChange.Type.ADDED)
                                sendDisasterNotification("Laporan Baru!", "Bencana dikesan di komuniti.");
                        }
                    }
                });
    }

    private void sendDisasterNotification(String title, String msg) {
        NotificationCompat.Builder b = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_alert).setContentTitle(title).setContentText(msg).setPriority(NotificationCompat.PRIORITY_MAX).setAutoCancel(true);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            NotificationManagerCompat.from(this).notify((int) System.currentTimeMillis(), b.build());
        }
    }

    // RESTORED: AgriMatch Dialog Function
    private void showAgriMatchDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_agri_match, null);

        android.widget.EditText etCrop = dialogView.findViewById(R.id.etCropType);
        android.widget.EditText etDistrict = dialogView.findViewById(R.id.etDistrict);
        android.widget.EditText etSize = dialogView.findViewById(R.id.etFarmSize);
        android.widget.Button btnSave = dialogView.findViewById(R.id.btnSaveProfile);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setView(dialogView);
        AlertDialog dialog = builder.create();

        btnSave.setOnClickListener(v -> {
            String crop = etCrop.getText().toString().trim();
            String district = etDistrict.getText().toString().trim();
            String sizeStr = etSize.getText().toString().trim();

            if (crop.isEmpty() || district.isEmpty() || sizeStr.isEmpty()) {
                Toast.makeText(this, "Sila isikan semua maklumat.", Toast.LENGTH_SHORT).show();
                return;
            }

            double size = Double.parseDouble(sizeStr);
            syncAgriMatchProfile(crop, district, size);
            dialog.dismiss();
        });

        dialog.show();
    }

    // RESTORED: AgriMatch Firebase Sync Function
    private void syncAgriMatchProfile(String selectedCrop, String selectedDistrict, double size) {
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            Toast.makeText(this, "Sila log masuk.", Toast.LENGTH_SHORT).show();
            return;
        }

        String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        FirebaseMessaging.getInstance().getToken().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                String token = task.getResult();
                FarmerProfile profile = new FarmerProfile(selectedCrop, selectedDistrict, size, token);
                db.collection("Users").document(userId).set(profile, SetOptions.merge())
                        .addOnSuccessListener(aVoid -> {
                            Toast.makeText(this, "Profil Disimpan! Mencari padanan...", Toast.LENGTH_SHORT).show();
                            Intent intent = new Intent(MainActivity.this, MatchResultsActivity.class);
                            intent.putExtra("CROP_TYPE", selectedCrop);
                            startActivity(intent);
                        });
            }
        });
    }

    interface GeminiCallback { void onSuccess(String res); void onError(String err); }
}