package com.kf7mxe.inglenook.demo

/**
 * Returns true if the app is running on a known demo website URL.
 * Only relevant on the web (JS) platform; returns false on Android/iOS.
 */
expect fun isDemoWebsite(): Boolean
