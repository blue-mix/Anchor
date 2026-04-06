package com.example.anchor.core.config

object AnchorConstants {
    object Xml {
        const val FEATURE_EXTERNAL_GENERAL_ENTITIES = "http://xml.org/sax/features/external-general-entities"
        const val FEATURE_EXTERNAL_PARAMETER_ENTITIES = "http://xml.org/sax/features/external-parameter-entities"
        const val FEATURE_DISALLOW_DOCTYPE = "http://apache.org/xml/features/disallow-doctype-decl"
    }

    object SharedPreferences {
        const val DEVICE_PREFS = "anchor_device"
        const val SERVER_PREFS = "anchor_server"
        const val USER_PREFS = "anchor_user"

        object Keys {
            const val DEVICE_UUID = "uuid"
            const val SERVER_PORT = "port"
            const val SHARED_DIR_URIS = "shared_dir_uris"
            const val ONBOARDING_DONE = "onboarding_done"
            const val LAST_VIEW_MODE = "last_view_mode"
        }
    }

    object Ssdp {
        object Headers {
            const val LOCATION = "LOCATION"
            const val USN = "USN"
            const val ST = "ST"
            const val NT = "NT"
            const val NTS = "NTS"
            const val SERVER = "SERVER"
            const val CACHE_CONTROL = "CACHE-CONTROL"
        }

        object NotificationTypes {
            const val ALIVE = "ssdp:alive"
            const val BYEBYE = "ssdp:byebye"
        }
    }
}
