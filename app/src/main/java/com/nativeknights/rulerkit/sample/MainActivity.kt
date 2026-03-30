package com.nativeknights.rulerkit.sample

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.nativeknights.rulerkit.HeightUnit
import com.nativeknights.rulerkit.RulerPickerView
import com.nativeknights.rulerkit.WeightUnit

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val rulerWeight  = findViewById<RulerPickerView>(R.id.rulerWeight)
        val rulerHeight  = findViewById<RulerPickerView>(R.id.rulerHeight)
        val rulerAge     = findViewById<RulerPickerView>(R.id.rulerAge)
        val rulerDist    = findViewById<RulerPickerView>(R.id.rulerDistance)
        val tvSelected   = findViewById<TextView>(R.id.tvSelectedValue)
        val btnKg        = findViewById<Button>(R.id.btnKg)
        val btnLb        = findViewById<Button>(R.id.btnLb)
        val btnCm        = findViewById<Button>(R.id.btnCm)
        val btnInch      = findViewById<Button>(R.id.btnInch)

        // ── Weight callbacks ──────────────────────────────
        rulerWeight.onValueChanged = { value, unit ->
            tvSelected.text = "Weight: ${"%.1f".format(value)} $unit"
        }

        btnKg.setOnClickListener {
            rulerWeight.setUnit(WeightUnit.KG)
            btnKg.isEnabled = false
            btnLb.isEnabled = true
        }
        btnLb.setOnClickListener {
            rulerWeight.setUnit(WeightUnit.LB)
            btnKg.isEnabled = true
            btnLb.isEnabled = false
        }
        btnKg.isEnabled = false   // kg is the default

        // ── Height callbacks ──────────────────────────────
        rulerHeight.onValueChanged = { value, unit ->
            tvSelected.text = "Height: ${"%.1f".format(value)} $unit"
        }

        btnCm.setOnClickListener {
            rulerHeight.setUnit(HeightUnit.CM)
            btnCm.isEnabled = false
            btnInch.isEnabled = true
        }
        btnInch.setOnClickListener {
            rulerHeight.setUnit(HeightUnit.INCH)
            btnCm.isEnabled = true
            btnInch.isEnabled = false
        }
        btnCm.isEnabled = false   // cm is the default

        // ── Age (custom) callbacks ────────────────────────
        rulerAge.onValueChanged = { value, unit ->
            tvSelected.text = "Age: ${value.toInt()} $unit"
        }

        // ── Distance callbacks ────────────────────────────
        rulerDist.onValueChanged = { value, unit ->
            tvSelected.text = "Distance: ${"%.1f".format(value)} $unit"
        }
    }
}
