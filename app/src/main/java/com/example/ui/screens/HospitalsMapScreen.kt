package com.example.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Directions
import androidx.compose.material.icons.rounded.LocalHospital
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.UiState
import com.example.data.model.HealthcarePlace
import com.example.data.model.HealthcarePlaceType
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.ErrorStateCard
import com.example.ui.components.LoadingStateBox
import com.example.ui.components.StatusPill
import com.example.ui.viewmodel.HospitalsMapViewModel
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.util.Locale

@Composable
fun HospitalsMapScreen(
    viewModel: HospitalsMapViewModel
) {
    val context = LocalContext.current
    val centerLatLon by viewModel.centerLatLon.collectAsStateWithLifecycle()
    val selectedFilter by viewModel.selectedFilter.collectAsStateWithLifecycle()
    val placesState by viewModel.placesState.collectAsStateWithLifecycle()
    val locationLabel by viewModel.locationLabel.collectAsStateWithLifecycle()

    var searchQuery by remember { mutableStateOf("") }
    var selectedPlace by remember { mutableStateOf<HealthcarePlace?>(null) }

    val fusedLocationClient = remember(context) {
        LocationServices.getFusedLocationProviderClient(context)
    }

    @SuppressLint("MissingPermission")
    fun fetchCurrentGpsLocation() {
        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
            .addOnSuccessListener { loc ->
                if (loc != null) {
                    viewModel.updateCoordinates(loc.latitude, loc.longitude, "Your Current GPS Location")
                }
            }
    }

    val locationPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val granted = perms[Manifest.permission.ACCESS_COARSE_LOCATION] == true ||
            perms[Manifest.permission.ACCESS_FINE_LOCATION] == true
        if (granted) {
            fetchCurrentGpsLocation()
        }
    }

    LaunchedEffect(Unit) {
        Configuration.getInstance().userAgentValue = context.packageName
        val hasPerm = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (hasPerm) {
            fetchCurrentGpsLocation()
        } else {
            locationPermLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION
                )
            )
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 900.dp)
                .fillMaxSize()
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Nearby Hospitals & Care", style = MaterialTheme.typography.headlineSmall)
                        Text(
                            locationLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    FilledTonalIconButton(
                        onClick = {
                            val hasPerm = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            ) == PackageManager.PERMISSION_GRANTED
                            if (hasPerm) {
                                fetchCurrentGpsLocation()
                            } else {
                                locationPermLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_COARSE_LOCATION,
                                        Manifest.permission.ACCESS_FINE_LOCATION
                                    )
                                )
                            }
                        },
                        modifier = Modifier.testTag("use_gps_button")
                    ) {
                        Icon(Icons.Rounded.MyLocation, contentDescription = "Center on my GPS location")
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search city, area, or ZIP...") },
                        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { viewModel.searchArea(searchQuery) }),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("map_search_input")
                    )
                    Button(
                        onClick = { viewModel.searchArea(searchQuery) },
                        modifier = Modifier.height(52.dp)
                    ) {
                        Text("Find")
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HealthcarePlaceType.entries.forEach { filter ->
                        FilterChip(
                            selected = selectedFilter == filter,
                            onClick = { viewModel.setFilter(filter) },
                            label = { Text(filter.label) }
                        )
                    }
                }
            }

            // Interactive OpenStreetMap View
            val places = (placesState as? UiState.Success)?.data.orEmpty()
            OsmHealthcareMap(
                centerLat = centerLatLon.first,
                centerLon = centerLatLon.second,
                places = places,
                selectedPlace = selectedPlace,
                onMarkerSelected = { selectedPlace = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(230.dp)
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(20.dp))
            )

            Spacer(modifier = Modifier.height(8.dp))

            when (val state = placesState) {
                is UiState.Loading -> LoadingStateBox()
                is UiState.Error -> Box(modifier = Modifier.padding(16.dp)) {
                    ErrorStateCard(message = state.message, onRetry = { viewModel.refreshNearby() })
                }
                is UiState.Success -> {
                    if (state.data.isEmpty()) {
                        Box(modifier = Modifier.padding(16.dp)) {
                            EmptyStateCard(
                                icon = Icons.Rounded.LocalHospital,
                                title = "No care facilities found nearby",
                                subtitle = "Try switching filter to All Care or searching a larger city center.",
                                actionLabel = "Retry",
                                onActionClick = { viewModel.refreshNearby() }
                            )
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            items(state.data, key = { it.id }) { place ->
                                HealthcarePlaceCard(
                                    place = place,
                                    isSelected = selectedPlace?.id == place.id,
                                    onSelect = { selectedPlace = place },
                                    onCall = {
                                        if (place.phone != "Not listed") {
                                            context.startActivity(
                                                Intent(Intent.ACTION_DIAL, Uri.parse("tel:${place.phone}"))
                                            )
                                        }
                                    },
                                    onDirections = {
                                        val uri = Uri.parse(
                                            "geo:${place.latitude},${place.longitude}?q=${Uri.encode(place.name)}"
                                        )
                                        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OsmHealthcareMap(
    centerLat: Double,
    centerLon: Double,
    places: List<HealthcarePlace>,
    selectedPlace: HealthcarePlace?,
    onMarkerSelected: (HealthcarePlace) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(13.5)
        }
    }

    DisposableEffect(mapView) {
        mapView.onResume()
        onDispose {
            mapView.onPause()
            mapView.onDetach()
        }
    }

    AndroidView(
        factory = { mapView },
        update = { view ->
            val targetPoint = if (selectedPlace != null) {
                GeoPoint(selectedPlace.latitude, selectedPlace.longitude)
            } else {
                GeoPoint(centerLat, centerLon)
            }
            view.controller.animateTo(targetPoint)
            view.overlays.clear()

            val userMarker = Marker(view).apply {
                position = GeoPoint(centerLat, centerLon)
                title = "Search Center"
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            }
            view.overlays.add(userMarker)

            places.take(25).forEach { place ->
                val marker = Marker(view).apply {
                    position = GeoPoint(place.latitude, place.longitude)
                    title = place.name
                    snippet = "${place.type.label} • ${String.format(Locale.US, "%.1f km", place.distanceKm)}"
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    setOnMarkerClickListener { m, _ ->
                        m.showInfoWindow()
                        onMarkerSelected(place)
                        true
                    }
                }
                view.overlays.add(marker)
            }
            view.invalidate()
        },
        modifier = modifier
    )
}

@Composable
private fun HealthcarePlaceCard(
    place: HealthcarePlace,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onCall: () -> Unit,
    onDirections: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
            else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(place.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        place.address,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                StatusPill(
                    text = String.format(Locale.US, "%.1f km", place.distanceKm),
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.primary
                )
            }
            Text(
                text = "${place.type.label} • Hours: ${place.openingHours} • Tel: ${place.phone}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onDirections,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Rounded.Directions, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Directions")
                }
                if (place.phone != "Not listed") {
                    OutlinedButton(
                        onClick = onCall,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.Call, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Call")
                    }
                }
            }
        }
    }
}
