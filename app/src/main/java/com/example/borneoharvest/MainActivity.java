package com.example.borneoharvest;

import android.Manifest;
import android.app.AlertDialog;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.SharedPreferences;
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
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.example.borneoharvest.ml.Model;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;

import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.firestore.SetOptions;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.tensorflow.lite.DataType;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // --- 1. SUBSCRIBE TO TOPIC FOR LIVE ANNOUNCEMENTS ---
        FirebaseMessaging.getInstance().subscribeToTopic("all_farmers")
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Log.d("FCM", "Subscribed to all_farmers topic");
                    }
                });

        // --- 2. AUTHENTICATION (ANONYMOUS) ---
        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() == null) {
            auth.signInAnonymously()
                    .addOnCompleteListener(this, task -> {
                        if (task.isSuccessful()) {
                            Log.d("AUTH", "Log masuk berjaya!");
                        } else {
                            Toast.makeText(this, "Ralat pengesahan Firebase.", Toast.LENGTH_SHORT).show();
                        }
                    });
        }

        // --- 3. UI INITIALIZATION ---
        createNotificationChannel();
        CardView btnAgriMatch = findViewById(R.id.btnAgriMatch);
        btnAgriMatch.setOnClickListener(v -> showAgriMatchDialog());

        CardView btnScan = findViewById(R.id.btnScan);
        CardView btnLibrary = findViewById(R.id.btnLibrary);
        CardView btnVoiceAsk = findViewById(R.id.btnVoiceAsk);
        CardView btnDisasterCenter = findViewById(R.id.btnDisasterCenter);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, 101);
            }
        }

        db = FirebaseFirestore.getInstance();
        appStartTime = System.currentTimeMillis();
        listenForNewDisasterReports();

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        textToSpeech = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                int result = textToSpeech.setLanguage(new Locale("ms", "MY"));
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    textToSpeech.setLanguage(new Locale("id", "ID"));
                }
            }
        });

        checkPermissionAndGetLocation();

        // --- 4. CAMERA & ML DOCTOR LOGIC ---
        cameraLauncher = registerForActivityResult(
                new ActivityResultContracts.TakePicture(),
                isSuccess -> {
                    if (isSuccess && imageUri != null) {
                        try {
                            Bitmap imageBitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), imageUri);
                            Bitmap scaledBitmap = Bitmap.createScaledBitmap(imageBitmap, 224, 224, true);

                            Model model = Model.newInstance(getApplicationContext());
                            TensorBuffer inputFeature0 = TensorBuffer.createFixedSize(new int[]{1, 224, 224, 3}, DataType.FLOAT32);
                            TensorImage tensorImage = new TensorImage(DataType.FLOAT32);
                            tensorImage.load(scaledBitmap);
                            ByteBuffer byteBuffer = tensorImage.getBuffer();
                            inputFeature0.loadBuffer(byteBuffer);

                            Model.Outputs outputs = model.process(inputFeature0);
                            TensorBuffer outputFeature0 = outputs.getOutputFeature0AsTensorBuffer();
                            model.close();

                            float[] confidences = outputFeature0.getFloatArray();
                            int maxPos = 0;
                            float maxConfidence = 0;
                            for (int i = 0; i < confidences.length; i++) {
                                if (confidences[i] > maxConfidence) {
                                    maxConfidence = confidences[i];
                                    maxPos = i;
                                }
                            }

                            String[] classes = {"Pokok Sihat", "Penyakit Bintik Perang"};
                            String diagnosis = classes[maxPos];

                            android.app.ProgressDialog loadingDialog = new android.app.ProgressDialog(MainActivity.this);
                            loadingDialog.setMessage("🤖 AI sedang menganalisis daun...");
                            loadingDialog.setCancelable(false);
                            loadingDialog.show();

                            String prompt = "Diagnosis: " + diagnosis + ". Berikan 3 langkah rawatan organik ringkas dalam Bahasa Melayu.";

                            askGemini(prompt, new GeminiCallback() {
                                @Override
                                public void onSuccess(String result) {
                                    loadingDialog.dismiss();
                                    if(textToSpeech != null) textToSpeech.speak(result, TextToSpeech.QUEUE_FLUSH, null, null);
                                    new AlertDialog.Builder(MainActivity.this)
                                            .setTitle("🩺 Keputusan: " + diagnosis)
                                            .setMessage(result)
                                            .setPositiveButton("Tutup", null).show();
                                }
                                @Override
                                public void onError(String errorMessage) {
                                    loadingDialog.dismiss();
                                    String offlineCure = diagnosis.equals("Penyakit Bintik Perang") ?
                                            "Potong daun terjejas dan sembur racun kulat organik." : "Pokok sihat.";
                                    new AlertDialog.Builder(MainActivity.this)
                                            .setTitle("🩺 Keputusan (Offline): " + diagnosis)
                                            .setMessage(offlineCure).setPositiveButton("Tutup", null).show();
                                }
                            });
                        } catch (IOException e) { e.printStackTrace(); }
                    }
                }
        );

        // --- 5. VOICE AI LOGIC ---
        voiceLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        ArrayList<String> matches = result.getData().getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                        if (matches != null && !matches.isEmpty()) {
                            String spokenText = matches.get(0);
                            android.app.ProgressDialog voiceLoadingDialog = new android.app.ProgressDialog(MainActivity.this);
                            voiceLoadingDialog.setMessage("🤖 Pembantu Tani sedang berfikir...");
                            voiceLoadingDialog.show();

                            String systemPrompt = "Anda adalah Pembantu Tani Borneo. Jawab dengan praktikal dalam 2 ayat Bahasa Melayu: " + spokenText;
                            askGemini(systemPrompt, new GeminiCallback() {
                                @Override
                                public void onSuccess(String result) {
                                    voiceLoadingDialog.dismiss();
                                    if(textToSpeech != null) textToSpeech.speak(result, TextToSpeech.QUEUE_FLUSH, null, null);
                                    new AlertDialog.Builder(MainActivity.this)
                                            .setTitle("🤖 Pembantu Tani AI")
                                            .setMessage(result).setPositiveButton("Tutup", null).show();
                                }
                                @Override
                                public void onError(String error) { voiceLoadingDialog.dismiss(); }
                            });
                        }
                    }
                }
        );

        // --- BUTTON LISTENERS ---
        btnScan.setOnClickListener(v -> {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                File imagePath = new File(getCacheDir(), "images");
                imagePath.mkdirs();
                File newFile = new File(imagePath, "temp_plant.jpg");
                imageUri = FileProvider.getUriForFile(this, getPackageName() + ".provider", newFile);
                cameraLauncher.launch(imageUri);
            } else {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_CODE);
            }
        });

        btnVoiceAsk.setOnClickListener(v -> {
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ms-MY");
            try { voiceLauncher.launch(intent); } catch (Exception e) { e.printStackTrace(); }
        });

        btnLibrary.setOnClickListener(v -> startActivity(new Intent(MainActivity.this, LibraryActivity.class)));
        btnDisasterCenter.setOnClickListener(v -> openDisasterCenter());
    }

    // --- AGRI-MATCH PROFILE SYNC ---
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

    // --- WEATHER & FLOOD API ---
    private void fetchLiveWeatherData(double lat, double lon) {
        String url = "https://api.open-meteo.com/v1/forecast?latitude=" + lat + "&longitude=" + lon + "&current=temperature_2m,precipitation&daily=precipitation_sum&timezone=auto";
        RequestQueue queue = Volley.newRequestQueue(this);
        JsonObjectRequest request = new JsonObjectRequest(Request.Method.GET, url, null, response -> {
            try {
                JSONObject current = response.getJSONObject("current");
                int temp = (int) Math.round(current.getDouble("temperature_2m"));
                globalRainProb = (int) Math.min(current.optDouble("precipitation", 0.0) * 10, 100);

                JSONObject daily = response.getJSONObject("daily");
                JSONArray rainArray = daily.getJSONArray("precipitation_sum");
                int[] weeklyRain = new int[5];
                for (int i = 0; i < 5; i++) weeklyRain[i] = (int) Math.round(rainArray.getDouble(i));

                updateDashboard(temp, globalRainProb, weeklyRain);
            } catch (JSONException e) { e.printStackTrace(); }
        }, null);
        queue.add(request);
    }

    private void fetchRiverData(double lat, double lon) {
        String url = "https://flood-api.open-meteo.com/v1/flood?latitude=" + lat + "&longitude=" + lon + "&daily=river_discharge&forecast_days=3";
        Volley.newRequestQueue(this).add(new JsonObjectRequest(Request.Method.GET, url, null, response -> {
            try {
                double discharge = response.getJSONObject("daily").getJSONArray("river_discharge").getDouble(0);
                if (discharge > 500.0) sendDisasterNotification("AMARAN BANJIR!", "Paras sungai tinggi.");
            } catch (JSONException e) { e.printStackTrace(); }
        }, null));
    }

    // --- HELPER METHODS ---
    private void askGemini(String prompt, GeminiCallback callback) {
        String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3-flash-preview:generateContent?key=" + BuildConfig.GEMINI_API_KEY;
        try {
            JSONObject body = new JSONObject().put("contents", new JSONArray().put(new JSONObject().put("parts", new JSONArray().put(new JSONObject().put("text", prompt)))));
            Volley.newRequestQueue(this).add(new JsonObjectRequest(Request.Method.POST, url, body, response -> {
                try {
                    callback.onSuccess(response.getJSONArray("candidates").getJSONObject(0).getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text").replace("**", ""));
                } catch (JSONException e) { callback.onError("Ralat AI."); }
            }, error -> callback.onError("Ralat Internet.")));
        } catch (JSONException e) { e.printStackTrace(); }
    }

    private void updateDashboard(int temp, int rainProb, int[] rainfall) {
        ((TextView)findViewById(R.id.tvTemp)).setText(temp + "°C ☁️");
        ((TextView)findViewById(R.id.tvRainProb)).setText("🌧️ " + rainProb + "%");
        findViewById(R.id.cardAlertRed).setVisibility(rainProb > 60 ? View.VISIBLE : View.GONE);
        findViewById(R.id.cardAlertYellow).setVisibility(rainProb > 60 ? View.GONE : View.VISIBLE);
    }

    private void listenForNewDisasterReports() {
        db.collection("DisasterReports").whereGreaterThan("timestamp", appStartTime)
                .addSnapshotListener((snapshots, e) -> {
                    if (snapshots != null) {
                        for (DocumentChange dc : snapshots.getDocumentChanges()) {
                            if (dc.getType() == DocumentChange.Type.ADDED) sendDisasterNotification("Laporan Baru!", "Bencana dikesan di komuniti.");
                        }
                    }
                });
    }

    private void sendDisasterNotification(String title, String message) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_alert).setContentTitle(title).setContentText(message).setPriority(NotificationCompat.PRIORITY_MAX).setAutoCancel(true);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            NotificationManagerCompat.from(this).notify((int) System.currentTimeMillis(), builder.build());
        }
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

    private void checkPermissionAndGetLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_CODE);
        } else { fetchDeviceLocation(); }
    }

    private void fetchDeviceLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.getLastLocation().addOnSuccessListener(this, location -> {
                if (location != null) {
                    fetchLiveWeatherData(location.getLatitude(), location.getLongitude());
                    fetchRiverData(location.getLatitude(), location.getLongitude());
                } else {
                    fetchLiveWeatherData(1.5533, 110.3592);
                }
            });
        }
    }

    interface GeminiCallback { void onSuccess(String result); void onError(String errorMessage); }
}