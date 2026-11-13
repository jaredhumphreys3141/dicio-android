package org.stypox.dicio.io.wake

import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.stypox.dicio.skills.notify.NotifyHandler

/**
 * Decides whether the [WakeService] should actually be using the microphone. When "only while
 * media plays" mode is enabled (the default), the wake word is only listened for while one of the
 * trigger apps (YouTube, YouTube Music) is playing, and for [STOP_DELAY_MILLIS] after it stops, so
 * that e.g. "Hey Dicio, play the music" still works right after pausing.
 *
 * Playback is observed through media sessions, which requires the notification access that is
 * granted to [NotifyHandler]; [NotifyHandler] attaches and detaches this gate.
 */
object MediaWakeGate {
    private const val TAG = "MediaWakeGate"
    private const val PREFS_NAME = "media_wake_gate"
    private const val KEY_ONLY_WHILE_MEDIA_PLAYS = "only_while_media_plays"
    private const val STOP_DELAY_MILLIS = 60_000L

    private val PLAYING_STATES = setOf(
        PlaybackState.STATE_PLAYING,
        PlaybackState.STATE_BUFFERING,
        PlaybackState.STATE_CONNECTING,
        PlaybackState.STATE_FAST_FORWARDING,
        PlaybackState.STATE_REWINDING,
        PlaybackState.STATE_SKIPPING_TO_NEXT,
        PlaybackState.STATE_SKIPPING_TO_PREVIOUS,
        PlaybackState.STATE_SKIPPING_TO_QUEUE_ITEM,
    )

    private val mediaPlayingFlow = MutableStateFlow(false)
    /** Whether a trigger app is playing (or stopped playing less than a minute ago). */
    val mediaPlaying: StateFlow<Boolean> = mediaPlayingFlow

    private val handler = Handler(Looper.getMainLooper())
    private var stopPending = false
    private val markStoppedRunnable = Runnable {
        stopPending = false
        Log.d(TAG, "Trigger app stopped playing")
        mediaPlayingFlow.value = false
    }

    private var prefs: SharedPreferences? = null
    @Volatile
    private var onlyWhileMediaPlays = true

    private var sessionManager: MediaSessionManager? = null
    private val tracked = mutableMapOf<MediaSession.Token, Pair<MediaController, MediaController.Callback>>()
    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener {
        updateControllers(it.orEmpty())
    }

    // ---------------------------------------------------------------------------------------------
    // Setting

    @Synchronized
    private fun loadPrefs(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .also { onlyWhileMediaPlays = it.getBoolean(KEY_ONLY_WHILE_MEDIA_PLAYS, true) }
        }
    }

    fun isOnlyWhileMediaPlays(context: Context): Boolean {
        loadPrefs(context)
        return onlyWhileMediaPlays
    }

    fun setOnlyWhileMediaPlays(context: Context, value: Boolean) {
        loadPrefs(context)
        onlyWhileMediaPlays = value
        prefs?.edit()?.putBoolean(KEY_ONLY_WHILE_MEDIA_PLAYS, value)?.apply()
    }

    /** Whether the wake word service should be recording right now. */
    fun shouldListen(context: Context): Boolean =
        !isOnlyWhileMediaPlays(context) || mediaPlayingFlow.value

    // The YouTube app, YouTube Music, and modified builds of them
    private fun isTriggerApp(packageName: String): Boolean =
        packageName.contains("youtube", ignoreCase = true)

    // ---------------------------------------------------------------------------------------------
    // Media session tracking

    /** Called by [NotifyHandler] once notification access is active. */
    fun attach(service: NotificationListenerService) {
        handler.post {
            try {
                val manager = ContextCompat.getSystemService(service, MediaSessionManager::class.java)
                    ?: return@post
                val component = ComponentName(service, NotifyHandler::class.java)
                sessionManager?.removeOnActiveSessionsChangedListener(sessionsListener)
                manager.addOnActiveSessionsChangedListener(sessionsListener, component, handler)
                sessionManager = manager
                updateControllers(manager.getActiveSessions(component))
            } catch (e: SecurityException) {
                Log.w(TAG, "Notification access not granted, cannot observe media sessions", e)
            }
        }
    }

    /** Called by [NotifyHandler] when notification access goes away. */
    fun detach() {
        handler.post {
            sessionManager?.removeOnActiveSessionsChangedListener(sessionsListener)
            sessionManager = null
            updateControllers(emptyList())
        }
    }

    private fun updateControllers(controllers: List<MediaController>) {
        val triggerControllers = controllers.filter { isTriggerApp(it.packageName) }
        val currentTokens = triggerControllers.map { it.sessionToken }.toSet()

        // stop observing sessions that went away
        tracked.keys.filter { it !in currentTokens }.forEach { token ->
            tracked.remove(token)?.let { (controller, callback) ->
                controller.unregisterCallback(callback)
            }
        }

        // start observing new ones
        triggerControllers.filter { it.sessionToken !in tracked }.forEach { controller ->
            val callback = object : MediaController.Callback() {
                override fun onPlaybackStateChanged(state: PlaybackState?) = recompute()
                override fun onSessionDestroyed() = recompute()
            }
            controller.registerCallback(callback, handler)
            tracked[controller.sessionToken] = controller to callback
        }

        recompute()
    }

    private fun recompute() {
        val playing = tracked.values.any { (controller, _) ->
            controller.playbackState?.state in PLAYING_STATES
        }

        if (playing) {
            handler.removeCallbacks(markStoppedRunnable)
            stopPending = false
            if (!mediaPlayingFlow.value) {
                Log.d(TAG, "Trigger app started playing")
                mediaPlayingFlow.value = true
            }
        } else if (mediaPlayingFlow.value && !stopPending) {
            // keep listening for a bit longer, e.g. to allow "play the music" after pausing
            stopPending = true
            handler.postDelayed(markStoppedRunnable, STOP_DELAY_MILLIS)
        }
    }
}
