package com.craxiom.networksurvey.ui.cellular.baidumap

import com.craxiom.messaging.LteRecord
import com.craxiom.messaging.NrRecord
import com.craxiom.messaging.ConnectionStatus
import com.craxiom.networksurvey.model.CellularProtocol
import com.craxiom.networksurvey.model.CellularRecordWrapper
import com.craxiom.networksurvey.model.StationInfo

internal fun List<CellularRecordWrapper>.mapServingCell(): CellularRecordWrapper? {
    var best: CellularRecordWrapper? = null
    var bestRank = 0
    for (cell in this) {
        val rank = when (val record = cell.cellularRecord) {
            is NrRecord -> when {
                record.data.connectionStatus == ConnectionStatus.PRIMARY_SERVING -> 5
                record.data.servingCell.value -> 4
                record.data.connectionStatus == ConnectionStatus.SECONDARY_SERVING -> 1
                else -> 0
            }
            is LteRecord -> when {
                record.data.connectionStatus == ConnectionStatus.PRIMARY_SERVING -> 5
                record.data.servingCell.value -> 3
                record.data.connectionStatus == ConnectionStatus.SECONDARY_SERVING -> 1
                else -> 0
            }
            else -> 0
        }
        if (rank > bestRank) {
            bestRank = rank
            best = cell
        }
    }
    return best
}

/* ==================== CellularRecordWrapper → StationInfo 转换 ==================== */

/**
 * 将上游 CellularRecordWrapper（protobuf 封装）转为本工程工参匹配用的 StationInfo。
 *
 * 仅提取服务小区关键字段（CI/PCI/EARFCN/MCC/MNC/RSRP/RSRQ/SINR/gNBId/sectorId），
 * 供 EngineeringParamDbHelper.match() 做工参自动匹配。
 * 支持 LTE（ECI = eNB×256+sector）和 NR（NCI = gNB×4096+sector）两种制式。
 */
internal fun CellularRecordWrapper.toStationInfo(): StationInfo? {
    return try {
        val s = StationInfo()
        s.isServing = true
        s.pci = -1
        s.earfcn = -1
        s.rsrp = Int.MIN_VALUE
        when (cellularProtocol) {
            CellularProtocol.LTE -> {
                val data = (cellularRecord as LteRecord).data
                s.networkType = "LTE"
                if (data.hasEci()) {
                    s.cellId = data.eci.value.toLong()
                    s.gnbId = (s.cellId / 256L).toInt()
                    s.sectorId = (s.cellId % 256L).toInt()
                }
                if (data.hasEarfcn()) s.earfcn = data.earfcn.value
                if (data.hasPci()) s.pci = data.pci.value
                if (data.hasTac()) s.tac = data.tac.value
                if (data.hasMcc()) s.mcc = data.mcc.value
                if (data.hasMnc()) s.mnc = data.mnc.value
                if (data.hasRsrp()) s.rsrp = data.rsrp.value.toInt()
                s.rsrq = if (data.hasRsrq()) data.rsrq.value.toInt() else Int.MIN_VALUE
                s.sinr = if (data.hasSnr()) data.snr.value.toInt() else Int.MIN_VALUE
            }
            CellularProtocol.NR -> {
                val data = (cellularRecord as NrRecord).data
                s.networkType = "NR"
                if (data.hasNci()) {
                    s.cellId = data.nci.value
                    s.gnbId = (s.cellId / 4096L).toInt()
                    s.sectorId = (s.cellId % 4096L).toInt()
                }
                if (data.hasNarfcn()) s.earfcn = data.narfcn.value
                if (data.hasPci()) s.pci = data.pci.value
                if (data.hasTac()) s.tac = data.tac.value
                if (data.hasMcc()) s.mcc = data.mcc.value
                if (data.hasMnc()) s.mnc = data.mnc.value
                if (data.hasSsRsrp()) s.rsrp = data.ssRsrp.value.toInt()
                s.rsrq = if (data.hasSsRsrq()) data.ssRsrq.value.toInt() else Int.MIN_VALUE
                s.sinr = if (data.hasSsSinr()) data.ssSinr.value.toInt() else Int.MIN_VALUE
            }
            else -> return null
        }
        s
    } catch (e: Throwable) {
        null
    }
}
