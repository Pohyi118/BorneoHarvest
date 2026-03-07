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

// NEW IMPORTS FOR REAL-TIME SYNC
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.FirebaseFirestore;

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

    // NEW VARIABLES FOR REAL-TIME SYNC
    private FirebaseFirestore db;
    private long appStartTime;

    private static final String CHANNEL_ID = "COMMUNITY_ALERTS_V2";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        createNotificationChannel();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, 101);
            }
        }


        // REAL-TIME SYNC INITIALIZATION

        db = FirebaseFirestore.getInstance();
        appStartTime = System.currentTimeMillis(); // Records when you opened the app
        listenForNewDisasterReports(); // Starts the background listener

        CardView btnScan = findViewById(R.id.btnScan);
        CardView btnLibrary = findViewById(R.id.btnLibrary);
        CardView btnVoiceAsk = findViewById(R.id.btnVoiceAsk);
        CardView btnDisasterCenter = findViewById(R.id.btnDisasterCenter);

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
                            loadingDialog.setMessage("🤖 AI sedang menganalisis daun...\nSila tunggu sebentar.");
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
                                            .setPositiveButton("Tutup", null)
                                            .show();
                                }

                                @Override
                                public void onError(String errorMessage) {
                                    loadingDialog.dismiss();

                                    String offlineCure = diagnosis.equals("Penyakit Bintik Perang") ?
                                            "Potong daun terjejas dan sembur racun kulat organik." : "Pokok sihat, teruskan penjagaan biasa.";

                                    if(textToSpeech != null) textToSpeech.speak("Luar talian. " + offlineCure, TextToSpeech.QUEUE_FLUSH, null, null);

                                    new AlertDialog.Builder(MainActivity.this)
                                            .setTitle("🩺 Keputusan (Luar Talian): " + diagnosis)
                                            .setMessage(offlineCure + "\n\n(Sila sambung ke internet untuk rawatan AI terperinci).")
                                            .setPositiveButton("Tutup", null)
                                            .show();
                                }
                            });
                        } catch (IOException e) {
                            Toast.makeText(MainActivity.this, "Ralat Model: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Toast.makeText(MainActivity.this, "Gambar dibatalkan.", Toast.LENGTH_SHORT).show();
                    }
                }
        );

        voiceLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        ArrayList<String> matches = result.getData().getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                        if (matches != null && !matches.isEmpty()) {

                            String spokenText = matches.get(0);

                            android.app.ProgressDialog voiceLoadingDialog = new android.app.ProgressDialog(MainActivity.this);
                            voiceLoadingDialog.setMessage("🤖 Pembantu Tani sedang berfikir...\nSila tunggu.");
                            voiceLoadingDialog.setCancelable(false);
                            voiceLoadingDialog.show();

                            String systemPrompt = "Anda adalah Pembantu Tani Borneo, pakar pertanian organik. Jawab soalan ini dengan praktikal dalam 2 ayat Bahasa Melayu: " + spokenText;

                            askGemini(systemPrompt, new GeminiCallback() {
                                @Override
                                public void onSuccess(String result) {
                                    voiceLoadingDialog.dismiss();

                                    if(textToSpeech != null) textToSpeech.speak(result, TextToSpeech.QUEUE_FLUSH, null, null);

                                    new AlertDialog.Builder(MainActivity.this)
                                            .setTitle("🤖 Pembantu Tani AI")
                                            .setMessage("Anda: \"" + spokenText + "\"\n\n" + result)
                                            .setPositiveButton("Tutup", null)
                                            .show();
                                }

                                @Override
                                public void onError(String error) {
                                    voiceLoadingDialog.dismiss();

                                    Toast.makeText(MainActivity.this, error, Toast.LENGTH_SHORT).show();
                                    String offlineMessage = "Maaf, tiada internet. Sila rujuk Pusat Bencana untuk maklumat offline.";

                                    if(textToSpeech != null) textToSpeech.speak(offlineMessage, TextToSpeech.QUEUE_FLUSH, null, null);
                                }
                            });
                        }
                    }
                }
        );

        btnScan.setOnClickListener(v -> {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                try {
                    File imagePath = new File(getCacheDir(), "images");
                    imagePath.mkdirs();
                    File newFile = new File(imagePath, "temp_plant.jpg");
                    imageUri = FileProvider.getUriForFile(this, getPackageName() + ".provider", newFile);
                    cameraLauncher.launch(imageUri);
                } catch (Exception e) {
                    Toast.makeText(this, "Ralat sistem fail.", Toast.LENGTH_SHORT).show();
                }
            } else {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_CODE);
            }
        });

        btnLibrary.setOnClickListener(v -> startActivity(new Intent(MainActivity.this, LibraryActivity.class)));

        btnVoiceAsk.setOnClickListener(v -> {
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ms-MY");
            intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Sila bercakap sekarang...");
            try {
                voiceLauncher.launch(intent);
            } catch (Exception e) {
                Toast.makeText(this, "Fungsi suara tidak disokong.", Toast.LENGTH_SHORT).show();
            }
        });

        btnDisasterCenter.setOnClickListener(v -> openDisasterCenter());
    }

    // ==========================================
    // NEW: THE "REAL-TIME SYNC" HACKATHON LISTENER
    // ==========================================
    private void listenForNewDisasterReports() {
        // Only listen for reports that are uploaded AFTER this phone opens the app
        db.collection("DisasterReports")
                .whereGreaterThan("timestamp", appStartTime)
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null || snapshots == null) return;

                    for (DocumentChange dc : snapshots.getDocumentChanges()) {
                        // When a brand new document is added to the cloud
                        if (dc.getType() == DocumentChange.Type.ADDED) {
                            String title = dc.getDocument().getString("title");
                            if (title == null) title = "Bencana Dikesan";

                            // Trigger visual notification
                            sendDisasterNotification("🚨 AMARAN KOMUNITI!", "Laporan Baru: " + title);

                            // Trigger voice alert
                            if (textToSpeech != null) {
                                textToSpeech.speak("Amaran Komuniti Baru Dikesan. " + title, TextToSpeech.QUEUE_FLUSH, null, null);
                            }
                        }
                    }
                });
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "Laporan Komuniti Penting", NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("Notifikasi Kecemasan Bencana");
            channel.enableVibration(true);
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }

    private void sendDisasterNotification(String title, String message) {
        Intent intent = new Intent(this, DisasterActivity.class);
        intent.putExtra("RAIN_VAL", globalRainProb);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            notificationManager.notify((int) System.currentTimeMillis(), builder.build());
        }
    }

    private void openDisasterCenter() {
        Intent intent = new Intent(this, DisasterActivity.class);
        intent.putExtra("RAIN_VAL", globalRainProb);
        startActivity(intent);
    }

    @Override
    protected void onDestroy() {
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
        super.onDestroy();
    }

    private void checkPermissionAndGetLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_CODE);
        } else {
            fetchDeviceLocation();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_CODE && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            fetchDeviceLocation();
        } else {
            fetchLiveWeatherData(1.5533, 110.3592);
        }
    }

    private void fetchDeviceLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.getLastLocation().addOnSuccessListener(this, location -> {
                TextView tvLocation = findViewById(R.id.tvLocation);
                SharedPreferences prefs = getSharedPreferences("OfflineWeather", MODE_PRIVATE);

                if (location != null) {
                    double lat = location.getLatitude();
                    double lon = location.getLongitude();
                    try {
                        Geocoder geocoder = new Geocoder(MainActivity.this, Locale.getDefault());
                        List<Address> addresses = geocoder.getFromLocation(lat, lon, 1);
                        if (addresses != null && !addresses.isEmpty()) {
                            String city = addresses.get(0).getLocality();
                            String locationText = (city != null) ? city : "Sarawak Region";
                            tvLocation.setText(locationText);
                            prefs.edit().putString("savedCity", locationText).apply();
                        }
                    } catch (IOException e) {
                        tvLocation.setText(prefs.getString("savedCity", "Sarawak Region"));
                    }
                    fetchLiveWeatherData(lat, lon);
                } else {
                    tvLocation.setText(prefs.getString("savedCity", "Sarawak Region"));
                    fetchLiveWeatherData(1.5533, 110.3592);
                }
            });
        }
    }

    private void fetchLiveWeatherData(double lat, double lon) {
        String url = "https://api.open-meteo.com/v1/forecast?latitude=" + lat + "&longitude=" + lon + "&current=temperature_2m,precipitation&daily=precipitation_sum&timezone=auto";
        RequestQueue queue = Volley.newRequestQueue(this);

        JsonObjectRequest jsonObjectRequest = new JsonObjectRequest(Request.Method.GET, url, null, response -> {
            try {
                JSONObject current = response.getJSONObject("current");
                int temp = (int) Math.round(current.getDouble("temperature_2m"));

                double currentRain = current.optDouble("precipitation", 0.0);
                globalRainProb = (int) Math.min(currentRain * 10, 100);

                if (globalRainProb > 80) {
                    sendDisasterNotification("AMARAN BENCANA!", "Risiko banjir tinggi dikesan di kawasan anda.");
                    if(textToSpeech != null){
                        textToSpeech.speak("Amaran banjir dikesan. Sila bersedia dan rujuk Pusat Bencana.", TextToSpeech.QUEUE_FLUSH, null, null);
                    }
                }

                JSONObject daily = response.getJSONObject("daily");
                JSONArray rainArray = daily.getJSONArray("precipitation_sum");
                int[] weeklyRain = new int[5];
                for (int i = 0; i < 5; i++) {
                    weeklyRain[i] = (int) Math.round(rainArray.getDouble(i));
                }

                SimpleDateFormat sdf = new SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault());
                String currentTime = "Dikemas kini: " + sdf.format(new Date());

                TextView tvLastUpdated = findViewById(R.id.tvLastUpdated);
                if (tvLastUpdated != null) {
                    tvLastUpdated.setText(currentTime);
                }

                saveToOfflinePrefs(temp, globalRainProb, rainArray.toString(), currentTime);
                updateDashboard(temp, globalRainProb, weeklyRain);

            } catch (JSONException e) { loadOfflineData(); }
        }, error -> loadOfflineData());

        jsonObjectRequest.setRetryPolicy(new DefaultRetryPolicy(
                15000,
                DefaultRetryPolicy.DEFAULT_MAX_RETRIES,
                DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));

        queue.add(jsonObjectRequest);
    }

    private void saveToOfflinePrefs(int temp, int rain, String rainString, String time) {
        SharedPreferences.Editor editor = getSharedPreferences("OfflineWeather", MODE_PRIVATE).edit();
        editor.putInt("savedTemp", temp);
        editor.putInt("savedRainProb", rain);
        editor.putString("savedWeeklyRain", rainString);
        editor.putString("savedTime", time);
        editor.apply();
    }

    private void loadOfflineData() {
        SharedPreferences prefs = getSharedPreferences("OfflineWeather", MODE_PRIVATE);
        String offlineTime = prefs.getString("savedTime", "Kemaskini: Tiada");
        TextView tvLastUpdated = findViewById(R.id.tvLastUpdated);
        if (tvLastUpdated != null) {
            tvLastUpdated.setText(offlineTime + " (Luar Talian)");
        }

        int[] offlineWeeklyRain = new int[5];
        try {
            JSONArray savedRainArray = new JSONArray(prefs.getString("savedWeeklyRain", "[0, 0, 0, 0, 0]"));
            for (int i = 0; i < 5; i++) offlineWeeklyRain[i] = (int) Math.round(savedRainArray.getDouble(i));
        } catch (JSONException e) { e.printStackTrace(); }

        updateDashboard(prefs.getInt("savedTemp", 28), prefs.getInt("savedRainProb", 20), offlineWeeklyRain);
    }

    private void updateDashboard(int temp, int rainProb, int[] rainfall) {
        ((TextView)findViewById(R.id.tvTemp)).setText(temp + "°C ☁️");
        ((TextView)findViewById(R.id.tvRainProb)).setText("🌧️ " + rainProb + "%");

        findViewById(R.id.cardAlertRed).setVisibility(rainProb > 60 ? View.VISIBLE : View.GONE);
        findViewById(R.id.cardAlertYellow).setVisibility(rainProb > 60 ? View.GONE : View.VISIBLE);

        int[] viewIds = {R.id.barMon, R.id.barTue, R.id.barWed, R.id.barThu, R.id.barFri};
        int[] textIds = {R.id.tvMonRain, R.id.tvTueRain, R.id.tvWedRain, R.id.tvThuRain, R.id.tvFriRain};

        for (int i = 0; i < 5; i++) {
            TextView tvRain = findViewById(textIds[i]);
            if (tvRain != null) tvRain.setText(String.valueOf(rainfall[i]));

            View bar = findViewById(viewIds[i]);
            if (bar != null) {
                ViewGroup.LayoutParams params = bar.getLayoutParams();
                params.height = Math.max(10, rainfall[i] * 3);
                bar.setLayoutParams(params);
                bar.setBackgroundColor(ContextCompat.getColor(this, rainfall[i] >= 40 ? R.color.alert_red_text : R.color.jungle_green));
            }
        }
    }

    private void askGemini(String prompt, final GeminiCallback callback) {
        String apiKey = BuildConfig.GEMINI_API_KEY;

        String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3-flash-preview:generateContent?key=" + apiKey;

        try {
            JSONObject jsonBody = new JSONObject();
            JSONArray contents = new JSONArray();
            JSONObject contentObj = new JSONObject();
            JSONArray parts = new JSONArray();
            JSONObject partObj = new JSONObject();

            partObj.put("text", prompt);
            parts.put(partObj);
            contentObj.put("parts", parts);
            contents.put(contentObj);
            jsonBody.put("contents", contents);

            JsonObjectRequest request = new JsonObjectRequest(Request.Method.POST, url, jsonBody,
                    response -> {
                        try {
                            String generatedText = response.getJSONArray("candidates")
                                    .getJSONObject(0).getJSONObject("content")
                                    .getJSONArray("parts").getJSONObject(0)
                                    .getString("text");
                            generatedText = generatedText.replace("**", "");
                            callback.onSuccess(generatedText);
                        } catch (JSONException e) {
                            callback.onError("Ralat memproses jawapan AI.");
                        }
                    },
                    error -> {
                        String realError = "Ralat Tidak Diketahui";
                        if (error instanceof com.android.volley.TimeoutError) {
                            realError = "Timeout: Pelayan lambat membalas.";
                        } else if (error instanceof com.android.volley.NoConnectionError) {
                            realError = "Tiada Internet: Semak WiFi/Data.";
                        }
                        callback.onError(realError);
                    }
            );

            request.setRetryPolicy(new DefaultRetryPolicy(
                    20000,
                    DefaultRetryPolicy.DEFAULT_MAX_RETRIES,
                    DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));

            Volley.newRequestQueue(this).add(request);

        } catch (JSONException e) {
            callback.onError("Ralat sistem.");
        }
    }

    interface GeminiCallback {
        void onSuccess(String result);
        void onError(String errorMessage);
    }
}