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

//        object : MutableReactiveValue<Color> {
//
//        // Fix 1: Interface requires 'var', not 'val'
//        override var value: Color
//            get() {
//                println("DEBUg COlor ${color.value}")
//                println("DEBUG fromHexSting ${Color.fromHexString(inputElement.value)}")
//                return Color.fromHexString(inputElement.value)}
//            set(v) {
//                inputElement.value = v.toAlphalessWeb()
//            }
//
//        // Fix 2: Interface requires 'suspend set'
//        override suspend fun set(value: Color) {
//            // Update the DOM
//            this.value = value
//            // Manually fire events if needed, though usually not necessary for programmatically setting local state
//            // unless you have other listeners relying on the DOM event.
//        }
//
//        // Fix 3: Interface requires listener to be () -> Unit (no arguments)
//        override fun addListener(listener: () -> Unit): () -> Unit {
//            val callback = { _: Event ->
//                listener()
//            }
//
//            // Fix 4: Explicit cast ensures addEventListener/removeEventListener are found
//            inputElement.addEventListener("input", callback)
//            inputElement.addEventListener("change", callback)
//
//            return {
//                inputElement.removeEventListener("input", callback)
//                inputElement.removeEventListener("change", callback)
//            }
//        }
//    }

    actual var enabled: Boolean
        get() = !inputElement.disabled
        set(value) { inputElement.disabled = !value }
}