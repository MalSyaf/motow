package com.example.motow.vehicles;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.motow.databinding.ActivityManageVehicleBinding;
import com.example.motow.rider.RiderManageActivity;
import com.example.motow.tower.TowerManageActivity;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.HashMap;

public class ManageVehicleActivity extends AppCompatActivity implements VehicleListener{

    private ActivityManageVehicleBinding binding;

    //Firebase
    private FirebaseFirestore fStore;
    private String userId;

    // Recycler view
    ArrayList<Vehicle> vehicleArrayList;
    VehicleAdapter vehicleAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityManageVehicleBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Firebase
        FirebaseAuth fAuth = FirebaseAuth.getInstance();
        fStore = FirebaseFirestore.getInstance();
        userId = fAuth.getCurrentUser().getUid();

        setListeners();
        setUpRecyclerView();
        eventChangeListener();
    }

    private void setListeners() {
        binding.backBtn.setOnClickListener(view -> {
            DocumentReference df = fStore.collection("Users").document(userId);
            // extract the data from the document
            df.get().addOnSuccessListener(new OnSuccessListener<DocumentSnapshot>() {
                @Override
                public void onSuccess(DocumentSnapshot documentSnapshot) {
                    // identify the user access level
                    if (documentSnapshot.getString("isRider") != null) {
                        // user is a rider
                        Intent intent = new Intent(getApplicationContext(), RiderManageActivity.class);
                        startActivity(intent);
                        finish();
                    }
                    if (documentSnapshot.getString("isTower") != null) {
                        // user is a rider
                        Intent intent = new Intent(getApplicationContext(), TowerManageActivity.class);
                        startActivity(intent);
                        finish();
                    }
                }
            });
        });
        binding.registerVehicle.setOnClickListener(view -> {
            startActivity(new Intent(getApplicationContext(), RegisterVehicleActivity.class));
            finish();
        });
    }

    private void setUpRecyclerView() {
        binding.recyclerView.setHasFixedSize(true);
        binding.recyclerView.setLayoutManager(new LinearLayoutManager(this));
        vehicleArrayList = new ArrayList<Vehicle>();
        vehicleAdapter = new VehicleAdapter(ManageVehicleActivity.this, vehicleArrayList, this);
        binding.recyclerView.setAdapter(vehicleAdapter);
    }

    private void eventChangeListener() {
        loading(true);
        fStore.collection("Vehicles").whereEqualTo("ownerId", userId)
                .addSnapshotListener((value, error) -> {
                    if(value.isEmpty()) {
                        binding.errorMessage.setVisibility(View.VISIBLE);
                    }
                    if(error != null) {
                        return;
                    }
                    for(DocumentChange dc : value.getDocumentChanges()) {
                        if(dc.getType() == DocumentChange.Type.ADDED) {
                            vehicleArrayList.add(dc.getDocument().toObject(Vehicle.class));
                            binding.errorMessage.setVisibility(View.GONE);
                        }
                    }
                    vehicleAdapter.notifyDataSetChanged();
                    loading(false);
                });
    }

    private void loading(Boolean isLoading) {
        if(isLoading) {
            binding.progressBar.setVisibility(View.VISIBLE);
        } else {
            binding.progressBar.setVisibility(View.GONE);
        }
    }

    @Override
    public void onVehicleClicked(Vehicle vehicle) {
        binding.makeDefault.setOnClickListener(view -> {
            HashMap<String, Object> currentVehicle = new HashMap<>();
            currentVehicle.put("currentVehicle", vehicle.vehicleId);
            fStore.collection("Users")
                    .document(userId)
                    .update(currentVehicle)
                    .addOnSuccessListener(unused -> {
                        Toast.makeText(ManageVehicleActivity.this, "Default vehicle has been changed", Toast.LENGTH_SHORT).show();
                    });
        });
        binding.deleteButton.setOnClickListener(view -> {
            fStore.collection("Users")
                    .document(userId)
                    .get()
                    .addOnSuccessListener(documentSnapshot -> {
                        if(vehicle.vehicleId != null) {
                            if(!documentSnapshot.getString("currentVehicle").equals(vehicle.vehicleId)) {
                                AlertDialog.Builder alert = new AlertDialog.Builder(this);
                                alert.setTitle("Are you sure?");
                                alert.setMessage("Do you want to delete this vehicle?");
                                alert.setPositiveButton("YES", (dialogInterface, i) -> {
                                    fStore.collection("Vehicles")
                                            .document(vehicle.vehicleId)
                                            .delete();
                                    Intent intent = new Intent(getApplicationContext(), ManageVehicleActivity.class);
                                    startActivity(intent);
                                    finish();
                                });
                                alert.setNegativeButton("NO", (dialogInterface, i) -> {
                                    //
                                });
                                alert.create().show();
                            } else {
                                AlertDialog.Builder alert = new AlertDialog.Builder(this);
                                alert.setTitle("Default Vehicle");
                                alert.setMessage("You cannot delete a default vehicle");
                                alert.setNeutralButton("OK", (dialogInterface, i) -> {
                                   //
                                });
                                alert.create().show();
                            }
                        }
                    });
            });
    }
}