package com.example.data.model

enum class HealthcarePlaceType(val label: String, val osmAmenity: String) {
    ALL("All Care", "hospital|clinic|doctors|pharmacy"),
    HOSPITAL("Hospitals", "hospital"),
    CLINIC("Clinics", "clinic|doctors"),
    PHARMACY("Pharmacies", "pharmacy")
}

data class HealthcarePlace(
    val id: String,
    val name: String,
    val type: HealthcarePlaceType,
    val address: String,
    val phone: String,
    val openingHours: String,
    val latitude: Double,
    val longitude: Double,
    val distanceKm: Double
)
