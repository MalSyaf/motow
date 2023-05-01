package com.example.motow.tower;

import android.Manifest;
import android.app.ActivityManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Base64;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import com.example.motow.R;
import com.example.motow.databinding.ActivityTowerBinding;
import com.example.motow.utilities.ForegroundService;
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.HashMap;
import java.util.Map;

public class TowerActivity extends FragmentActivity implements OnMapReadyCallback {

    private ActivityTowerBinding binding;

    // Google Map
    private GoogleMap mMap;
    private Boolean check = false;

    // Firebase
    private FirebaseFirestore fStore;
    private String userId;

    private String riderId, currentProcessId, riderCurrentVehicle;
    private static final int REQUEST_CALL = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityTowerBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        if (!foreGroundServiceRunning()) {
            Intent serviceIntent = new Intent(this, ForegroundService.class);
            startForegroundService(serviceIntent);
        }

        // Firebase
        FirebaseAuth fAuth = FirebaseAuth.getInstance();
        fStore = FirebaseFirestore.getInstance();
        userId = fAuth.getUid();

        supportMapFragment();
        loadUserDetails();
        deleteRequests();
        createNotificationChannel();
        setListeners();
    }

    public boolean foreGroundServiceRunning() {
        ActivityManager activityManager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : activityManager.getRunningServices(Integer.MAX_VALUE)) {
            if (ForegroundService.class.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    private void supportMapFragment() {
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
        ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION}, PackageManager.PERMISSION_GRANTED);
        ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.ACCESS_COARSE_LOCATION}, PackageManager.PERMISSION_GRANTED);
        LocationManager locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION}, 100);
        }
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 500, 0, new LocationListener() {
            @Override
            public void onLocationChanged(@NonNull Location location) {
                if (check) {
                    double latitude = location.getLatitude();
                    double longitude = location.getLongitude();

                    // Update current coordinate in database
                    updateCurrentLocation(latitude, longitude);
                }
            }
            @Override
            public void onProviderEnabled(@NonNull String provider) {
                //
            }
            @Override
            public void onProviderDisabled(@NonNull String provider) {
                //
            }
            @Override
            public void onStatusChanged(String provider, int status, Bundle extras) {
                //
            }
        });
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        check = true;

        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        mMap.setMyLocationEnabled(true);
        fStore.collection("Users")
                .document(userId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    LatLng firstCamera = new LatLng(documentSnapshot.getDouble("latitude"), documentSnapshot.getDouble("longitude"));
                    CameraUpdate cameraUpdate = CameraUpdateFactory.newLatLngZoom(firstCamera, 15);
                    mMap.moveCamera(cameraUpdate);
                });
    }

    private void loadUserDetails() {
        fStore.collection("Users")
                .document(userId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    binding.userName.setText("Hi, " + documentSnapshot.getString("name") + "!");
                    byte[] bytes = Base64.decode(documentSnapshot.getString("image"), Base64.DEFAULT);
                    Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                    binding.welcomePfp.setImageBitmap(bitmap);
                });
    }

    private void deleteRequests() {
        fStore.collection("Processes")
                .whereEqualTo("towerId", userId)
                .whereEqualTo("processStatus", "requesting")
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        for (QueryDocumentSnapshot document : task.getResult()) {
                            fStore.collection("Processes")
                                    .document(document.getId())
                                    .delete();
                        }
                    }
                });
    }

    private void createNotificationChannel() {
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel("My Notifications","My Notifications", NotificationManager.IMPORTANCE_DEFAULT);
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }
    }

    private void setListeners() {
        // Navbar listeners
        binding.manageBtn.setOnClickListener(v ->
            fStore.collection("Users")
                    .document(userId)
                    .get()
                    .addOnSuccessListener(documentSnapshot -> {
                        if(documentSnapshot.getString("status").equals("onduty")) {
                            AlertDialog.Builder alert = new AlertDialog.Builder(this);
                            alert.setTitle("You Are On Duty!");
                            alert.setMessage("You are not allowed to manage account while on duty");
                            alert.create().show();
                        } else if (documentSnapshot.getString("status").equals("online")) {
                            AlertDialog.Builder alert = new AlertDialog.Builder(this);
                            alert.setTitle("You Are Online!");
                            alert.setMessage("You are not allowed to manage account while online");
                            alert.create().show();
                        } else {
                            startActivity(new Intent(getApplicationContext(), TowerManageActivity.class));
                            finish();
                        }
                    }));

        // Chat listeners
        binding.chatBtn.setOnClickListener(view -> {
            binding.chatLayout.setVisibility(View.VISIBLE);
            loadReceiverName();
        });
        binding.chatBackBtn.setOnClickListener(view ->
            binding.chatLayout.setVisibility(View.INVISIBLE));
        binding.callBtn.setOnClickListener(view ->
            makePhoneCall());

        // Button listeners
        binding.offlineBtn.setOnClickListener(view -> {
            changeStatusToOnline();
            getAssistance(userId);
        });
        binding.onlineBtn.setOnClickListener(view -> {
            binding.offlineBtn.setVisibility(View.VISIBLE);
            binding.onlineBtn.setVisibility(View.GONE);
            changeStatusToOffline();
        });
        binding.doneBtn.setOnClickListener(view -> {
            binding.doneBtn.setVisibility(View.GONE);
            binding.onlineBtn.setVisibility(View.VISIBLE);
            binding.acceptBtn.setVisibility(View.VISIBLE);
            binding.rejectBtn.setVisibility(View.VISIBLE);
            binding.riderContainer.setVisibility(View.GONE);
            binding.textStatus.setText("Assistance Needed!");
            deleteRequests();
        });

        binding.okBtn.setOnClickListener(view ->
            binding.riderContainer.setVisibility(View.GONE));
        binding.riderBar.setOnClickListener(view -> {
            binding.riderContainer.setVisibility(View.VISIBLE);
            binding.okBtn.setVisibility(View.VISIBLE);
            binding.acceptBtn.setVisibility(View.GONE);
            binding.rejectBtn.setVisibility(View.GONE);
            fStore.collection("Users")
                    .document(riderId)
                    .get()
                    .addOnSuccessListener(documentSnapshot -> {
                        LatLng firstCamera = new LatLng(documentSnapshot.getDouble("latitude"), documentSnapshot.getDouble("longitude"));
                        CameraUpdate cameraUpdate = CameraUpdateFactory.newLatLngZoom(firstCamera, 15);
                        mMap.animateCamera(cameraUpdate);
                    });
        });
    }

    private void updateCurrentLocation(double latitude, double longitude) {
        Map<String, Object> infoUpdate = new HashMap<>();
        infoUpdate.put("latitude", latitude);
        infoUpdate.put("longitude", longitude);

        fStore.collection("Users")
                .document(userId)
                .update(infoUpdate)
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void unused) {
                        //
                    }
                }).addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        //
                    }
                });
    }

    private void loadReceiverName() {
        fStore.collection("Users")
                .document(riderId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                   binding.chatName.setText(documentSnapshot.getString("name"));
                });
    }

    private void makePhoneCall() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.CALL_PHONE}, REQUEST_CALL);
        } else {
            fStore.collection("Users")
                    .document(riderId)
                    .get()
                    .addOnSuccessListener(documentSnapshot -> {
                        String contact = documentSnapshot.getString("contact");
                        String dial = "tel:" + contact;
                        startActivity(new Intent(Intent.ACTION_CALL, Uri.parse(dial)));
                    });
        }
    }

    private void getAssistance(String userId) {
        fStore.collection("Processes")
                .addSnapshotListener(new EventListener<QuerySnapshot>() {
                    @Override
                    public void onEvent(@Nullable QuerySnapshot value, @Nullable FirebaseFirestoreException error) {
                        for (QueryDocumentSnapshot documentSnapshot : value) {
                            riderId = null;
                            riderId = documentSnapshot.getString("riderId");

                            binding.chatBtn.setVisibility(View.VISIBLE);

                            fStore.collection("Processes")
                                    .whereEqualTo("riderId", riderId)
                                    .whereEqualTo("towerId", userId)
                                    .whereEqualTo("processStatus", "paid")
                                    .whereEqualTo("processId", currentProcessId)
                                    .get()
                                    .addOnCompleteListener(task -> {
                                        if (task.isSuccessful()) {
                                            for (QueryDocumentSnapshot document : task.getResult()) {
                                                binding.completeBtn.setVisibility(View.VISIBLE);
                                                binding.waitingText.setVisibility(View.GONE);
                                            }
                                        }
                                    });

                            // Find request
                            fStore.collection("Processes")
                                    .whereEqualTo("towerId", userId)
                                    .whereEqualTo("riderId", riderId)
                                    .whereEqualTo("processStatus", "requesting")
                                    .get()
                                    .addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
                                        @Override
                                        public void onComplete(@NonNull Task<QuerySnapshot> task) {
                                            if (task.isSuccessful()) {
                                                for (QueryDocumentSnapshot document : task.getResult()) {
                                                    binding.riderContainer.setVisibility(View.VISIBLE);
                                                    binding.onlineBtn.setVisibility(View.GONE);

                                                    currentProcessId = document.getId();

                                                    fStore.collection("Users")
                                                            .document(riderId)
                                                            .get()
                                                            .addOnSuccessListener(documentSnapshot -> {
                                                                binding.riderName.setText(documentSnapshot.getString("name"));
                                                                byte[] bytes = Base64.decode(documentSnapshot.getString("image"), Base64.DEFAULT);
                                                                Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                                                                binding.riderPfp.setImageBitmap(bitmap);
                                                                binding.riderBarPfp.setImageBitmap(bitmap);

                                                                riderCurrentVehicle = documentSnapshot.getString("currentVehicle");
                                                                fStore.collection("Vehicles")
                                                                        .document(riderCurrentVehicle)
                                                                        .get()
                                                                        .addOnSuccessListener(documentSnapshot1 -> {
                                                                            binding.riderVehicle.setText(documentSnapshot1.getString("brand") + " " + documentSnapshot1.getString("model") + " (" + documentSnapshot1.getString("color") + ")");
                                                                            binding.riderPlate.setText(documentSnapshot1.getString("plateNumber"));
                                                                        });

                                                                NotificationCompat.Builder builder = new NotificationCompat.Builder(TowerActivity.this, "notify");
                                                                builder.setContentTitle("Assistance Needed!");
                                                                builder.setContentText("Respond to Rider Now");
                                                                builder.setSmallIcon(R.drawable.logo);
                                                                builder.setAutoCancel(true);

                                                                NotificationManagerCompat managerCompat = NotificationManagerCompat.from(TowerActivity.this);
                                                                if (ActivityCompat.checkSelfPermission(TowerActivity.this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                                                                    // TODO: Consider calling
                                                                    //    ActivityCompat#requestPermissions
                                                                    // here to request the missing permissions, and then overriding
                                                                    //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                                                    //                                          int[] grantResults)
                                                                    // to handle the case where the user grants the permission. See the documentation
                                                                    // for ActivityCompat#requestPermissions for more details.
                                                                    return;
                                                                }
                                                                managerCompat.notify(1, builder.build());
                                                            });

                                                    binding.acceptBtn.setOnClickListener(v -> {
                                                        binding.riderContainer.setVisibility(View.GONE);
                                                        binding.riderBar.setVisibility(View.VISIBLE);
                                                        binding.pickupBtn.setVisibility(View.VISIBLE);
                                                        binding.okBtn.setVisibility(View.VISIBLE);
                                                        binding.acceptBtn.setVisibility(View.GONE);
                                                        binding.rejectBtn.setVisibility(View.GONE);

                                                        fStore.collection("Users")
                                                                .document(riderId)
                                                                .get()
                                                                .addOnSuccessListener(documentSnapshot1 -> {
                                                                    LatLng firstCamera = new LatLng(documentSnapshot.getDouble("latitude"),documentSnapshot.getDouble("longitude"));
                                                                    CameraUpdate cameraUpdate = CameraUpdateFactory.newLatLngZoom(firstCamera, 15);
                                                                    mMap.animateCamera(cameraUpdate);
                                                                });

                                                        HashMap<String, Object> informRider = new HashMap<>();
                                                        informRider.put("towerId", userId);
                                                        informRider.put("riderId", riderId);
                                                        informRider.put("message", "I'm on my way!");
                                                        fStore.collection("Chats")
                                                                .add(informRider);

                                                        HashMap<String, Object> userStatus = new HashMap<>();
                                                        userStatus.put("status", "onduty");
                                                        fStore.collection("Users")
                                                                .document(userId)
                                                                .update(userStatus);

                                                        HashMap<String, Object> updateStatus = new HashMap<>();
                                                        updateStatus.put("processStatus", "ongoing");
                                                        fStore.collection("Processes")
                                                                .document(currentProcessId)
                                                                .update(updateStatus)
                                                                .addOnSuccessListener(unused ->
                                                                        Toast.makeText(TowerActivity.this, "Request has been accepted", Toast.LENGTH_SHORT).show());

                                                        fStore.collection("Vehicles")
                                                                .document(riderCurrentVehicle)
                                                                .get()
                                                                .addOnSuccessListener(documentSnapshot1 -> {
                                                                    binding.riderBarVehicle.setText(documentSnapshot.getString("brand") + " " + documentSnapshot.getString("model") + " (" + documentSnapshot.getString("color") + ")");
                                                                    binding.riderBarPlate.setText(documentSnapshot.getString("plateNumber"));
                                                                });

                                                        fStore.collection("Users")
                                                                .document(riderId)
                                                                .get()
                                                                .addOnSuccessListener(documentSnapshot1 -> {
                                                                    double riderLatitude = documentSnapshot.getDouble("latitude");
                                                                    double riderLongitude = documentSnapshot.getDouble("longitude");
                                                                    LatLng riderLocation = new LatLng(riderLatitude, riderLongitude);

                                                                    mMap.addMarker(new MarkerOptions().position(riderLocation).title(documentSnapshot.getString("fullName")));

                                                                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=" + riderLatitude + "," + riderLongitude));
                                                                    intent.setPackage("com.google.android.apps.maps");
                                                                    if(intent.resolveActivity(getPackageManager()) != null){
                                                                        startActivity(intent);
                                                                    }
                                                                });
                                                    });

                                                    binding.rejectBtn.setOnClickListener(v -> {
                                                        binding.riderContainer.setVisibility(View.GONE);
                                                        binding.offlineBtn.setVisibility(View.VISIBLE);

                                                        HashMap<String, Object> updateStatus = new HashMap<>();
                                                        updateStatus.put("processStatus", "rejected");
                                                        fStore.collection("Processes")
                                                                .document(currentProcessId)
                                                                .update(updateStatus)
                                                                .addOnSuccessListener(unused ->
                                                                        Toast.makeText(TowerActivity.this, "Request has been rejected", Toast.LENGTH_SHORT).show());

                                                        HashMap<String, Object> userStatus = new HashMap<>();
                                                        userStatus.put("status", "offline");
                                                        fStore.collection("Users")
                                                                .document(userId)
                                                                .update(userStatus);
                                                    });

                                                    binding.pickupBtn.setOnClickListener(v -> {
                                                        binding.pickupBtn.setVisibility(View.GONE);

                                                        HashMap<String, Object> updateStatus = new HashMap<>();
                                                        updateStatus.put("processStatus", "towed");
                                                        fStore.collection("Processes")
                                                                .document(currentProcessId)
                                                                .update(updateStatus)
                                                                .addOnSuccessListener(unused -> {
                                                                    binding.waitingText.setVisibility(View.VISIBLE);
                                                                    Toast.makeText(TowerActivity.this, "Vehicle has been towed", Toast.LENGTH_SHORT).show();
                                                                });
                                                    });

                                                    binding.completeBtn.setOnClickListener(v -> {
                                                        binding.riderBar.setVisibility(View.GONE);
                                                        binding.completeBtn.setVisibility(View.GONE);
                                                        binding.textStatus.setText("Thank you for the assistance");
                                                        binding.okBtn.setVisibility(View.GONE);
                                                        binding.doneBtn.setVisibility(View.VISIBLE);

                                                        HashMap<String, Object> userStatus = new HashMap<>();
                                                        userStatus.put("status", "online");
                                                        fStore.collection("Users")
                                                                .document(userId)
                                                                .update(userStatus);

                                                        HashMap<String, Object> updateStatus = new HashMap<>();
                                                        updateStatus.put("processStatus", "completed");
                                                        fStore.collection("Processes")
                                                                .document(currentProcessId)
                                                                .update(updateStatus)
                                                                .addOnSuccessListener(unused ->
                                                                        Toast.makeText(TowerActivity.this, "Process has been completed", Toast.LENGTH_SHORT).show());
                                                    });
                                                }
                                            }
                                        }
                                    });
                        }
                    }
                });
    }

    private void changeStatusToOnline() {
        fStore.collection("Users")
                .document(userId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if(documentSnapshot.getString("companyRegNum") != null & documentSnapshot.getString("currentVehicle") != null & documentSnapshot.getString("providerType") != null){
                        Map<String, Object> infoUpdate = new HashMap<>();
                        infoUpdate.put("status", "online");
                        fStore.collection("Users")
                                .document(userId)
                                .update(infoUpdate)
                                .addOnSuccessListener(unused -> {
                                    binding.offlineBtn.setVisibility(View.INVISIBLE);
                                    binding.onlineBtn.setVisibility(View.VISIBLE);
                                    Toast.makeText(TowerActivity.this, "You are online!", Toast.LENGTH_SHORT).show();
                                });
                    } else {
                        Toast.makeText(TowerActivity.this, "Register company and vehicle", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void changeStatusToOffline() {
        Map<String, Object> infoUpdate = new HashMap<>();
        infoUpdate.put("status", "offline");
        fStore.collection("Users")
                .document(userId)
                .update(infoUpdate)
                .addOnSuccessListener(unused ->
                    Toast.makeText(TowerActivity.this, "You are offline!", Toast.LENGTH_SHORT).show());
    }
}