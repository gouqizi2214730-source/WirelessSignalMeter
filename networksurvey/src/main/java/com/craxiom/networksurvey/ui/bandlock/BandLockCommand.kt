package com.craxiom.networksurvey.ui.bandlock

internal enum class LockRadio(val argument: String, val maxBand: Int, val maxChannel: Int, val maxPci: Int) {
    Lte("lte", 64, 262_143, 503),
    Nr("nr", 512, 3_279_165, 1007),
}

internal enum class LockTarget { Bands, Frequency, Cell }
internal enum class LockNetworkMode(val argument: String) { Auto("auto"), Lte("lte"), Nr("nr") }

internal data class BandLockCommand(val arguments: List<String>, val restartsModem: Boolean) {
    companion object {
        private fun number(text: String, maximum: Int, minimum: Int = 0): Int {
            require(text.trim().matches(Regex("[0-9]+")))
            return requireNotNull(text.trim().toIntOrNull()).also { require(it in minimum..maximum) }
        }

        fun apply(
            radio: LockRadio, target: LockTarget, bands: String, channel: String, pci: String, scs: Int,
        ): BandLockCommand {
            val prefix = radio.argument
            val args: List<String>
            var instantLteBands = false
            when (target) {
                LockTarget.Bands -> {
                    val tokens = bands.trim().split(Regex("[,;\\s]+"))
                    require(tokens.isNotEmpty() && tokens.size <= radio.maxBand)
                    val numbers = tokens.map { token ->
                        number(token.removePrefix("b").removePrefix("B").removePrefix("n").removePrefix("N"), radio.maxBand, 1)
                    }.distinct().sorted()
                    args = listOf("$prefix-band", numbers.joinToString(","))
                    /* Native sends bands 1..32 as an instant CM frame with no modem
                     * restart; only 33..64 still ride the EFS+restart route. */
                    instantLteBands = radio == LockRadio.Lte && numbers.all { it <= 32 }
                }
                LockTarget.Frequency, LockTarget.Cell -> {
                    val frequency = number(channel, radio.maxChannel, if (radio == LockRadio.Nr) 1 else 0)
                    if (radio == LockRadio.Nr) require(scs in setOf(15, 30, 60, 120))
                    args = if (target == LockTarget.Frequency) {
                        listOfNotNull("$prefix-freq", frequency.toString(), scs.toString().takeIf { radio == LockRadio.Nr })
                    } else {
                        val cell = number(pci, radio.maxPci)
                        if (radio == LockRadio.Lte) listOf("lte-cell", "$frequency:$cell")
                        else listOf("nr-cell", number(bands, radio.maxBand, 1).toString(), frequency.toString(), cell.toString(), scs.toString())
                    }
                }
            }
            return BandLockCommand(args, !instantLteBands)
        }

        fun restore(radio: LockRadio, target: LockTarget) = BandLockCommand(
            listOf("${radio.argument}-${when (target) {
                LockTarget.Bands -> "band-restore"
                LockTarget.Frequency -> "freq-clear"
                LockTarget.Cell -> "cell-clear"
            }}"), true,
        )

        fun mode(mode: LockNetworkMode) = BandLockCommand(listOf("mode", mode.argument), false)
        fun probe() = BandLockCommand(listOf("probe"), false)
    }
}
