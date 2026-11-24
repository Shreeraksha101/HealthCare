package com.example.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
data object DashboardRoute

@Serializable
data object AppointmentsRoute

@Serializable
data object MedicinesRoute

@Serializable
data object AiAssistantRoute

@Serializable
data object DoctorsRoute

@Serializable
data class DoctorDetailRoute(val doctorId: String)

@Serializable
data object ProviderAvailabilityRoute

@Serializable
data object HospitalsMapRoute

@Serializable
data object ReportsRoute

@Serializable
data object ProfileRoute

@Serializable
data class VideoCallRoute(val roomId: String)
