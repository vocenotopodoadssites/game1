package com.example.alienfall.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

enum class SfxType {
    ATTACK_MELEE,
    ATTACK_LASER,
    ATTACK_BOW,
    ATTACK_ARCANE,
    HIT,
    ENEMY_DEATH,
    PICKUP_XP,
    PICKUP_COIN,
    LEVEL_UP,
    CARD_SELECT,
    DASH,
    SPECIAL,
    BOSS_ALERT,
    EXPLOSION,
    POISON_TICK,
    LIGHTNING
}

enum class MusicMode {
    MENU,
    EXPLORE,
    COMBAT,
    BOSS
}

/**
 * Procedural Web-Audio-style real-time PCM synthesizer using Android AudioTrack.
 * Produces dark sci-fi ambient synth, combat pulse, boss theme, and distinct SFX
 * without requiring external audio assets.
 */
class SoundEngine {

    private data class ActiveVoice(
        val startSample: Long,
        val durationSamples: Int,
        val freqStart: Float,
        val freqEnd: Float,
        val waveform: Int, // 0=sine, 1=saw, 2=square, 3=noise
        val gain: Float
    )

    private val sampleRate = 22050
    private val running = AtomicBoolean(false)
    private val voices = CopyOnWriteArrayList<ActiveVoice>()

    @Volatile
    var musicEnabled: Boolean = true

    @Volatile
    var sfxEnabled: Boolean = true

    @Volatile
    var musicMode: MusicMode = MusicMode.MENU

    @Volatile
    private var sampleCursor: Long = 0L
    private var audioThread: Thread? = null
    private var audioTrack: AudioTrack? = null

    fun start() {
        if (running.getAndSet(true)) return
        audioThread = Thread({
            try {
                val minBuf = AudioTrack.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                ).coerceAtLeast(2048)

                val track = runCatching {
                    AudioTrack.Builder()
                        .setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_GAME)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                .build()
                        )
                        .setAudioFormat(
                            AudioFormat.Builder()
                                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                .setSampleRate(sampleRate)
                                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                                .build()
                        )
                        .setBufferSizeInBytes(minBuf)
                        .setTransferMode(AudioTrack.MODE_STREAM)
                        .build()
                }.getOrNull()

                if (track == null) {
                    running.set(false)
                    return@Thread
                }
                audioTrack = track
                runCatching { track.play() }

            val chunkSize = 512
            val buffer = ShortArray(chunkSize)

            // Minor sci-fi synth scale frequencies (D minor / Phrygian)
            val bassNotes = floatArrayOf(73.42f, 73.42f, 87.31f, 82.41f, 65.41f, 73.42f, 98.00f, 77.78f)
            val bossNotes = floatArrayOf(55.00f, 58.27f, 65.41f, 55.00f, 73.42f, 69.30f, 58.27f, 55.00f)

            while (running.get()) {
                val now = sampleCursor
                val activeList = voices.toList()

                for (i in 0 until chunkSize) {
                    val tSample = now + i
                    val tSec = tSample.toDouble() / sampleRate
                    var mix = 0.0

                    // Procedural Sci-Fi Background Music
                    if (musicEnabled) {
                        val mode = musicMode
                        val stepDur = when (mode) {
                            MusicMode.MENU -> 0.65
                            MusicMode.EXPLORE -> 0.50
                            MusicMode.COMBAT -> 0.30
                            MusicMode.BOSS -> 0.22
                        }
                        val stepIndex = (tSec / stepDur).toInt()
                        val stepFrac = (tSec / stepDur) - stepIndex
                        val env = exp(-stepFrac * (if (mode == MusicMode.BOSS) 4.2 else 3.0))

                        val droneFreq = if (mode == MusicMode.BOSS) 55.0 else 73.42
                        val pad = sin(2.0 * PI * droneFreq * tSec) * 0.07 +
                            sin(2.0 * PI * (droneFreq * 1.5) * tSec + sin(tSec * 0.7)) * 0.04

                        val seqFreq = if (mode == MusicMode.BOSS) {
                            bossNotes[stepIndex % bossNotes.size].toDouble()
                        } else {
                            bassNotes[stepIndex % bassNotes.size].toDouble()
                        }
                        val bassPulse = when (mode) {
                            MusicMode.MENU -> sin(2.0 * PI * seqFreq * tSec) * 0.05 * env
                            MusicMode.EXPLORE -> sin(2.0 * PI * seqFreq * tSec) * 0.06 * env
                            MusicMode.COMBAT -> {
                                val saw = (((tSec * seqFreq) % 1.0) * 2.0 - 1.0)
                                saw * 0.065 * env
                            }
                            MusicMode.BOSS -> {
                                val saw = (((tSec * seqFreq) % 1.0) * 2.0 - 1.0)
                                val sub = sin(2.0 * PI * (seqFreq * 0.5) * tSec)
                                (saw * 0.08 + sub * 0.07) * env
                            }
                        }

                        val lead = if (mode == MusicMode.COMBAT || mode == MusicMode.BOSS) {
                            val arp = seqFreq * if (stepIndex % 2 == 0) 4.0 else 3.0
                            sin(2.0 * PI * arp * tSec) * 0.03 * exp(-stepFrac * 6.0)
                        } else 0.0

                        mix += (pad + bassPulse + lead)
                    }

                    // Active SFX voices
                    for (v in activeList) {
                        val age = (tSample - v.startSample).toInt()
                        if (age in 0 until v.durationSamples) {
                            val progress = age.toFloat() / v.durationSamples.toFloat()
                            val freq = v.freqStart + (v.freqEnd - v.freqStart) * progress
                            val env = (1f - progress) * (1f - progress)
                            val phase = (age.toDouble() / sampleRate) * freq
                            val wave = when (v.waveform) {
                                0 -> sin(2.0 * PI * phase)
                                1 -> (phase % 1.0) * 2.0 - 1.0
                                2 -> if ((phase % 1.0) < 0.5) 1.0 else -1.0
                                else -> (Random.nextDouble() * 2.0 - 1.0)
                            }
                            mix += wave * v.gain * env
                        }
                    }

                    val clamped = mix.coerceIn(-0.92, 0.92)
                    buffer[i] = (clamped * 32767.0).toInt().toShort()
                }

                sampleCursor += chunkSize
                if (voices.isNotEmpty()) {
                    voices.removeIf { (sampleCursor - it.startSample) > it.durationSamples }
                }

                val written = runCatching { track.write(buffer, 0, chunkSize) }.getOrDefault(-1)
                if (written < 0) break
            }
                runCatching {
                    track.stop()
                    track.release()
                }
                audioTrack = null
            } catch (_: Throwable) {
                running.set(false)
            }
        }, "AlienfallAudioThread").apply {
            isDaemon = true
            start()
        }
    }

    fun stop() {
        running.set(false)
        audioThread?.interrupt()
        audioThread = null
    }

    fun playSfx(type: SfxType) {
        if (!sfxEnabled || !running.get()) return
        if (voices.size > 20) return // Prevent audio saturation in intense swarms
        val now = sampleCursor
        when (type) {
            SfxType.ATTACK_MELEE -> addVoice(now, 0.11f, 320f, 110f, 1, 0.16f)
            SfxType.ATTACK_LASER -> addVoice(now, 0.10f, 920f, 240f, 1, 0.14f)
            SfxType.ATTACK_BOW -> addVoice(now, 0.09f, 680f, 380f, 0, 0.14f)
            SfxType.ATTACK_ARCANE -> addVoice(now, 0.16f, 520f, 880f, 0, 0.15f)
            SfxType.HIT -> addVoice(now, 0.06f, 190f, 90f, 3, 0.11f)
            SfxType.ENEMY_DEATH -> addVoice(now, 0.14f, 240f, 65f, 1, 0.14f)
            SfxType.PICKUP_XP -> addVoice(now, 0.08f, 760f, 1140f, 0, 0.11f)
            SfxType.PICKUP_COIN -> {
                addVoice(now, 0.07f, 1046f, 1318f, 0, 0.13f)
                addVoice(now + 1200, 0.10f, 1318f, 1568f, 0, 0.13f)
            }
            SfxType.LEVEL_UP -> {
                addVoice(now, 0.16f, 440f, 554f, 0, 0.18f)
                addVoice(now + 2400, 0.20f, 554f, 659f, 0, 0.18f)
                addVoice(now + 4800, 0.30f, 659f, 880f, 0, 0.22f)
            }
            SfxType.CARD_SELECT -> addVoice(now, 0.22f, 587f, 1174f, 0, 0.20f)
            SfxType.DASH -> addVoice(now, 0.14f, 420f, 140f, 3, 0.18f)
            SfxType.SPECIAL -> {
                addVoice(now, 0.28f, 220f, 660f, 1, 0.20f)
                addVoice(now + 1800, 0.28f, 330f, 880f, 0, 0.18f)
            }
            SfxType.BOSS_ALERT -> {
                addVoice(now, 0.35f, 180f, 120f, 2, 0.24f)
                addVoice(now + 7500, 0.40f, 180f, 110f, 2, 0.26f)
            }
            SfxType.EXPLOSION -> addVoice(now, 0.26f, 150f, 42f, 3, 0.24f)
            SfxType.POISON_TICK -> addVoice(now, 0.06f, 320f, 260f, 0, 0.07f)
            SfxType.LIGHTNING -> addVoice(now, 0.14f, 1400f, 400f, 2, 0.15f)
        }
    }

    private fun addVoice(
        startSample: Long,
        durSec: Float,
        fStart: Float,
        fEnd: Float,
        waveform: Int,
        gain: Float
    ) {
        voices.add(
            ActiveVoice(
                startSample = startSample,
                durationSamples = (durSec * sampleRate).toInt().coerceAtLeast(100),
                freqStart = fStart,
                freqEnd = fEnd,
                waveform = waveform,
                gain = gain
            )
        )
    }
}
