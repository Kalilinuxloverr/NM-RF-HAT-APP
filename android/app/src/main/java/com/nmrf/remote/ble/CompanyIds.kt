package com.nmrf.remote.ble

/** Auszug der Bluetooth-SIG Company Identifiers -> Herstellername (pure, testbar). */
object CompanyIds {
    private val map = mapOf(
        0x0001 to "Ericsson",
        0x0006 to "Microsoft",
        0x000F to "Broadcom",
        0x0059 to "Nordic Semiconductor",
        0x004C to "Apple",
        0x0075 to "Samsung",
        0x0087 to "Garmin",
        0x00D2 to "Fitbit",
        0x00E0 to "Google",
        0x0117 to "Espressif",
        0x0157 to "Huawei",
        0x0171 to "Amazon",
        0x038F to "Xiaomi",
    )

    fun name(companyId: Int?): String? = companyId?.let { map[it] }
}
