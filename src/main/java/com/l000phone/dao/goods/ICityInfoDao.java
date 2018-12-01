package com.l000phone.dao.goods;

import com.l000phone.bean.goods.CityInfo;

import java.util.List;

/**
 * 操作城市信息数据访问层接口
 */
public interface ICityInfoDao {
    /**
     * 查询所有的城市信息
     */
     List<CityInfo> findAllInfos();
}
