package com.l000phone.bean.ad;

/**
 * Description：最近1小时各广告各分钟的点击量数据的封装<br/>
 * Copyright (c) ， 2018， Jansonxu <br/>
 * This program is protected by copyright laws. <br/>
 *
 * @author 徐文波
 * @version : 1.0
 */
public class AdClickTrend {
    /**
     * 日期 （每天）
     */
    private String date;

    /**
     * 广告编号
     */
    private int ad_id;

    /**
     * 分钟（20180328 14:50）
     */
    private String minute;

    /**
     * 点击次数
     */
    private int click_count;

    public AdClickTrend() {
    }

    public AdClickTrend(String date, int ad_id, String minute, int click_count) {
        this.date = date;
        this.ad_id = ad_id;
        this.minute = minute;
        this.click_count = click_count;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public int getAd_id() {
        return ad_id;
    }

    public void setAd_id(int ad_id) {
        this.ad_id = ad_id;
    }

    public String getMinute() {
        return minute;
    }

    public void setMinute(String minute) {
        this.minute = minute;
    }

    public int getClick_count() {
        return click_count;
    }

    public void setClick_count(int click_count) {
        this.click_count = click_count;
    }

    @Override
    public String toString() {
        return "AdClickTrend{" +
                "date='" + date + '\'' +
                ", ad_id=" + ad_id +
                ", minute='" + minute + '\'' +
                ", click_count=" + click_count +
                '}';
    }
}
