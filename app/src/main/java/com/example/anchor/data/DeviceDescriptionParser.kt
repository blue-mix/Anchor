package com.example.anchor.data

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader

/**
 * Parses UPnP device description XML to extract device details.
 */
object DeviceDescriptionParser {

    data class DeviceDescription(
        val friendlyName: String = "",
        val manufacturer: String = "",
        val modelName: String = "",
        val modelDescription: String = "",
        val udn: String = "",
        val deviceType: String = "",
        val presentationUrl: String = ""
    )

    /**
     * Parses the device description XML and extracts relevant fields.
     */
    fun parse(xml: String): DeviceDescription? {
        return try {
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(StringReader(xml))

            var friendlyName = ""
            var manufacturer = ""
            var modelName = ""
            var modelDescription = ""
            var udn = ""
            var deviceType = ""
            var presentationUrl = ""

            var eventType = parser.eventType
            var currentTag = ""

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        currentTag = parser.name
                    }
                    XmlPullParser.TEXT -> {
                        val text = parser.text?.trim() ?: ""
                        if (text.isNotEmpty()) {
                            when (currentTag.lowercase()) {
                                "friendlyname" -> friendlyName = text
                                "manufacturer" -> manufacturer = text
                                "modelname" -> modelName = text
                                "modeldescription" -> modelDescription = text
                                "udn" -> udn = text
                                "devicetype" -> deviceType = text
                                "presentationurl" -> presentationUrl = text
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        currentTag = ""
                    }
                }
                eventType = parser.next()
            }

            DeviceDescription(
                friendlyName = friendlyName,
                manufacturer = manufacturer,
                modelName = modelName,
                modelDescription = modelDescription,
                udn = udn,
                deviceType = deviceType,
                presentationUrl = presentationUrl
            )
        } catch (e: Exception) {
            null
        }
    }
}