package com.tlmc.player.ui.player

import android.media.audiofx.Equalizer
import android.os.Looper
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.tlmc.player.data.repository.ConfigManager
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.ln

/**
 * 播放均衡器：把系统 [Equalizer] 效果绑定到 ExoPlayer 的 audio session。
 *
 * 开关与各频段增益通过 [ConfigManager] 持久化；设备频段数量不同，
 * 因此按中心频率匹配保存值。
 */
@Singleton
class AudioEqualizer @Inject constructor(
    private val configManager: ConfigManager
) {

    data class Band(
        val index: Short,
        val centerFrequencyHz: Int,
        val levelMb: Int,
        val minLevelMb: Int,
        val maxLevelMb: Int
    )

    data class State(
        val available: Boolean,
        val enabled: Boolean,
        val bands: List<Band>,
        val activePreset: EqualizerPreset? = null
    )

    private val _state = MutableLiveData(
        State(available = false, enabled = false, bands = emptyList())
    )
    val state: LiveData<State> = _state

    private var equalizer: Equalizer? = null
    private var sessionId: Int = 0
    private var enabled = false
    private var bands: List<Band> = emptyList()
    private var activePreset: EqualizerPreset? = null

    /**
     * 绑定到指定 audio session。重复绑定同一 session 时直接返回；
     * session 变化时重建效果器并恢复已保存的配置。
     */
    fun attach(audioSessionId: Int) {
        if (audioSessionId <= 0) return
        if (equalizer != null && sessionId == audioSessionId) {
            // 效果器在 AudioTrack 重建后可能失去控制权，需要重新绑定
            val stillValid = try {
                equalizer?.hasControl() == true
            } catch (_: Exception) {
                false
            }
            if (stillValid) return
        }

        releaseEffect()
        sessionId = audioSessionId

        val effect = try {
            Equalizer(0, audioSessionId)
        } catch (_: Exception) {
            null
        }
        val hasControl = effect != null && (try {
            effect.hasControl()
        } catch (_: Exception) {
            false
        })
        if (effect == null || !hasControl) {
            try {
                effect?.release()
            } catch (_: Exception) {
                // 忽略释放失败
            }
            publish(available = false)
            return
        }

        equalizer = effect
        // 先关闭，避免恢复配置期间短暂生效
        try {
            effect.setEnabled(false)
        } catch (_: Exception) {
            releaseEffect()
            publish(available = false)
            return
        }

        try {
            val levelRange = effect.bandLevelRange
            bands = (0 until effect.numberOfBands.toInt()).map { bandOffset ->
                val bandIndex = bandOffset.toShort()
                Band(
                    index = bandIndex,
                    centerFrequencyHz = effect.getCenterFreq(bandIndex) / 1000,
                    levelMb = effect.getBandLevel(bandIndex).toInt(),
                    minLevelMb = levelRange[0].toInt(),
                    maxLevelMb = levelRange[1].toInt()
                )
            }

            val saved = configManager.getEqualizerState()
            if (saved != null) {
                restoreState(saved)
            } else {
                bands.forEach { applyBandLevel(it.index, 0) }
                enabled = false
                effect.setEnabled(false)
                persist()
            }

            if (bands.isEmpty()) {
                throw IllegalStateException("equalizer has no bands")
            }
        } catch (_: Exception) {
            releaseEffect()
            publish(available = false)
            return
        }

        publish(available = true)
    }

    /** 释放效果器（服务销毁时调用）。 */
    fun detach() {
        releaseEffect()
        sessionId = 0
        publish(available = false)
    }

    fun setEnabled(enabled: Boolean) {
        val effect = equalizer ?: return
        try {
            effect.setEnabled(enabled)
        } catch (_: Exception) {
            return
        }
        this.enabled = enabled
        persist()
        publish(available = true)
    }

    fun setBandLevel(bandIndex: Short, levelMb: Int) {
        if (equalizer == null) return
        applyBandLevel(bandIndex, levelMb)
        activePreset = null
        persist()
        publish(available = true)
    }

    fun applyPreset(preset: EqualizerPreset) {
        if (equalizer == null) return
        bands.forEach { band ->
            val level = (preset.gainDbAt(band.centerFrequencyHz) * 100).toInt()
            applyBandLevel(band.index, level)
        }
        activePreset = preset
        persist()
        publish(available = true)
    }

    fun reset() {
        applyPreset(EqualizerPreset.FLAT)
    }

    private fun restoreState(saved: ConfigManager.EqualizerState) {
        bands.forEach { band ->
            val savedBand = saved.bands.minByOrNull {
                abs(it.frequencyHz - band.centerFrequencyHz)
            }
            applyBandLevel(band.index, savedBand?.levelMb ?: 0)
        }
        enabled = saved.enabled
        equalizer?.setEnabled(enabled)
        activePreset = null
    }

    private fun applyBandLevel(bandIndex: Short, levelMb: Int) {
        val band = bands.firstOrNull { it.index == bandIndex } ?: return
        val clamped = levelMb.coerceIn(band.minLevelMb, band.maxLevelMb)
        try {
            equalizer?.setBandLevel(bandIndex, clamped.toShort())
        } catch (_: Exception) {
            return
        }
        bands = bands.map {
            if (it.index == bandIndex) it.copy(levelMb = clamped) else it
        }
    }

    private fun persist() {
        configManager.saveEqualizerState(
            enabled = enabled,
            bands = bands.map {
                ConfigManager.EqualizerBandState(
                    frequencyHz = it.centerFrequencyHz,
                    levelMb = it.levelMb
                )
            }
        )
    }

    private fun releaseEffect() {
        try {
            equalizer?.release()
        } catch (_: Exception) {
            // 忽略释放失败
        }
        equalizer = null
    }

    private fun publish(available: Boolean) {
        val value = State(
            available = available,
            enabled = enabled,
            bands = bands,
            activePreset = activePreset
        )
        if (Looper.myLooper() == Looper.getMainLooper()) {
            _state.value = value
        } else {
            _state.postValue(value)
        }
    }
}

/** 预置音效曲线，按频段中心频率给出目标增益（dB）。 */
enum class EqualizerPreset(val label: String, private val gains: FloatArray) {
    FLAT("平坦", floatArrayOf(0f, 0f, 0f, 0f, 0f)),
    POP("流行", floatArrayOf(2f, 1f, 2f, 2f, 1.5f)),
    ROCK("摇滚", floatArrayOf(5f, 3f, -1f, 2f, 4f)),
    JAZZ("爵士", floatArrayOf(3f, 1.5f, 0.5f, 2f, 3f)),
    CLASSICAL("古典", floatArrayOf(1f, 0.5f, 0f, 1.5f, 2f)),
    ELECTRONIC("电子", floatArrayOf(6f, 3f, 0f, 2f, 3f)),
    DANCE("舞曲", floatArrayOf(5f, 2.5f, 0f, 2f, 4f)),
    VOCAL("人声", floatArrayOf(-4f, 1f, 4f, 2f, -1f)),
    BASS("低音增强", floatArrayOf(7f, 5f, 1f, -2f, -4f)),
    TREBLE("高音增强", floatArrayOf(-3f, -2f, 0f, 3f, 5f)),
    ACOUSTIC("原声", floatArrayOf(1f, 2f, 2f, 1.5f, 1f)),
    HIPHOP("嘻哈", floatArrayOf(7f, 5f, 0f, 1f, -1f)),
    METAL("金属", floatArrayOf(4f, 2f, -2f, 3f, 5f)),
    LOUDNESS("响度", floatArrayOf(6f, 3f, -2f, 4f, 4f));

    /** 返回指定频率的目标增益（dB），按对数频率在控制点之间线性插值。 */
    fun gainDbAt(frequencyHz: Int): Float {
        val frequency = frequencyHz.coerceAtLeast(20).toFloat()
        val first = CONTROL_FREQUENCIES.first()
        val last = CONTROL_FREQUENCIES.last()
        if (frequency <= first) return gains.first()
        if (frequency >= last) return gains.last()

        for (index in 0 until CONTROL_FREQUENCIES.lastIndex) {
            val low = CONTROL_FREQUENCIES[index]
            val high = CONTROL_FREQUENCIES[index + 1]
            if (frequency <= high) {
                val ratio = (ln(frequency) - ln(low)) / (ln(high) - ln(low))
                return gains[index] + (gains[index + 1] - gains[index]) * ratio
            }
        }
        return gains.last()
    }

    companion object {
        private val CONTROL_FREQUENCIES = floatArrayOf(60f, 250f, 1000f, 4000f, 12000f)
    }
}
