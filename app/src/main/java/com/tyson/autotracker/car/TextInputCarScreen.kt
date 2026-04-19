package com.tyson.autotracker.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.*

class TextInputCarScreen(
    carContext: CarContext,
    private val title: String,
    private val initialValue: String,
    private val onInputEntered: (String) -> Unit
) : Screen(carContext) {

    private var currentInput = initialValue

    override fun onGetTemplate(): Template {
        return SearchTemplate.Builder(object : SearchTemplate.SearchCallback {
            override fun onSearchTextChanged(searchText: String) {
                currentInput = searchText
            }

            override fun onSearchSubmitted(searchText: String) {
                onInputEntered(searchText)
                screenManager.pop()
            }
        })
        .setHeaderAction(Action.BACK)
        .setInitialSearchText(initialValue)
        .setSearchHint(title)
        .setShowKeyboardByDefault(true)
        .build()
    }
}
