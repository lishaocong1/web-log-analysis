package com.l000phone.bean.goods;

/**
 * 城市信息表
 */
public class CityInfo {
    /**
     * 城市编号
     */
    private int city_id;

    /**
     * 城市名
     */
    private String city_name;

    /**
     * 城市所属区域
     */
    private String area;

    public CityInfo() {
    }

    public CityInfo(int city_id, String city_name, String area) {
        this.city_id = city_id;
        this.city_name = city_name;
        this.area = area;
    }

    public int getCity_id() {
        return city_id;
    }

    public void setCity_id(int city_id) {
        this.city_id = city_id;
    }

    public String getCity_name() {
        return city_name;
    }

    public void setCity_name(String city_name) {
        this.city_name = city_name;
    }

    public String getArea() {
        return area;
    }

    public void setArea(String area) {
        this.area = area;
    }
}
