package com.l000phone.dao.ad.impl;

import com.l000phone.bean.ad.AdStat;
import com.l000phone.dao.ad.IAdStatDao;
import com.l000phone.util.DBCPUtil;
import org.apache.commons.dbutils.QueryRunner;
import org.apache.commons.dbutils.handlers.ScalarHandler;

import java.sql.SQLException;
import java.util.LinkedList;
import java.util.List;

/**
 * Description：每天各省各城市各广告的点击量操作DAO层接口实现类<br/>
 * Copyright (c) ， 2018， Jansonxu <br/>
 * This program is protected by copyright laws. <br/>
 *
 * @author 徐文波
 * @version : 1.0
 */
public class AdStatDaoImpl implements IAdStatDao {
    private QueryRunner qr = new QueryRunner(DBCPUtil.getDataSource());

    @Override
    public void updateBatch(List<AdStat> beans) {

        try {
            //步骤：
            //①准备两个容器分别存储要更新的AdUserClickCount实例和要插入的AdUserClickCount实例
            List<AdStat> updateContainer = new LinkedList<>();
            List<AdStat> insertContainer = new LinkedList<>();

            //②填充容器（一次与db中的记录进行比对，若存在，就添加到更新容器中；否则，添加到保存的容器中）
            String sql = "select click_count from ad_stat where `date`=? and province=? and city=? and ad_id=?";
            for (AdStat bean : beans) {
                Object click_count = qr.query(sql, new ScalarHandler<>("click_count"), new Object[]{bean.getDate(), bean.getProvince(), bean.getCity(), bean.getAd_id()});
                if (click_count == null) {
                    insertContainer.add(bean);
                } else {
                    updateContainer.add(bean);
                }
            }

            //③对更新的容器进行批量update操作
            // click_count=click_count+?  <~ ? 证明?传过来的是本batch新增的click_count,不包括过往的历史  (调用处调用：reduceByKey)
            // click_count=?  <~ ? 证明?传过来的是总的click_count （调用出：使用了updateStateByKey）
            sql = "update ad_stat set click_count=?  where `date`=? and province=? and city=? and ad_id=?";
            Object[][] params = new Object[updateContainer.size()][];
            for (int i = 0; i < params.length; i++) {
                AdStat bean = updateContainer.get(i);
                params[i] = new Object[]{bean.getClick_count(), bean.getDate(), bean.getProvince(), bean.getCity(), bean.getAd_id()};
            }
            qr.batch(sql, params);

            //④对保存的容器进行批量insert操作
            sql = "insert into ad_stat values(?,?,?,?,?)";
            params = new Object[insertContainer.size()][];
            for (int i = 0; i < params.length; i++) {
                AdStat bean = insertContainer.get(i);
                params[i] = new Object[]{bean.getDate(), bean.getProvince(), bean.getCity(), bean.getAd_id(), bean.getClick_count()};
            }
            qr.batch(sql, params);

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
