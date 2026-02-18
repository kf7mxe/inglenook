package com.kf7mxe.inglenook

import com.lightningkite.kiteui.models.Color
import com.lightningkite.kiteui.views.RContext
import com.lightningkite.kiteui.views.RView
import com.lightningkite.kiteui.views.direct.vprop
import com.lightningkite.kiteui.views.valueString
import com.lightningkite.reactive.core.MutableReactiveValue
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.events.Event

// Ensure the constructor explicitly declares the actual matching the expect
actual class ColorPicker actual constructor(context: RContext) : RView(context) {

    // Helper to safely access the native element as the correct type
    private val inputElement: HTMLInputElement
        get() = native as HTMLInputElement

    init {
        native.tag = "input"
        native.setAttribute("type", "color")
        native.classes.add("color-picker")
    }

    actual val color: MutableReactiveValue<Color> = native.vprop("input", { Color.fromHexString(attributes.valueString?: "#fff")},{ attributes.valueString = it.toAlphalessWeb() })

    actual var enabled: Boolean
        get() = !inputElement.disabled
        set(value) { inputElement.disabled = !value }
}