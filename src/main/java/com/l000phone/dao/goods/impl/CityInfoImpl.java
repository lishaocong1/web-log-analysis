package com.l000phone.dao.goods.impl;

import com.l000phone.bean.goods.CityInfo;
import com.l000phone.dao.goods.ICityInfoDao;
import com.l000phone.util.DBCPUtil;
import org.apache.commons.dbutils.QueryRunner;
import org.apache.commons.dbutils.handlers.BeanListHandler;

import java.sql.SQLException;
import java.util.List;

public class CityInfoImpl implements ICityInfoDao {
    private QueryRunner qr=new QueryRunner(DBCPUtil.getDataSource());
    @Override
    public List<CityInfo> findAllInfos() {
        try {
            return qr.query("select * from city_info",new BeanListHandler<CityInfo>(CityInfo.class));
        } catch (SQLException e) {
            e.printStackTrace();
            return  null;
        }
    }
}
