package com.kf7mxe.inglenook

import com.lightningkite.kiteui.models.Icon
import com.lightningkite.kiteui.models.Icon.StrokeLineCap
import com.lightningkite.kiteui.models.Icon.StrokePathData
import com.lightningkite.kiteui.models.dp
import com.lightningkite.kiteui.models.rem

// Drag handle for bottom sheet
val Icon.Companion.dragHandle get() = Icon(
    width = 1.5.rem,
    height = 1.5.rem,
    viewBoxMinX = 0,
    viewBoxMinY = -960,
    viewBoxWidth = 960,
    viewBoxHeight = 960,
    listOf("M160-360v-80h640v80H160Zm0-160v-80h640v80H160Z")
)

// Book icon
val Icon.Companion.book get() = Icon(
    width = 1.5.rem,
    height = 1.5.rem,
    viewBoxMinX = 0,
    viewBoxMinY = -960,
    viewBoxWidth = 960,
    viewBoxHeight = 960,
    listOf("M240-80q-33 0-56.5-23.5T160-160v-640q0-33 23.5-56.5T240-880h480q33 0 56.5 23.5T800-800v640q0 33-23.5 56.5T720-80H240Zm0-80h480v-640H240v640Zm80-480h320v-80H320v80Zm0 160h320v-80H320v80Zm0 160h200v-80H320v80Zm-80 160h480-480Z")
)

// Headphones icon for audiobooks
val Icon.Companion.headphones get() = Icon(
    width = 1.5.rem,
    height = 1.5.rem,
    viewBoxMinX = 0,
    viewBoxMinY = -960,
    viewBoxWidth = 960,
    viewBoxHeight = 960,
    listOf("M360-120H200q-33 0-56.5-23.5T120-200v-280q0-75 28.5-140.5t77-114q48.5-48.5 114-77T480-840q75 0 140.5 28.5t114 77q48.5 48.5 77 114T840-480v280q0 33-23.5 56.5T760-120H600v-320h160v-40q0-117-81.5-198.5T480-760q-117 0-198.5 81.5T200-480v40h160v320Zm-80-240h-80v160h80v-160Zm400 0v160h80v-160h-80Z")
)

// Play button
val Icon.Companion.playArrow get() = Icon(
    width = 1.5.rem,
    height = 1.5.rem,
    viewBoxMinX = 0,
    viewBoxMinY = -960,
    viewBoxWidth = 960,
    viewBoxHeight = 960,
    listOf("M320-200v-560l440 280-440 280Z")
)

// Pause button
val Icon.Companion.pause get() = Icon(
    width = 1.5.rem,
    height = 1.5.rem,
    viewBoxMinX = 0,
    viewBoxMinY = -960,
    viewBoxWidth = 960,
    viewBoxHeight = 960,
    listOf("M560-200v-560h160v560H560Zm-320 0v-560h160v560H240Z")
)

// Skip forward 30 seconds
val Icon.Companion.forward30 get() = Icon(
    width = 1.5.rem,
    height = 1.5.rem,
    viewBoxMinX = 0,
    viewBoxMinY = -960,
    viewBoxWidth = 960,
    viewBoxHeight = 960,
    listOf("M480-80q-75 0-140.5-28.5t-114-77q-48.5-48.5-77-114T120-440h80q0 117 81.5 198.5T480-160q117 0 198.5-81.5T760-440q0-117-81.5-198.5T480-720h-6l62 62-56 58-160-160 160-160 56 58-62 62h6q75 0 140.5 28.5t114 77q48.5 48.5 77 114T840-440q0 75-28.5 140.5t-77 114q-48.5 48.5-114 77T480-80ZM380-320v-160h-60v-40h100v200h-40Zm100 0v-80q0-17 11.5-28.5T520-440h40v-40h-80v-40h80q17 0 28.5 11.5T600-480v40q0 17-11.5 28.5T560-400h-40v40h80v40h-80q-17 0-28.5-11.5T480-360Z")
)

// Skip backward 15 seconds
val Icon.Companion.replay15 get() = Icon(
    width = 1.5.rem,
    height = 1.5.rem,
    viewBoxMinX = 0,
    viewBoxMinY = -960,
    viewBoxWidth = 960,
    viewBoxHeight = 960,
    listOf("M480-80q-75 0-140.5-28.5t-114-77q-48.5-48.5-77-114T120-440h80q0 117 81.5 198.5T480-160q117 0 198.5-81.5T760-440q0-117-81.5-198.5T480-720h-6l62 62-56 58-160-160 160-160 56 58-62 62h6q75 0 140.5 28.5t114 77q48.5 48.5 77 114T840-440q0 75-28.5 140.5t-77 114q-48.5 48.5-114 77T480-80ZM380-320v-160h-60v-40h100v200h-40Zm100 0v-80q0-17 11.5-28.5T520-440h40v-40h-80v-40h80q17 0 28.5 11.5T600-480v40q0 17-11.5 28.5T560-400h-40v40h80v40h-80q-17 0-28.5-11.5T480-360Z")
)

// Skip to next track
val Icon.Companion.skipNext get() = Icon(
    width = 1.5.rem,
    height = 1.5.rem,
    viewBoxMinX = 0,
    viewBoxMinY = -960,
    viewBoxWidth = 960,
    viewBoxHeight = 960,
    listOf("M660-240v-480h80v480h-80Zm-440 0v-480l360 240-360 240Z")
)

// Skip to previous track
val Icon.Companion.skipPrevious get() = Icon(
    width = 1.5.rem,
    height = 1.5.rem,
    viewBoxMinX = 0,
    viewBoxMinY = -960,
    viewBoxWidth = 960,
    viewBoxHeight = 960,
    listOf("M220-240v-480h80v480h-80Zm520 0-360-240 360-240v480Z")
)

// Download icon
val Icon.Companion.download get() = Icon(
    width = 1.5.rem,
    height = 1.5.rem,
    viewBoxMinX = 0,
    viewBoxMinY = -960,
    viewBoxWidth = 960,
    viewBoxHeight = 960,
    listOf("M480-320 280-520l56-58 104 104v-326h80v326l104-104 56 58-200 200ZM240-160q-33 0-56.5-23.5T160-240v-120h80v120h480v-120h80v120q0 33-23.5 56.5T720-160H240Z")
)

// Download done/check
val Icon.Companion.downloadDone get() = Icon(
    width = 1.5.rem,
    height = 1.5.rem,
    viewBoxMinX = 0,
    viewBoxMinY = -960,
    viewBoxWidth = 960,
    viewBoxHeight = 960,
    listOf("M480-336 280-536l56-58 144 144 144-144 56 58-200 200ZM240-160q-33 0-56.5-23.5T160-240v-120h80v120h480v-120h80v120q0 33-23.5 56.5T720-160H240Zm0-480v-80h480v80H240Z")
)

// Library/collection icon
val Icon.Companion.libraryBooks get() = Icon(
    width = 1.5.rem,
    height = 1.5.rem,
    viewBoxMinX = 0,
    viewBoxMinY = -960,
    viewBoxWidth = 960,
    viewBoxHeight = 960,
    listOf("M480-400q-33 0-56.5-23.5T400-480v-320q0-33 23.5-56.5T480-880h320q33 0 56.5 23.5T880-800v320q0 33-23.5 56.5T800-400H480Zm0-80h320v-320H480v320ZM160-80q-33 0-56.5-23.5T80-160v-480h80v480h480v80H160Zm160-160q-33 0-56.5-23.5T240-320v-400q0-33 23.5-56.5T320-800h80v80h-80v400h400v-80h80v80q0 33-23.5 56.5T720-240H320Zm160-240v-320 320Z")
)

// Speed/playback rate
val Icon.Companion.speed get() = Icon(
    width = 1.5.rem,
    height = 1.5.rem,
    viewBoxMinX = 0,
    viewBoxMinY = -960,
    viewBoxWidth = 960,
    viewBoxHeight = 960,
    listOf("M418-340q24 24 62 23.5t56-27.5l224-336-336 224q-27 18-28.5 55t22.5 61ZM480-80q-83 0-156-31.5T197-197q-54-54-85.5-127T80-480q0-83 31.5-156T197-763q54-54 127-85.5T480-880q83 0 156 31.5T763-763q54 54 85.5 127T880-480q0 83-31.5 156T763-197q-54 54-127 85.5T480-80Zm0-80q134 0 227-93t93-227q0-134-93-227t-227-93q-134 0-227 93t-93 227q0 134 93 227t227 93Zm0-320Z")
)

// Sleep timer / bedtime
val Icon.Companion.bedtime get() = Icon(
    width = 1.5.rem,
    height = 1.5.rem,
    viewBoxMinX = 0,
    viewBoxMinY = -960,
    viewBoxWidth = 960,
    viewBoxHeight = 960,
    listOf("M580-80q-83 0-156-31.5T297-197q-54-54-85.5-127T180-480q0-83 31.5-156T297-763q54-54 127-85.5T580-880q18 0 36 1.5t36 5.5q-38 29-61 72t-23 91q0 83 58.5 141.5T768-510q48 0 91-23t72-61q4 18 5.5 36t1.5 36q0 83-31.5 156T821-239q-54 54-127 85.5T580-80Zm0-80q88 0 158-48.5T854-340q-18 4-36.5 6t-37.5 2q-116 0-198-82t-82-198q0-19 2-37.5t6-36.5q-83 46-131.5 116T308-412q0 113 79.5 192.5T580-140Zm-12-270Z")
)

// List view
val Icon.Companion.viewList get() = Icon(
    width = 1.5.rem,
    height = 1.5.rem,
    viewBoxMinX = 0,
    viewBoxMinY = -960,
    viewBoxWidth = 960,
    viewBoxHeight = 960,
    listOf("M360-240h440v-107H360v107Zm0-187h440v-106H360v106Zm0-186h440v-107H360v107ZM160-240h120v-107H160v107Zm0-187h120v-106H160v106Zm0-186h120v-107H160v107ZM80-80v-800h800v800H80Zm80-80h640v-640H160v640Zm0 0v-640 640Z")
)

// Grid view
val Icon.Companion.viewModule get() = Icon(
    width = 1.5.rem,
    height = 1.5.rem,
    viewBoxMinX = 0,
    viewBoxMinY = -960,
    viewBoxWidth = 960,
    viewBoxHeight = 960,
    listOf("M120-520v-320h320v320H120Zm0 400v-320h320v320H120Zm400-400v-320h320v320H520Zm0 400v-320h320v320H520ZM200-600h160v-160H200v160Zm400 0h160v-160H600v160Zm0 400h160v-160H600v160Zm-400 0h160v-160H200v160Zm400-400Zm0 240Zm-240 0Zm0-240Z")
)

// Sort icon
val Icon.Companion.sort get() = Icon(
    width = 1.5.rem,
    height = 1.5.rem,
    viewBoxMinX = 0,
    viewBoxMinY = -960,
    viewBoxWidth = 960,
    viewBoxHeight = 960,
    listOf("M120-240v-80h240v80H120Zm0-200v-80h480v80H120Zm0-200v-80h720v80H120Z")
)

// Filter icon
val Icon.Companion.filter get() = Icon(
    width = 1.5.rem,
    height = 1.5.rem,
    viewBoxMinX = 0,
    viewBoxMinY = -960,
    viewBoxWidth = 960,
    viewBoxHeight = 960,
    listOf("M440-160q-17 0-28.5-11.5T400-200v-240L168-736q-15-20-4.5-42t36.5-22h560q26 0 36.5 22t-4.5 42L560-440v240q0 17-11.5 28.5T520-160h-80Zm40-308 198-252H282l198 252Zm0 0Z")
)

// Bookmark/collection add
val Icon.Companion.collectionsBookmark get() = Icon(
    width = 1.5.rem,
    height = 1.5.rem,
    viewBoxMinX = 0,
    viewBoxMinY = -960,
    viewBoxWidth = 960,
    viewBoxHeight = 960,
    listOf("M320-320h480v-480h-80v280l-100-60-100 60v-280H320v480Zm0 80q-33 0-56.5-23.5T240-320v-480q0-33 23.5-56.5T320-880h480q33 0 56.5 23.5T880-800v480q0 33-23.5 56.5T800-240H320ZM160-80q-33 0-56.5-23.5T80-160v-560h80v560h560v80H160Zm160-720v480-480Z")
)

// Server/connection icon
val Icon.Companion.dns get() = Icon(
    width = 1.5.rem,
    height = 1.5.rem,
    viewBoxMinX = 0,
    viewBoxMinY = -960,
    viewBoxWidth = 960,
    viewBoxHeight = 960,
    listOf("M200-400q-17 0-28.5-11.5T160-440v-160q0-17 11.5-28.5T200-640h560q17 0 28.5 11.5T800-600v160q0 17-11.5 28.5T760-400H200Zm40-80h200v-80H240v80Zm380 0q17 0 28.5-11.5T660-520q0-17-11.5-28.5T620-560q-17 0-28.5 11.5T580-520q0 17 11.5 28.5T620-480Zm80 0q17 0 28.5-11.5T740-520q0-17-11.5-28.5T700-560q-17 0-28.5 11.5T660-520q0 17 11.5 28.5T700-480ZM200-80q-17 0-28.5-11.5T160-120v-160q0-17 11.5-28.5T200-320h560q17 0 28.5 11.5T800-280v160q0 17-11.5 28.5T760-80H200Zm40-80h200v-80H240v80Zm380 0q17 0 28.5-11.5T660-200q0-17-11.5-28.5T620-240q-17 0-28.5 11.5T580-200q0 17 11.5 28.5T620-160Zm80 0q17 0 28.5-11.5T740-200q0-17-11.5-28.5T700-240q-17 0-28.5 11.5T660-200q0 17 11.5 28.5T700-160ZM200-720q-17 0-28.5-11.5T160-760v-160q0-17 11.5-28.5T200-960h560q17 0 28.5 11.5T800-920v160q0 17-11.5 28.5T760-720H200Zm40-80h200v-80H240v80Zm380 0q17 0 28.5-11.5T660-840q0-17-11.5-28.5T620-880q-17 0-28.5 11.5T580-840q0 17 11.5 28.5T620-800Zm80 0q17 0 28.5-11.5T740-840q0-17-11.5-28.5T700-880q-17 0-28.5 11.5T660-840q0 17 11.5 28.5T700-800ZM200-160h560-560Zm0-320h560-560Zm0-320h560-560Z")
)

// Timer/clock icon
val Icon.Companion.timer get() = Icon(
    width = 1.5.rem,
    height = 1.5.rem,
    viewBoxMinX = 0,
    viewBoxMinY = -960,
    viewBoxWidth = 960,
    viewBoxHeight = 960,
    listOf("M360-860v-60h240v60H360Zm80 460h80v-240h-80v240Zm40 340q-74 0-139.5-28.5T226-186q-49-49-77.5-114.5T120-440q0-74 28.5-139.5T226-694q49-49 114.5-77.5T480-800q62 0 119 20t107 58l56-56 42 42-56 56q38 50 58 107t20 119q0 74-28.5 139.5T720-200q-49 49-114.5 77.5T480-94Zm0-86q107 0 178.5-71.5T730-430q0-107-71.5-178.5T480-680q-107 0-178.5 71.5T230-430q0 107 71.5 178.5T480-180Zm0-250Z")
)

// Chapter/list icon
val Icon.Companion.formatListNumbered get() = Icon(
    width = 1.5.rem,
    height = 1.5.rem,
    viewBoxMinX = 0,
    viewBoxMinY = -960,
    viewBoxWidth = 960,
    viewBoxHeight = 960,
    listOf("M120-80v-60h100v-30h-60v-60h60v-30H120v-60h120q17 0 28.5 11.5T280-280v40q0 17-11.5 28.5T240-200q17 0 28.5 11.5T280-160v40q0 17-11.5 28.5T240-80H120Zm0-280v-110q0-17 11.5-28.5T160-510h60v-30H120v-60h120q17 0 28.5 11.5T280-560v70q0 17-11.5 28.5T240-450h-60v30h100v60H120Zm60-280v-180h-60v-60h120v240h-60Zm180 440v-80h480v80H360Zm0-240v-80h480v80H360Zm0-240v-80h480v80H360Z")
)

val Icon.Companion.reverseThirtySeconds get() = Icon(
    width = 1.5.rem,
    height = 1.5.rem,
    viewBoxMinX = 0,
    viewBoxMinY = -960,
    viewBoxWidth = 960,
    viewBoxHeight = 960,
    listOf("M480-80q-75 0-140.5-28.5t-114-77q-48.5-48.5-77-114T120-440h80q0 117 81.5 198.5T480-160q117 0 198.5-81.5T760-440q0-117-81.5-198.5T480-720h-6l62 62-56 58-160-160 160-160 56 58-62 62h6q75 0 140.5 28.5t114 77q48.5 48.5 77 114T840-440q0 75-28.5 140.5t-77 114q-48.5 48.5-114 77T480-80ZM300-320v-60h100v-40h-60v-40h60v-40H300v-60h120q17 0 28.5 11.5T460-520v160q0 17-11.5 28.5T420-320H300Zm240 0q-17 0-28.5-11.5T500-360v-160q0-17 11.5-28.5T540-560h80q17 0 28.5 11.5T660-520v160q0 17-11.5 28.5T620-320h-80Zm20-60h40v-120h-40v120Z")
)

val Icon.Companion.forwardThirtySeconds get() = Icon(
    width = 1.5.rem,
    height = 1.5.rem,
    viewBoxMinX = 0,
    viewBoxMinY = -960,
    viewBoxWidth = 960,
    viewBoxHeight = 960,
    listOf("M300-320v-60h100v-40h-60v-40h60v-40H300v-60h120q17 0 28.5 11.5T460-520v160q0 17-11.5 28.5T420-320H300Zm240 0q-17 0-28.5-11.5T500-360v-160q0-17 11.5-28.5T540-560h80q17 0 28.5 11.5T660-520v160q0 17-11.5 28.5T620-320h-80Zm20-60h40v-120h-40v120ZM480-80q-75 0-140.5-28.5t-114-77q-48.5-48.5-77-114T120-440q0-75 28.5-140.5t77-114q48.5-48.5 114-77T480-800h6l-62-62 56-58 160 160-160 160-56-58 62-62h-6q-117 0-198.5 81.5T200-440q0 117 81.5 198.5T480-160q117 0 198.5-81.5T760-440h80q0 75-28.5 140.5t-77 114q-48.5 48.5-114 77T480-80Z")
)

// Checkmark icon
val Icon.Companion.check get() = Icon(
    width = 1.5.rem,
    height = 1.5.rem,
    viewBoxMinX = 0,
    viewBoxMinY = -960,
    viewBoxWidth = 960,
    viewBoxHeight = 960,
    listOf("M382-240 154-468l57-57 171 171 367-367 57 57-424 424Z")
)

// Stop icon
val Icon.Companion.stop get() = Icon(
    width = 1.5.rem,
    height = 1.5.rem,
    viewBoxMinX = 0,
    viewBoxMinY = -960,
    viewBoxWidth = 960,
    viewBoxHeight = 960,
    listOf("M240-240v-480h480v480H240Zm80-80h320v-320H320v320Zm0 0v-320 320Z")
)

// Edit icon
val Icon.Companion.edit get() = Icon(
    width = 1.5.rem,
    height = 1.5.rem,
    viewBoxMinX = 0,
    viewBoxMinY = -960,
    viewBoxWidth = 960,
    viewBoxHeight = 960,
    listOf("M200-200h57l391-391-57-57-391 391v57Zm-80 80v-170l528-527q12-11 26.5-17t30.5-6q16 0 31 6t26 18l55 56q12 11 17.5 26t5.5 30q0 16-5.5 30.5T817-647L290-120H120Zm640-584-56-56 56 56Zm-141 85-28-29 57 57-29-28Z")
)

val Icon.Companion.dashboard get() = Icon(
    width = 1.5.rem,
    height = 1.5.rem,
    viewBoxMinX = 0,
    viewBoxMinY = -960,
    viewBoxWidth = 960,
    viewBoxHeight = 960,
    listOf("M520-600v-240h320v240H520ZM120-440v-400h320v400H120Zm400 320v-400h320v400H520Zm-400 0v-240h320v240H120Zm80-400h160v-240H200v240Zm400 320h160v-240H600v240Zm0-480h160v-80H600v80ZM200-200h160v-80H200v80Zm160-320Zm240-160Zm0 240ZM360-280Z")
)
val Icon.Companion.unfoldLess get() = Icon(
    width = 1.5.rem,
    height = 1.5.rem,
    viewBoxMinX = 0,
    viewBoxMinY = -960,
    viewBoxWidth = 960,
    viewBoxHeight = 960,
    listOf("m356-160-56-56 180-180 180 180-56 56-124-124-124 124Zm124-404L300-744l56-56 124 124 124-124 56 56-180 180Z")
)
val Icon.Companion.unfoldMore get() = Icon(
    width = 1.5.rem,
    height = 1.5.rem,
    viewBoxMinX = 0,
    viewBoxMinY = -960,
    viewBoxWidth = 960,
    viewBoxHeight = 960,
    listOf("M480-120 300-300l58-58 122 122 122-122 58 58-180 180ZM358-598l-58-58 180-180 180 180-58 58-122-122-122 122Z")
)

// Check circle (completed state)
val Icon.Companion.checkCircle get() = Icon(
    width = 1.5.rem,
    height = 1.5.rem,
    viewBoxMinX = 0,
    viewBoxMinY = -960,
    viewBoxWidth = 960,
    viewBoxHeight = 960,
    listOf("m424-296 282-282-56-56-226 226-114-114-56 56 170 170Zm56 216q-83 0-156-31.5T197-197q-54-54-85.5-127T80-480q0-83 31.5-156T197-763q54-54 127-85.5T480-880q83 0 156 31.5T763-763q54 54 85.5 127T880-480q0 83-31.5 156T763-197q-54 54-127 85.5T480-80Zm0-80q134 0 227-93t93-227q0-134-93-227t-227-93q-134 0-227 93t-93 227q0 134 93 227t227 93Zm0-320Z")
)

// Cloud download (downloading state)
val Icon.Companion.cloudDownload get() = Icon(
    width = 1.5.rem,
    height = 1.5.rem,
    viewBoxMinX = 0,
    viewBoxMinY = -960,
    viewBoxWidth = 960,
    viewBoxHeight = 960,
    listOf("M260-160q-91 0-155.5-63T40-377q0-78 47-139t123-78q25-92 100-149t170-57q117 0 198.5 81.5T760-520q69 8 114.5 59.5T920-340q0 75-52.5 127.5T740-160H520v-286l72 72 56-56-168-168-168 168 56 56 72-72v286H260Zm0-80h100v-240h200v240h180q42 0 71-29t29-71q0-42-29-71t-71-29h-60v-80q0-83-58.5-141.5T480-720q-83 0-141.5 58.5T280-520h-20q-58 0-99 41t-41 99q0 58 41 99t99 41Zm220-240Z")
)

// Schedule/pending (waiting state)
val Icon.Companion.schedule get() = Icon(
    width = 1.5.rem,
    height = 1.5.rem,
    viewBoxMinX = 0,
    viewBoxMinY = -960,
    viewBoxWidth = 960,
    viewBoxHeight = 960,
    listOf("m612-292 56-56-148-148v-184h-80v216l172 172ZM480-80q-83 0-156-31.5T197-197q-54-54-85.5-127T80-480q0-83 31.5-156T197-763q54-54 127-85.5T480-880q83 0 156 31.5T763-763q54 54 85.5 127T880-480q0 83-31.5 156T763-197q-54 54-127 85.5T480-80Zm0-400Zm0 320q133 0 226.5-93.5T800-480q0-133-93.5-226.5T480-800q-133 0-226.5 93.5T160-480q0 133 93.5 226.5T480-160Z")
)

// Error icon (failed state)
val Icon.Companion.errorIcon get() = Icon(
    width = 1.5.rem,
    height = 1.5.rem,
    viewBoxMinX = 0,
    viewBoxMinY = -960,
    viewBoxWidth = 960,
    viewBoxHeight = 960,
    listOf("M480-280q17 0 28.5-11.5T520-320q0-17-11.5-28.5T480-360q-17 0-28.5 11.5T440-320q0 17 11.5 28.5T480-280Zm-40-160h80v-240h-80v240Zm40 360q-83 0-156-31.5T197-197q-54-54-85.5-127T80-480q0-83 31.5-156T197-763q54-54 127-85.5T480-880q83 0 156 31.5T763-763q54 54 85.5 127T880-480q0 83-31.5 156T763-197q-54 54-127 85.5T480-80Zm0-80q134 0 227-93t93-227q0-134-93-227t-227-93q-134 0-227 93t-93 227q0 134 93 227t227 93Zm0-320Z")
)