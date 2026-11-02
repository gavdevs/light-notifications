package com.loosewire.notifications.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.viewModelScope
import com.loosewire.notifications.NotificationServices
import com.loosewire.notifications.data.NotificationList
import com.loosewire.notifications.data.NotificationListState
import com.thelightphone.sdk.InitialScreen
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.LightViewModel
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.apps.LightNotification
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightScrollView
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightTheme
import com.thelightphone.sdk.ui.LightThemeController
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.LightTopBar
import com.thelightphone.sdk.ui.LightTopBarCenter
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.lightClickable
import com.thelightphone.sdk.ui.lightTapAndLongPress
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class NotificationListViewModel(
    private val services: NotificationServices,
) : LightViewModel<Unit>() {

    private val _uiState = MutableStateFlow(NotificationListState())
    val uiState: StateFlow<NotificationListState> = _uiState.asStateFlow()

    /**
     * Re-reads access and the active list. Access is re-checked every time
     * because the user can revoke it from settings while this screen is alive.
     */
    fun refresh() {
        viewModelScope.launch {
            val granted = services.notifications.isAccessGranted()
            if (!granted) {
                _uiState.update {
                    it.copy(accessGranted = false, loading = false, rows = emptyList())
                }
                return@launch
            }
            val rows = NotificationList.ordered(services.notifications.active())
            _uiState.update {
                it.copy(
                    accessGranted = true,
                    connected = services.notifications.isListenerConnected(),
                    loading = false,
                    rows = rows,
                )
            }
        }
    }

    fun openAccessSettings() {
        if (!services.notifications.openAccessSettings()) {
            _uiState.update { it.copy(message = "Could not open notification settings") }
        }
    }

    fun open(notification: LightNotification) {
        viewModelScope.launch {
            if (!services.notifications.open(notification)) {
                _uiState.update { it.copy(message = "Could not open ${notification.appLabel}") }
            }
        }
    }

    /**
     * Removes the row immediately rather than waiting for the listener callback,
     * so the tap feels instant even when notification traffic is slow.
     */
    fun dismiss(notification: LightNotification) {
        _uiState.update { state ->
            state.copy(rows = state.rows.filterNot { it.key == notification.key })
        }
        viewModelScope.launch {
            services.notifications.dismiss(notification.key)
        }
    }

    fun dismissMessage() {
        _uiState.update { it.copy(message = null) }
    }
}

@InitialScreen
class NotificationListScreen(sealedActivity: SealedLightActivity) :
    LightScreen<Unit, NotificationListViewModel>(sealedActivity) {

    private val services by lazy { NotificationServices.from(lightContext) }

    override val viewModelClass = NotificationListViewModel::class.java

    override fun createViewModel() = NotificationListViewModel(services)

    override fun willShow() {
        // Access can be granted or revoked outside the app, so this is the only
        // reliable point to re-check it.
        viewModel.refresh()
    }

    @Composable
    override fun Content() {
        val colors by LightThemeController.colors.collectAsState()
        val state by viewModel.uiState.collectAsState()

        // The SDK bumps its version on connect/post/remove; re-reading keeps the
        // list live without polling.
        LaunchedEffect(services) {
            services.notifications.changes().collect { viewModel.refresh() }
        }

        LightTheme(colors = colors) {
            val palette = LightThemeTokens.colors
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(palette.background),
            ) {
                LightTopBar(
                    center = LightTopBarCenter.Text("Notifications"),
                    modifier = Modifier.padding(bottom = 1f.gridUnitsAsDp()),
                )

                state.message?.let { message ->
                    LightText(
                        text = message,
                        variant = LightTextVariant.Detail,
                        lighten = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .lightClickable { viewModel.dismissMessage() }
                            .padding(
                                horizontal = 1f.gridUnitsAsDp(),
                                vertical = 0.5f.gridUnitsAsDp(),
                            ),
                    )
                }

                when {
                    !state.accessGranted -> AccessGate(onOpenSettings = viewModel::openAccessSettings)
                    state.loading -> Centered("Loading…")
                    // Access is granted here, so an unbound listener is a
                    // cold-start wait rather than a permission problem.
                    state.rows.isEmpty() && !state.connected -> Centered("Connecting…")
                    state.rows.isEmpty() -> Centered("No notifications")
                    else -> NotificationRows(
                        rows = state.rows,
                        modifier = Modifier.weight(1f),
                        onTap = viewModel::open,
                        onDismiss = viewModel::dismiss,
                        onLongPress = { notification ->
                            navigateTo(
                                screenFactory = { activity ->
                                    NotificationActionsScreen(
                                        activity,
                                        notification,
                                        services,
                                    )
                                },
                                resultCallback = { viewModel.refresh() },
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun AccessGate(onOpenSettings: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 2f.gridUnitsAsDp()),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            LightText(
                text = "Notifications needs access to read them.",
                variant = LightTextVariant.Copy,
                lighten = true,
            )
            Box(
                modifier = Modifier
                    .padding(top = 2f.gridUnitsAsDp())
                    .lightClickable(onClick = onOpenSettings)
                    .padding(1f.gridUnitsAsDp()),
            ) {
                LightText(text = "OPEN SETTINGS", variant = LightTextVariant.Copy)
            }
        }
    }
}

@Composable
private fun NotificationRows(
    rows: List<LightNotification>,
    modifier: Modifier = Modifier,
    onTap: (LightNotification) -> Unit,
    onDismiss: (LightNotification) -> Unit,
    onLongPress: (LightNotification) -> Unit,
) {
    LightScrollView(modifier = modifier.fillMaxSize()) {
        rows.forEach { notification ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ROW_HEIGHT_UNITS.gridUnitsAsDp())
                    .padding(horizontal = 1f.gridUnitsAsDp()),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .width(DISMISS_WIDTH_UNITS.gridUnitsAsDp())
                        .fillMaxHeight()
                        .lightClickable(onClickLabel = "Dismiss") { onDismiss(notification) },
                    contentAlignment = Alignment.CenterStart,
                ) {
                    LightText(text = "✕", variant = LightTextVariant.Copy)
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .lightTapAndLongPress(
                            onClickLabel = "Open",
                            onLongClickLabel = "Notification actions",
                            onTap = { onTap(notification) },
                            onLongPress = { onLongPress(notification) },
                        ),
                    verticalArrangement = Arrangement.Center,
                ) {
                    LightText(
                        text = notification.title,
                        variant = LightTextVariant.Copy,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    NotificationList.body(notification)?.let { body ->
                        LightText(
                            text = body,
                            variant = LightTextVariant.Detail,
                            lighten = true,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun Centered(text: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        LightText(
            text = text,
            variant = LightTextVariant.Copy,
            lighten = true,
            modifier = Modifier.padding(horizontal = 2f.gridUnitsAsDp()),
        )
    }
}

private const val ROW_HEIGHT_UNITS = 5f
private const val DISMISS_WIDTH_UNITS = 3f
