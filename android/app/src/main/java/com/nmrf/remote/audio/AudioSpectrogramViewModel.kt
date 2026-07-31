package com.nmrf.remote.audio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import kotlin.math.ln

data class AudioUiState(
    val spectrum: FloatArray,
    val spectrogram: List<FloatArray>,
    val running: Boolean,
)

private const val DISPLAY_BINS = 80
private const val MAX_COLUMNS = 120

@OptIn(ExperimentalCoroutinesApi::class)
class AudioSpectrogramViewModel(private val source: AudioSource) : ViewModel() {
    private val enabled = MutableStateFlow(false)
    private val _state = MutableStateFlow(AudioUiState(FloatArray(0), emptyList(), false))
    val state: StateFlow<AudioUiState> = _state.asStateFlow()
    private val columns = ArrayDeque<FloatArray>()

    init {
        viewModelScope.launch {
            enabled.flatMapLatest { on -> if (on) source.frames else emptyFlow() }
                .collect { frame ->
                    val col = toColumn(Fft.magnitudes(frame))
                    columns.addLast(col)
                    while (columns.size > MAX_COLUMNS) columns.removeFirst()
                    _state.value = AudioUiState(col, columns.toList(), true)
                }
        }
    }

    fun setEnabled(b: Boolean) {
        enabled.value = b
        if (!b) _state.value = _state.value.copy(running = false)
    }

    // ponytail: max-pooling auf DISPLAY_BINS + log-Skalierung; Bitmap-Ring wäre schneller, erst wenn's ruckelt.
    private fun toColumn(mag: FloatArray): FloatArray {
        val usable = mag.size
        val out = FloatArray(DISPLAY_BINS)
        val per = usable.toFloat() / DISPLAY_BINS
        for (j in 0 until DISPLAY_BINS) {
            val start = (j * per).toInt()
            val end = ((j + 1) * per).toInt().coerceAtLeast(start + 1).coerceAtMost(usable)
            var m = 0f
            for (k in start until end) if (mag[k] > m) m = mag[k]
            out[j] = (ln(1f + m) / 7f).coerceIn(0f, 1f)
        }
        return out
    }
}
