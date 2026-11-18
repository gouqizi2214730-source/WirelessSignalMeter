package com.craxiom.networksurvey.logging.db.dao;

import androidx.room.Dao;
import androidx.room.Query;
import androidx.room.Transaction;

import com.craxiom.networksurvey.logging.db.model.CdmaRecordEntity;
import com.craxiom.networksurvey.logging.db.model.GsmRecordEntity;
import com.craxiom.networksurvey.logging.db.model.LteRecordEntity;
import com.craxiom.networksurvey.logging.db.model.NrRecordEntity;
import com.craxiom.networksurvey.logging.db.model.UmtsRecordEntity;
import com.craxiom.networksurvey.logging.db.model.WifiBeaconRecordEntity;

import java.util.List;

@Dao
public interface SurveyRecordDao
{
    @Query("DELETE FROM gsm_survey_records")
    void deleteAllGsmRecords();

    @Query("DELETE FROM cdma_survey_records")
    void deleteAllCdmaRecords();

    @Query("DELETE FROM umts_survey_records")
    void deleteAllUmtsRecords();

    @Query("DELETE FROM lte_survey_records")
    void deleteAllLteRecords();

    @Query("DELETE FROM nr_survey_records")
    void deleteAllNrRecords();

    @Query("DELETE FROM wifi_survey_records")
    void deleteAllWifiRecords();

    @Transaction
    default void deleteAllRecords()
    {
        deleteAllGsmRecords();
        deleteAllCdmaRecords();
        deleteAllUmtsRecords();
        deleteAllLteRecords();
        deleteAllNrRecords();
        deleteAllWifiRecords();
    }

    @Query("SELECT COUNT(*) FROM gsm_survey_records")
    int getGsmRecordCount();

    @Query("SELECT COUNT(*) FROM cdma_survey_records")
    int getCdmaRecordCount();

    @Query("SELECT COUNT(*) FROM umts_survey_records")
    int getUmtsRecordCount();

    @Query("SELECT COUNT(*) FROM lte_survey_records")
    int getLteRecordCount();

    @Query("SELECT COUNT(*) FROM nr_survey_records")
    int getNrRecordCount();

    @Query("SELECT COUNT(*) FROM wifi_survey_records")
    int getWifiRecordCount();

    @Transaction
    default int getTotalRecordCount()
    {
        return getGsmRecordCount() + getCdmaRecordCount() + getUmtsRecordCount() + getLteRecordCount() + getNrRecordCount() + getWifiRecordCount();
    }
}
