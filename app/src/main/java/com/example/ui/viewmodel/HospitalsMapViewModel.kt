package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.UiState
import com.example.data.model.HealthcarePlace
import com.example.data.model.HealthcarePlaceType
import com.example.data.remote.NearbyHealthcareService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HospitalsMapViewModel(
    private val service: NearbyHealthcareService = NearbyHealthcareService()
) : ViewModel() {

    private val _centerLatLon = MutableStateFlow(37.7749 to -122.4194)
    val centerLatLon: StateFlow<Pair<Double, Double>> = _centerLatLon.asStateFlow()

    private val _selectedFilter = MutableStateFlow(HealthcarePlaceType.ALL)
    val selectedFilter: StateFlow<HealthcarePlaceType> = _selectedFilter.asStateFlow()

    private val _placesState = MutableStateFlow<UiState<List<HealthcarePlace>>>(UiState.Loading)
    val placesState: StateFlow<UiState<List<HealthcarePlace>>> = _placesState.asStateFlow()

    private val _locationLabel = MutableStateFlow("San Francisco, CA (Tap GPS or search city)")
    val locationLabel: StateFlow<String> = _locationLabel.asStateFlow()

    init {
        refreshNearby()
    }

    fun updateCoordinates(lat: Double, lon: Double, label: String) {
        _centerLatLon.value = lat to lon
        _locationLabel.value = label
        refreshNearby()
    }

    fun setFilter(type: HealthcarePlaceType) {
        _selectedFilter.value = type
        refreshNearby()
    }

    fun searchArea(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            _placesState.value = UiState.Loading
            try {
                val coords = service.geocodeCityOrAddress(trimmed)
                if (coords != null) {
                    _centerLatLon.value = coords
                    _locationLabel.value = trimmed
                    val places = service.fetchNearbyPlaces(
                        latitude = coords.first,
                        longitude = coords.second,
                        filterType = _selectedFilter.value
                    )
                    _placesState.value = UiState.Success(places)
                } else {
                    _placesState.value = UiState.Error("Could not find \"$trimmed\". Try a city or ZIP code.")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Area search failed", e)
                _placesState.value = UiState.Error(e.localizedMessage ?: "Failed to search nearby healthcare centers.")
            }
        }
    }

    fun refreshNearby() {
        val (lat, lon) = _centerLatLon.value
        val filter = _selectedFilter.value
        viewModelScope.launch {
            _placesState.value = UiState.Loading
            try {
                val places = service.fetchNearbyPlaces(
                    latitude = lat,
                    longitude = lon,
                    filterType = filter
                )
                _placesState.value = UiState.Success(places)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Nearby fetch failed", e)
                _placesState.value = UiState.Error(
                    e.localizedMessage ?: "Unable to load nearby hospitals. Tap Retry to try again."
                )
            }
        }
    }

    private companion object {
        const val TAG = "HospitalsMapVM"
    }
}
