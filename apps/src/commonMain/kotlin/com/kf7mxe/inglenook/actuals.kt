package com.kf7mxe.inglenook

import com.lightningkite.kiteui.models.Color


import com.lightningkite.kiteui.views.*
import com.lightningkite.reactive.core.MutableReactiveValue
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.text.toInt

@OptIn(ExperimentalContracts::class)
@ViewDsl
inline fun ViewWriter.colorPicker(setup: ColorPicker.() -> Unit = {}): ColorPicker {
    contract { callsInPlace(setup, InvocationKind.EXACTLY_ONCE) }
    return write(ColorPicker(context), setup)
}

expect class ColorPicker(context: RContext) : RView {
    val color: MutableReactiveValue<Color>
    var enabled: Boolean
}



fun Color.toHexString():String {
    return "#" + (this.toInt() and 0xFFFFFF).toString(16).padStart(6, '0')
}

expect fun RView.animatePulsating()
