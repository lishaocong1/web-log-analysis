package com.l000phone.dao.ad;



import com.l000phone.bean.ad.AdProvinceTop3;

import java.util.List;

/**
 * Description：每天各省份top3热门广告的数据处理Dao层接口<br/>
 * Copyright (c) ， 2018， Jansonxu <br/>
 * This program is protected by copyright laws. <br/>
 *
 *
 * @author 徐文波
 * @version : 1.0
 */
public interface IAdProvinceTop3Dao {
    /**
     * 批量更新
     */
    void updateBatch(List<AdProvinceTop3> beans);
}
