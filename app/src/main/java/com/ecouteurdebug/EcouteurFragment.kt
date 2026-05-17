package com.ecouteurdebug

import android.media.MediaPlayer
import android.net.Uri
import android.os.*
import android.provider.OpenableColumns
import android.view.*
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment

class EcouteurFragment : Fragment() {

    private lateinit var btnPlay: Button
    private lateinit var btnStop: Button
    private lateinit var seekBar: SeekBar
    private lateinit var tvFile: TextView
    private lateinit var tvNow: TextView
    private lateinit var tvDur: TextView
    private lateinit var tvStatus: TextView

    private var player: MediaPlayer? = null
    private var currentUri: Uri? = null
    private val handler = Handler(Looper.getMainLooper())

    private val updateSeek = object : Runnable {
        override fun run() {
            player?.let { mp ->
                if (mp.isPlaying) {
                    val pos = mp.currentPosition
                    val dur = mp.duration
                    if (dur > 0) {
                        seekBar.progress = (pos * 100 / dur)
                        tvNow.text = fmtTime(pos)
                        tvDur.text = fmtTime(dur)
                    }
                }
            }
            handler.postDelayed(this, 300)
        }
    }

    private val pickWav = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri ?: return@registerForActivityResult
        try {
            requireContext().contentResolver.takePersistableUriPermission(
                uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (e: Exception) {}
        loadFile(uri)
    }

    override fun onCreateView(inflater: LayoutInflater, c: ViewGroup?, s: Bundle?): View =
        inflater.inflate(R.layout.fragment_ecouter, c, false)

    override fun onViewCreated(v: View, s: Bundle?) {
        super.onViewCreated(v, s)

        btnPlay  = v.findViewById(R.id.btnEcouteurPlay)
        btnStop  = v.findViewById(R.id.btnEcouteurStop)
        seekBar  = v.findViewById(R.id.seekEcouteur)
        tvFile   = v.findViewById(R.id.tvEcouteurFile)
        tvNow    = v.findViewById(R.id.tvEcouteurNow)
        tvDur    = v.findViewById(R.id.tvEcouteurDur)
        tvStatus = v.findViewById(R.id.tvEcouteurStatus)

        v.findViewById<Button>(R.id.btnPickWav).setOnClickListener {
            pickWav.launch(arrayOf("audio/wav", "audio/*"))
        }

        btnPlay.setOnClickListener { togglePlay() }
        btnStop.setOnClickListener { stopPlayer() }

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) {
                if (fromUser) player?.let { mp -> mp.seekTo(mp.duration * p / 100) }
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {}
        })

        handler.post(updateSeek)
    }

    private fun loadFile(uri: Uri) {
        stopPlayer()
        currentUri = uri
        val name = requireContext().contentResolver.query(
            uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null
        )?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        } ?: uri.lastPathSegment ?: "fichier.wav"
        tvFile.text = name
        tvFile.setTextColor(0xFF06b6d4.toInt())
        btnPlay.isEnabled = true
        btnStop.isEnabled = true
        tvStatus.text = "Fichier chargé — appuie Lecture"
        seekBar.progress = 0
        tvNow.text = "0:00"; tvDur.text = "0:00"
    }

    private fun togglePlay() {
        val uri = currentUri ?: return
        val mp = player
        if (mp != null && mp.isPlaying) {
            mp.pause()
            btnPlay.text = "▶ Lecture"
            tvStatus.text = "En pause"
        } else if (mp != null) {
            mp.start()
            btnPlay.text = "⏸ Pause"
            tvStatus.text = "Lecture..."
        } else {
            try {
                val newMp = MediaPlayer().apply {
                    setDataSource(requireContext(), uri)
                    prepare()
                    setOnCompletionListener {
                        btnPlay.text = "▶ Lecture"
                        tvStatus.text = "Terminé"
                        seekBar.progress = 0
                    }
                    start()
                }
                player = newMp
                btnPlay.text = "⏸ Pause"
                tvStatus.text = "Lecture..."
                tvDur.text = fmtTime(newMp.duration)
            } catch (e: Exception) {
                tvStatus.text = "⚠ Erreur: ${e.message}"
            }
        }
    }

    private fun stopPlayer() {
        player?.stop()
        player?.release()
        player = null
        btnPlay.text = "▶ Lecture"
        seekBar.progress = 0
        tvNow.text = "0:00"
        if (currentUri != null) tvStatus.text = "Arrêté"
    }

    private fun fmtTime(ms: Int): String {
        val s = ms / 1000
        return "${s / 60}:${(s % 60).toString().padStart(2, '0')}"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        handler.removeCallbacks(updateSeek)
        player?.release()
        player = null
    }
}
