package com.nativeknights.rulerkit.sample

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.nativeknights.rulerkit.HeightUnit
import com.nativeknights.rulerkit.RulerConfig
import com.nativeknights.rulerkit.RulerPicker
import com.nativeknights.rulerkit.RulerPickerView
import com.nativeknights.rulerkit.WeightUnit
import com.nativeknights.rulerkit.rememberRulerPickerState
import kotlinx.coroutines.launch

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

        // ── Compose ruler demo ────────────────────────────
        val composeRuler = findViewById<ComposeView>(R.id.composeRuler)
        composeRuler.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        composeRuler.setContent {
            MaterialTheme {
                val state = rememberRulerPickerState(
                    RulerConfig(
                        inputType    = com.nativeknights.rulerkit.InputType.Weight(WeightUnit.KG),
                        initialValue = 70f,
                        indicatorColor = 0xFF9C27B0.toInt(),
                    )
                )
                val scope = rememberCoroutineScope()
                var isKg by remember { mutableStateOf(true) }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                ) {
                    RulerPicker(
                        state    = state,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp),
                        onValueChanged = { value, unit ->
                            tvSelected.text = "Weight (Compose): ${"%.1f".format(value)} $unit"
                        }
                    )

                    Row(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(top = 8.dp)
                    ) {
                        Button(
                            onClick = {
                                if (!isKg) {
                                    isKg = true
                                    scope.launch { state.setUnit(WeightUnit.KG) }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isKg) Color(0xFF9C27B0) else Color(0xFFEEEEEE),
                                contentColor   = if (isKg) Color.White else Color.Black
                            )
                        ) {
                            androidx.compose.material3.Text("kg")
                        }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (isKg) {
                                    isKg = false
                                    scope.launch { state.setUnit(WeightUnit.LB) }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (!isKg) Color(0xFF9C27B0) else Color(0xFFEEEEEE),
                                contentColor   = if (!isKg) Color.White else Color.Black
                            )
                        ) {
                            androidx.compose.material3.Text("lb")
                        }
                    }
                }
            }
        }
    }
}
