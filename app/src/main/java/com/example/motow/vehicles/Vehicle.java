package com.example.motow.vehicles;

public class Vehicle {

    public String  vehicleId, plateNumber, brand, model, color;

    public Vehicle(String vehicleId, String plateNumber, String brand, String model, String color) {
        this.vehicleId = vehicleId;
        this.plateNumber = plateNumber;
        this.brand = brand;
        this.model = model;
        this.color = color;
    }

    public Vehicle(){
        //
    }

    public String getPlateNumber() {
        return plateNumber;
    }

    public void setPlateNumber(String plateNumber) {
        this.plateNumber = plateNumber;
    }

    public String getBrand() {
        return brand;
    }

    public void setBrand(String brand) {
        this.brand = brand;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }
}
