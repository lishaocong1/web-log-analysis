package com.l000phone.dao.ad.impl;

import com.l000phone.bean.ad.AdProvinceTop3;
import com.l000phone.bean.ad.AdProvinceTop3Temp;
import com.l000phone.dao.ad.IAdProvinceTop3Dao;
import com.l000phone.util.DBCPUtil;
import org.apache.commons.dbutils.QueryRunner;
import org.apache.commons.dbutils.handlers.BeanListHandler;
import org.apache.commons.dbutils.handlers.ScalarHandler;

import java.sql.SQLException;
import java.util.LinkedList;
import java.util.List;

/**
 * Description：每天各省份top3热门广告的数据处理Dao层接口实现类<br/>
 * Copyright (c) ， 2018， Jansonxu <br/>
 * This program is protected by copyright laws. <br/>
 * Date：2018年03月28日
 *
 * @author 徐文波
 * @version : 1.0
 */
public class AdProvinceTop3DaoImpl implements IAdProvinceTop3Dao {
    private QueryRunner qr = new QueryRunner(DBCPUtil.getDataSource());

    @Override
    public void updateBatch(List<AdProvinceTop3> beans) {
        try {
            //步骤：
            //①准备两个容器分别存储要更新的AdUserClickCount实例和要插入的AdUserClickCount实例
            List<AdProvinceTop3> updateContainer = new LinkedList<>();
            List<AdProvinceTop3> insertContainer = new LinkedList<>();

            //②填充容器（一次与db中的记录进行比对，若存在，就添加到更新容器中；否则，添加到保存的容器中）
            String sql = "select click_count from ad_province_top3 where `date`=? and province=? and ad_id=?";
            for (AdProvinceTop3 bean : beans) {
                Object click_count = qr.query(sql, new ScalarHandler<>("click_count"), new Object[]{bean.getDate(), bean.getProvince(), bean.getAd_id()});
                if (click_count == null) {
                    insertContainer.add(bean);
                } else {
                    updateContainer.add(bean);
                }
            }

            //③对更新的容器进行批量update操作
            // click_count=click_count+?  <~ ? 证明?传过来的是本batch新增的click_count,不包括过往的历史  (调用处调用：reduceByKey)
            // click_count=?  <~ ? 证明?传过来的是总的click_count （调用出：使用了updateStateByKey）
            sql = "update ad_province_top3 set click_count=?  where `date`=? and province=? and ad_id=?";
            Object[][] params = new Object[updateContainer.size()][];
            for (int i = 0; i < params.length; i++) {
                AdProvinceTop3 bean = updateContainer.get(i);
                params[i] = new Object[]{bean.getClick_count(), bean.getDate(), bean.getProvince(), bean.getAd_id()};
            }
            qr.batch(sql, params);

            //④对保存的容器进行批量insert操作
            saveToDB(insertContainer);


            //对db中已经保存的数据进行筛选（只选出表中相同省份的前三条）
            List<AdProvinceTop3> allBeans = qr.query("SELECT * FROM ad_province_top3 ORDER BY province,click_count DESC", new BeanListHandler<AdProvinceTop3>(AdProvinceTop3.class));


            StringBuilder builder = new StringBuilder();
            builder.append("SELECT province ,(adIdTypeCnt-3) delCnt FROM ( ");
            builder.append("SELECT COUNT(*) adIdTypeCnt, province FROM ");
            builder.append("(SELECT * FROM ad_province_top3 ORDER BY province,click_count DESC) t ");
            builder.append("GROUP BY province ");
            builder.append(")t2 WHERE adIdTypeCnt>3");

            //存储了相应省份要删除的多余的记录条数
            List<AdProvinceTop3Temp> willDelBeans = qr.query(builder.toString(), new BeanListHandler<AdProvinceTop3Temp>(AdProvinceTop3Temp.class));

            //真正要删除的记录
            List<AdProvinceTop3> realDelBeans = new LinkedList<>();

            //存储每个省份对应的信息
            List<AdProvinceTop3> perProvince = new LinkedList<>();


            //找出要删除的bean
            for (AdProvinceTop3Temp bean : willDelBeans) {
                //清空容器
                perProvince.clear();

                String provinceName = bean.getProvince();
                int delCnt = bean.getDelCnt();
                for (int i = 0; i < allBeans.size(); i++) {
                    AdProvinceTop3 beanTmp = allBeans.get(i);
                    if (provinceName != null && provinceName.equals(beanTmp.getProvince())) {
                        perProvince.add(beanTmp);
                    }
                }

                //从当前省份对应的容器中筛选出待删除的省份信息，再保存到最终待删除的容器中
                for (int i = 3; i < perProvince.size(); i++) {
                    realDelBeans.add(perProvince.get(i));
                }
            }


            // 清空表
            for (AdProvinceTop3 realyDelBean : realDelBeans) {
                qr.update("delete from ad_province_top3 where  `date`=? and province=? and ad_id=?", new Object[]{realyDelBean.getDate(), realyDelBean.getProvince(), realyDelBean.getAd_id()});
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * 保存到db中
     *
     * @param insertContainer
     * @throws SQLException
     */
    private void saveToDB(List<AdProvinceTop3> insertContainer) throws SQLException {
        String sql;
        Object[][] params;
        sql = "insert into ad_province_top3 values(?,?,?,?)";
        params = new Object[insertContainer.size()][];
        for (int i = 0; i < params.length; i++) {
            AdProvinceTop3 bean = insertContainer.get(i);
            params[i] = new Object[]{bean.getDate(), bean.getProvince(), bean.getAd_id(), bean.getClick_count()};
        }
        qr.batch(sql, params);
    }
}
