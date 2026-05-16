package com.kf7mxe.inglenook.screens

import com.lightningkite.kiteui.models.*
import com.lightningkite.kiteui.navigation.Page
import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.views.card
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.kiteui.views.expanding
import com.lightningkite.kiteui.Routable
import com.lightningkite.reactive.core.Constant

@Routable("/privacy-policy")
class PrivacyPolicyPage : Page {
    override val title get() = Constant("Privacy Policy")

    override fun ViewWriter.render() {
        scrolling.col {
            h3 { content = "Privacy Policy" }
            
            card.col {
                h3 { content = "Our Commitment to Privacy" }
                text { 
                    content = "Inglenook is designed with privacy in mind. We believe that your data belongs to you, and we aim to be as transparent as possible about what information is shared."
                }
            }

            card.col {
                h3 { content = "Personal Data" }
                text { 
                    content = "Inglenook does not collect any personal data, such as your name, email address, or location, by default. The app connects directly to your Jellyfin server, and all library metadata and playback progress are stored on your server and cached locally on your device."
                }
            }

            card.col {
                h3 { content = "Crash Reports & Diagnostics" }
                text { 
                    content = "To help us improve the app and fix bugs, you have the option to share anonymous crash reports and diagnostic data. This information is only collected if you explicitly enable it in the app settings."
                }
                space()
                text {
                    content = "When enabled, we may collect technical information such as:"
                }
                col {
                    padding = 1.rem
                    text { content = "• Device model and operating system version" }
                    text { content = "• App version and build number" }
                    text { content = "• Stack traces and error logs when the app crashes" }
                    text { content = "• Anonymous usage statistics" }
                }
                space()
                text {
                    content = "This data is processed through Sentry and is used solely for the purpose of identifying and resolving technical issues."
                }
            }

            card.col {
                h3 { content = "Third-Party Services" }
                text { 
                    content = "Inglenook connects to your Jellyfin server. Please refer to your Jellyfin server administrator's privacy policy for information on how your data is handled on the server side."
                }
            }

            card.col {
                h3 { content = "Changes to This Policy" }
                text { 
                    content = "We may update our Privacy Policy from time to time. Any changes will be reflected in this page."
                }
            }

            space()
        }
    }
}
