package com.tyson.autotracker.car

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.*
import androidx.core.graphics.drawable.IconCompat

class NumericInputCarScreen(
    carContext: CarContext,
    private val title: String,
    private val initialValue: String,
    private val isDecimal: Boolean = false,
    private val onInputEntered: (String) -> Unit
) : Screen(carContext) {

    private var currentInput = initialValue

    override fun onGetTemplate(): Template {
        val gridBuilder = ItemList.Builder()

        // Create buttons 1-9
        for (i in 1..9) {
            gridBuilder.addItem(createNumberItem(i.toString()))
        }

        // Bottom row: Dot/Clear, 0, Backspace
        if (isDecimal) {
            gridBuilder.addItem(createNumberItem("."))
        } else {
            gridBuilder.addItem(
                GridItem.Builder()
                    .setTitle("Clear")
                    .setImage(createLetterIcon("C"))
                    .setOnClickListener {
                        currentInput = ""
                        invalidate()
                    }
                    .build()
            )
        }

        gridBuilder.addItem(createNumberItem("0"))

        gridBuilder.addItem(
            GridItem.Builder()
                .setTitle("Back")
                .setImage(CarIcon.BACK) // Using default back icon as backspace
                .setOnClickListener {
                    if (currentInput.isNotEmpty()) {
                        currentInput = currentInput.dropLast(1)
                        invalidate()
                    }
                }
                .build()
        )

        val actionStrip = ActionStrip.Builder()
            .addAction(
                Action.Builder()
                    .setTitle("DONE")
                    .setOnClickListener {
                        onInputEntered(currentInput)
                        screenManager.pop()
                    }
                    .build()
            )
            .build()

        return GridTemplate.Builder()
            .setSingleList(gridBuilder.build())
            .setTitle("$title: $currentInput")
            .setHeaderAction(Action.BACK)
            .setActionStrip(actionStrip)
            .build()
    }

    private fun createNumberItem(num: String): GridItem {
        return GridItem.Builder()
            .setTitle(num)
            .setImage(createLetterIcon(num))
            .setOnClickListener {
                currentInput += num
                invalidate()
            }
            .build()
    }

    private fun createLetterIcon(letter: String): CarIcon {
        val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint().apply {
            color = Color.WHITE
            textSize = 48f
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }
        val xPos = canvas.width / 2f
        val yPos = (canvas.height / 2f - (paint.descent() + paint.ascent()) / 2f)
        canvas.drawText(letter, xPos, yPos, paint)
        return CarIcon.Builder(IconCompat.createWithBitmap(bitmap)).build()
    }
}
