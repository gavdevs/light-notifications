package com.loosewire.notifications.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.viewModelScope
import com.loosewire.notifications.NotificationServices
import com.loosewire.notifications.data.NotificationAction
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NotificationActionsUiState(
    val header: String = "",
    val actions: List<NotificationAction> = listOf(
        NotificationAction.Open,
        NotificationAction.AppNotificationSettings,
        NotificationAction.AppInfo,
        NotificationAction.CopyText,
        NotificationAction.MuteApp,
    ),
    val message: String? = null,
    val close: Boolean = false,
)

class NotificationActionsViewModel(
    private val notification: LightNotification,
    private val services: NotificationServices,
) : LightViewModel<Unit>() {

    private val _uiState = MutableStateFlow(NotificationActionsUiState())
    val uiState: StateFlow<NotificationActionsUiState> = _uiState.asStateFlow()

    init {
        _uiState.update {
            it.copy(
                header = listOf(notification.appLabel, notification.title)
                    .filter { part -> part.isNotBlank() }
                    .distinct()
                    .joinToString(" · "),
            )
        }
    }

    fun perform(action: NotificationAction) {
        when (action) {
            NotificationAction.Open -> run(
                failure = { "Could not open ${notification.appLabel}" },
                block = { services.notifications.open(notification) },
            )

            NotificationAction.AppNotificationSettings,
            NotificationAction.MuteApp,
            -> run(
                failure = { "Could not open ${notification.appLabel} notification settings" },
                block = { services.notifications.openNotificationSettings(notification.packageName) },
            )

            NotificationAction.AppInfo -> run(
                failure = { "Could not open app info for ${notification.appLabel}" },
                block = { services.notifications.openAppInfo(notification.packageName) },
            )

            NotificationAction.CopyText ->
                viewModelScope.launch {
                    if (services.notifications.copyText(notification)) {
                        _uiState.update { it.copy(message = "Copied to clipboard") }
                    } else {
                        _uiState.update { it.copy(message = "Nothing to copy") }
                    }
                }
        }
    }

    fun dismissMessage() = _uiState.update { it.copy(message = null) }

    /**
     * Launches leave this screen behind, so it closes; a failure is reported in
     * place instead.
     */
    private fun run(failure: () -> String, block: suspend () -> Boolean) {
        viewModelScope.launch {
            if (block()) {
                _uiState.update { it.copy(close = true) }
            } else {
                _uiState.update { it.copy(message = failure()) }
            }
        }
    }
}

class NotificationActionsScreen(
    sealedActivity: SealedLightActivity,
    private val notification: LightNotification,
    private val services: NotificationServices,
) : LightScreen<Unit, NotificationActionsViewModel>(sealedActivity) {

    override val viewModelClass = NotificationActionsViewModel::class.java

    override fun createViewModel() = NotificationActionsViewModel(notification, services)

    @Composable
    override fun Content() {
        val colors by LightThemeController.colors.collectAsState()
        val state by viewModel.uiState.collectAsState()

        LaunchedEffect(state.close) {
            if (state.close) goBack()
        }

        LightTheme(colors = colors) {
            val palette = LightThemeTokens.colors
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(palette.background),
            ) {
                LightTopBar(
                    leftButton = LightBarButton.LightIcon(
                        icon = LightIcons.BACK,
                        onClick = { goBack() },
                    ),
                    center = LightTopBarCenter.Text(state.header),
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
                            .padding(horizontal = 1f.gridUnitsAsDp()),
                    )
                }

                LightScrollView(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    state.actions.forEach { action ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(ACTION_ROW_UNITS.gridUnitsAsDp())
                                .lightClickable { viewModel.perform(action) }
                                .padding(horizontal = 1f.gridUnitsAsDp()),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            LightText(
                                text = action.label(),
                                variant = LightTextVariant.Copy,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun NotificationAction.label(): String = when (this) {
    NotificationAction.Open -> "Open app"
    NotificationAction.AppNotificationSettings -> "Notification settings"
    NotificationAction.AppInfo -> "App info"
    NotificationAction.CopyText -> "Copy text"
    NotificationAction.MuteApp -> "Mute app"
}

private const val ACTION_ROW_UNITS = 4f
