package com.l000phone.dao.ad;



import com.l000phone.bean.ad.AdUserClickCount;

import java.sql.SQLException;
import java.util.List;

/**
 * Description：统计每天各用户对各广告的点击次数功能接口<br/>
 * Copyright (c) ， 2018， Jansonxu <br/>
 * This program is protected by copyright laws. <br/>
 *
 *
 * @author 徐文波
 * @version : 1.0
 */
public interface IAdUserClickCountDao {
    /**
     * 批量更新（包括两步骤：①批量更新；②批量保存）
     */
    void updateBatch(List<AdUserClickCount> beans);
}
