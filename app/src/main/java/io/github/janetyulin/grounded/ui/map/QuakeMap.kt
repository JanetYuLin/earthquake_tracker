package io.github.janetyulin.grounded.ui.map

import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import android.graphics.RectF
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.janetyulin.grounded.R
import io.github.janetyulin.grounded.domain.model.GeoPoint
import io.github.janetyulin.grounded.domain.model.Severity
import io.github.janetyulin.grounded.ui.common.EarthquakeUiState
import io.github.janetyulin.grounded.ui.theme.colors
import kotlin.math.hypot
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point

/** MapLibre ids for the earthquake GeoJSON source, its circle layer and each feature's properties. */
private object QuakeLayer {
    const val SOURCE_ID = "earthquakes"
    const val LAYER_ID = "earthquake-circles"
    const val PROP_ID = "id"
    const val PROP_MAG = "mag"
    const val PROP_SEVERITY = "severity"

    /** The "you are here" marker, drawn above the earthquakes. */
    const val USER_SOURCE_ID = "user-location"
    const val USER_LAYER_ID = "user-location-circle"
}

private object QuakeMapDefaults {
    const val LIGHT_STYLE_URL = "https://tiles.openfreemap.org/styles/positron"
    const val DARK_STYLE_URL = "https://tiles.openfreemap.org/styles/dark"

    /** Regional view around the user: neighbouring countries and the nearest plate boundaries. */
    const val USER_ZOOM = 4.0
    const val WORLD_ZOOM = 1.0

    /** Minimum touch target from the Material accessibility guidelines. */
    const val TAP_TARGET_DP = 48f
}

/**
 * Keeps one MapLibre [MapView] alive for as long as the nav host, so leaving the Map tab (for
 * another tab or for details) hides the map instead of destroying it. Creating a `MapView` means GL
 * setup plus a style and tile load; reattaching an existing one only recreates its surface, the same
 * path the app takes when it comes back from the background.
 *
 * The view's lifecycle is the host's, capped at CREATED while no [QuakeMap] shows it, so a hidden
 * map renders nothing. Obtain one with [rememberQuakeMapHolder]; holds no app state.
 */
@Stable
class QuakeMapHolder internal constructor(private val context: Context) {

    private var view: MapView? = null
    private var hostState = Lifecycle.State.INITIALIZED
    private var shown = false
    private var stepper: LifecycleStepper? = null
    private var destroyed = false

    internal var map by mutableStateOf<MapLibreMap?>(null)
        private set
    internal var style by mutableStateOf<Style?>(null)

    /** Theme [style] was loaded for, so returning to the tab doesn't reload it. */
    internal var styleIsDark: Boolean? = null
    internal var rendered by mutableStateOf(false)
        private set

    /** Whether the camera already shows the user (or a restored position); then it's the user's to move. */
    internal var centeredOnUser = false

    internal var onEarthquakeClick: (String) -> Unit = {}
    internal var onCameraIdle: (MapCameraUiState) -> Unit = {}

    internal val isCreated: Boolean get() = view != null

    /**
     * The retained view, created on first use (starting at [start]), detached from wherever it was
     * last shown. [start] only matters for the first call.
     */
    internal fun obtainView(start: StartCamera): MapView {
        val existing = view
        if (existing != null) {
            (existing.parent as? ViewGroup)?.removeView(existing)
            return existing
        }
        return MapView(context).also { created ->
            created.onCreate(null)
            created.addOnDidFinishRenderingMapListener { fully -> if (fully) rendered = true }
            created.getMapAsync { loaded ->
                // Place the camera before listening, so the default (0, 0) view never reports idle
                // and overwrites a position restored after rotation.
                loaded.cameraPosition = start.position
                centeredOnUser = start.isUserOrRestored
                loaded.addOnCameraIdleListener {
                    val position = loaded.cameraPosition
                    position.target?.let { onCameraIdle(MapCameraUiState(it.latitude, it.longitude, position.zoom)) }
                }
                loaded.addOnMapClickListener { latLng -> handleTap(created, loaded, latLng) }
                map = loaded
            }
            view = created
            stepper = LifecycleStepper(created::onStart, created::onResume, created::onPause, created::onStop)
            sync()
        }
    }

    internal fun setShown(value: Boolean) {
        shown = value
        sync()
    }

    internal fun setHostState(state: Lifecycle.State) {
        hostState = state
        sync()
    }

    internal fun onLowMemory() {
        view?.onLowMemory()
    }

    internal fun destroy() {
        if (destroyed) return
        shown = false
        sync()
        view?.onDestroy()
        view = null
        destroyed = true
    }

    private fun sync() {
        if (!destroyed) stepper?.update(hostState, shown)
    }

    private fun handleTap(mapView: MapView, loaded: MapLibreMap, latLng: LatLng): Boolean {
        // Circles can be 8dp wide; search a 48dp box and pick the closest marker.
        val tap = loaded.projection.toScreenLocation(latLng)
        val half = QuakeMapDefaults.TAP_TARGET_DP / 2 * mapView.resources.displayMetrics.density
        val box = RectF(tap.x - half, tap.y - half, tap.x + half, tap.y + half)
        val id = loaded.queryRenderedFeatures(box, QuakeLayer.LAYER_ID)
            .minByOrNull { feature ->
                val point = feature.geometry() as? Point ?: return@minByOrNull Float.MAX_VALUE
                val screen = loaded.projection.toScreenLocation(LatLng(point.latitude(), point.longitude()))
                hypot(screen.x - tap.x, screen.y - tap.y)
            }
            ?.getStringProperty(QuakeLayer.PROP_ID)
        id?.let(onEarthquakeClick)
        return id != null
    }
}

/**
 * Moves a view's lifecycle one step at a time towards what it should be: the host's state while the
 * view is shown, capped at CREATED while it's hidden or the host is stopped. Every start/resume is
 * therefore paired with a pause/stop, whatever order host and visibility changes arrive in.
 */
internal class LifecycleStepper(
    private val onStart: () -> Unit,
    private val onResume: () -> Unit,
    private val onPause: () -> Unit,
    private val onStop: () -> Unit
) {
    var current: Lifecycle.State = Lifecycle.State.CREATED
        private set

    fun update(host: Lifecycle.State, shown: Boolean) {
        val target = if (shown && host.isAtLeast(Lifecycle.State.STARTED)) host else Lifecycle.State.CREATED
        while (current != target) {
            current = when {
                current < target && current == Lifecycle.State.CREATED -> Lifecycle.State.STARTED.also { onStart() }
                current < target -> Lifecycle.State.RESUMED.also { onResume() }
                current == Lifecycle.State.RESUMED -> Lifecycle.State.STARTED.also { onPause() }
                else -> Lifecycle.State.CREATED.also { onStop() }
            }
        }
    }
}

/**
 * A [QuakeMapHolder] tied to the calling composable's lifecycle (the activity, at the nav host).
 * The map itself is created lazily, the first time the Map tab is shown.
 */
@Composable
fun rememberQuakeMapHolder(): QuakeMapHolder {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val holder = remember(context) { QuakeMapHolder(context) }
    DisposableEffect(holder, lifecycle) {
        val observer = LifecycleEventObserver { _, _ -> holder.setHostState(lifecycle.currentState) }
        // The retained map stays alive on other tabs, so let it drop caches under memory pressure.
        val memory = object : ComponentCallbacks2 {
            override fun onTrimMemory(level: Int) {
                if (level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) holder.onLowMemory()
            }
            override fun onConfigurationChanged(newConfig: Configuration) = Unit

            @Deprecated("Deprecated in Java")
            override fun onLowMemory() = holder.onLowMemory()
        }
        lifecycle.addObserver(observer)
        context.registerComponentCallbacks(memory)
        onDispose {
            lifecycle.removeObserver(observer)
            context.unregisterComponentCallbacks(memory)
            holder.destroy()
        }
    }
    return holder
}

/**
 * Thin Compose wrapper that shows [holder]'s retained MapLibre map. Holds no app state: it renders
 * [earthquakes] and [userLocation], starts from [camera] (or the user, or the whole world) the first
 * time the map is created, and reports taps and camera moves upward.
 *
 * The very first creation is deferred until after the first frame, so the tab renders immediately,
 * and a spinner covers the map until MapLibre's first full render. Later visits show it at once.
 */
@Composable
fun QuakeMap(
    holder: QuakeMapHolder,
    earthquakes: List<EarthquakeUiState>,
    userLocation: GeoPoint?,
    camera: MapCameraUiState?,
    darkTheme: Boolean,
    onEarthquakeClick: (String) -> Unit,
    onCameraIdle: (MapCameraUiState) -> Unit,
    modifier: Modifier = Modifier
) {
    var attach by remember { mutableStateOf(holder.isCreated) }
    LaunchedEffect(Unit) {
        if (attach) return@LaunchedEffect
        withFrameNanos { }
        attach = true
    }
    SideEffect {
        holder.onEarthquakeClick = onEarthquakeClick
        holder.onCameraIdle = onCameraIdle
    }
    Box(modifier = modifier) {
        if (attach) {
            RetainedMapView(holder, earthquakes, userLocation, camera, darkTheme, Modifier.fillMaxSize())
        }
        if (!holder.rendered) {
            val loadingDescription = stringResource(R.string.map_loading)
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .semantics { contentDescription = loadingDescription },
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
    }
}

@Composable
private fun RetainedMapView(
    holder: QuakeMapHolder,
    earthquakes: List<EarthquakeUiState>,
    userLocation: GeoPoint?,
    camera: MapCameraUiState?,
    darkTheme: Boolean,
    modifier: Modifier = Modifier
) {
    val userColor = MaterialTheme.colorScheme.primary.toArgb()
    val userStrokeColor = MaterialTheme.colorScheme.surface.toArgb()

    DisposableEffect(holder) {
        holder.setShown(true)
        onDispose { holder.setShown(false) }
    }

    LaunchedEffect(holder.map, userLocation) {
        val loaded = holder.map ?: return@LaunchedEffect
        if (holder.centeredOnUser || userLocation == null) return@LaunchedEffect
        holder.centeredOnUser = true
        // Jump rather than animate: a fly from the world view loads tiles at every zoom on the way.
        loaded.moveCamera(
            CameraUpdateFactory.newCameraPosition(
                cameraPosition(userLocation.latitude, userLocation.longitude, QuakeMapDefaults.USER_ZOOM)
            )
        )
    }

    LaunchedEffect(holder.map, darkTheme) {
        val loaded = holder.map ?: return@LaunchedEffect
        if (holder.styleIsDark == darkTheme && holder.style != null) return@LaunchedEffect
        holder.styleIsDark = darkTheme
        holder.style = null
        loaded.setStyle(
            Style.Builder().fromUri(
                if (darkTheme) QuakeMapDefaults.DARK_STYLE_URL else QuakeMapDefaults.LIGHT_STYLE_URL
            )
        ) { newStyle ->
            newStyle.addSource(GeoJsonSource(QuakeLayer.SOURCE_ID))
            newStyle.addLayer(earthquakeLayer())
            newStyle.addSource(GeoJsonSource(QuakeLayer.USER_SOURCE_ID))
            newStyle.addLayer(userLayer(userColor, userStrokeColor))
            holder.style = newStyle
        }
    }

    LaunchedEffect(holder.style, earthquakes) {
        val source = holder.style?.getSourceAs<GeoJsonSource>(QuakeLayer.SOURCE_ID) ?: return@LaunchedEffect
        source.setGeoJson(earthquakes.toFeatureCollection())
    }

    LaunchedEffect(holder.style, userLocation) {
        val source = holder.style?.getSourceAs<GeoJsonSource>(QuakeLayer.USER_SOURCE_ID) ?: return@LaunchedEffect
        val features =
            listOfNotNull(userLocation?.let { Feature.fromGeometry(Point.fromLngLat(it.longitude, it.latitude)) })
        source.setGeoJson(FeatureCollection.fromFeatures(features))
    }

    AndroidView(factory = { holder.obtainView(startCamera(camera, userLocation)) }, modifier = modifier)
}

private fun earthquakeLayer(): CircleLayer = CircleLayer(QuakeLayer.LAYER_ID, QuakeLayer.SOURCE_ID).withProperties(
    PropertyFactory.circleRadius(
        Expression.interpolate(
            Expression.linear(),
            Expression.get(QuakeLayer.PROP_MAG),
            Expression.stop(2.5, 4f),
            Expression.stop(5, 10f),
            Expression.stop(7, 20f)
        )
    ),
    PropertyFactory.circleColor(
        Expression.match(
            Expression.get(QuakeLayer.PROP_SEVERITY),
            Expression.color(Severity.MINOR.colors().container.toArgb()),
            *Severity.entries.map { severity ->
                Expression.stop(severity.name, Expression.color(severity.colors().container.toArgb()))
            }.toTypedArray()
        )
    ),
    PropertyFactory.circleOpacity(0.85f),
    // A dark stroke keeps pale (minor) circles visible on the light basemap.
    PropertyFactory.circleStrokeWidth(1f),
    PropertyFactory.circleStrokeColor(android.graphics.Color.argb(160, 0, 0, 0))
)

private fun userLayer(color: Int, strokeColor: Int): CircleLayer =
    CircleLayer(QuakeLayer.USER_LAYER_ID, QuakeLayer.USER_SOURCE_ID).withProperties(
        PropertyFactory.circleRadius(7f),
        PropertyFactory.circleColor(color),
        PropertyFactory.circleStrokeWidth(3f),
        PropertyFactory.circleStrokeColor(strokeColor)
    )

/** Where a newly created map starts. */
internal class StartCamera(val position: CameraPosition, val isUserOrRestored: Boolean)

/** A camera restored after recreation wins; then the user, when already known; then the world. */
private fun startCamera(camera: MapCameraUiState?, userLocation: GeoPoint?): StartCamera = when {
    camera != null -> StartCamera(
        cameraPosition(camera.latitude, camera.longitude, camera.zoom),
        isUserOrRestored = true
    )

    userLocation != null ->
        StartCamera(
            cameraPosition(userLocation.latitude, userLocation.longitude, QuakeMapDefaults.USER_ZOOM),
            isUserOrRestored = true
        )

    else -> StartCamera(
        cameraPosition(WORLD_CENTER.latitude, WORLD_CENTER.longitude, QuakeMapDefaults.WORLD_ZOOM),
        isUserOrRestored = false
    )
}

private fun cameraPosition(latitude: Double, longitude: Double, zoom: Double): CameraPosition =
    CameraPosition.Builder().target(LatLng(latitude, longitude)).zoom(zoom).build()

/** Slightly north of the equator, so the populated mid-latitudes fill the first view. */
private val WORLD_CENTER = GeoPoint(latitude = 20.0, longitude = 0.0)

private fun List<EarthquakeUiState>.toFeatureCollection(): FeatureCollection = FeatureCollection.fromFeatures(
    map { quake ->
        Feature.fromGeometry(Point.fromLngLat(quake.longitude, quake.latitude)).apply {
            addStringProperty(QuakeLayer.PROP_ID, quake.id)
            addNumberProperty(QuakeLayer.PROP_MAG, quake.magnitude)
            addStringProperty(QuakeLayer.PROP_SEVERITY, quake.severity.name)
        }
    }
)
