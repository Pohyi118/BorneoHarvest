package com.example.borneoharvest;
    public class FarmerProfile {
        public String cropType;    // Must match "targetCrop" in Subsidies
        public String district;    // e.g., "Miri"
        public double farmSize;    // In hectares
        public String fcmToken;    // For proactive matching alerts

        public FarmerProfile() {} // Required for Firestore

        public FarmerProfile(String cropType, String district, double farmSize, String fcmToken) {
            this.cropType = cropType;
            this.district = district;
            this.farmSize = farmSize;
            this.fcmToken = fcmToken;
        }
    }

