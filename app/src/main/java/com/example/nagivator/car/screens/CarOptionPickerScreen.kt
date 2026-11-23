package com.example.nagivator.car.screens

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template

/**
 * Reusable Android Auto radio-button selection screen for multi-option settings
 * (Theme, Night Map Scheme, Car Speed Preference).
 */
data class CarPickerOption(
    val title: String,
    val subtitle: String? = null
)

class CarOptionPickerScreen(
    carContext: CarContext,
    private val title: String,
    private val options: List<CarPickerOption>,
    private val selectedIndexProvider: () -> Int,
    private val onOptionSelected: (Int) -> Unit
) : Screen(carContext) {

    override fun onGetTemplate(): Template {
        val selectedIndex = selectedIndexProvider()
            .coerceIn(0, (options.size - 1).coerceAtLeast(0))

        val listBuilder = ItemList.Builder()
            .setSelectedIndex(selectedIndex)
            .setOnSelectedListener { index ->
                if (index in options.indices) {
                    onOptionSelected(index)
                    invalidate()
                }
            }

        options.forEach { option ->
            val rowBuilder = Row.Builder().setTitle(option.title)
            if (!option.subtitle.isNullOrBlank()) {
                rowBuilder.addText(option.subtitle)
            }
            listBuilder.addItem(rowBuilder.build())
        }

        return ListTemplate.Builder()
            .setTitle(title)
            .setHeaderAction(Action.BACK)
            .setSingleList(listBuilder.build())
            .build()
    }
}
