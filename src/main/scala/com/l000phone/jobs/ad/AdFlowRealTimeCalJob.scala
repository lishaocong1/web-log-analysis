package com.l000phone.jobs.ad

import java.sql.Date

import com.l000phone.bean.ad.AdUserClickCount
import com.l000phone.dao.ad.IAdUserClickCountDao
import com.l000phone.dao.ad.impl.AdUserClickCountDaoImpl
import com.l000phone.util.{DateUtils, ResourcesUtils}
import kafka.serializer.StringDecoder
import org.apache.spark.streaming.dstream.DStream
import org.apache.spark.streaming.kafka.KafkaUtils
import org.apache.spark.streaming.{Seconds, StreamingContext}
import org.apache.spark.{SparkConf, SparkContext}

/**
  * 广播流量实时统计
  */
object AdFlowRealTimeCalJob {
  def main(args: Array[String]): Unit = {
    //前提：
    //①获得StreamingContext的实例
    //sparkContext: SparkContext, batchDuration: Duration
    val config: SparkConf = new SparkConf
    config.setAppName(AdFlowRealTimeCalJob.getClass.getSimpleName)
    //若是本地集群模式，需要单独设置
    if (ResourcesUtils.dMode.toString.toLowerCase().equals("local")) {
      config.setMaster("local[*]")
    }

    val sc: SparkContext = new SparkContext(config)
    sc.setLogLevel("WARN")

    val ssc: StreamingContext = new StreamingContext(sc, Seconds(2))

    //②从消息队列中获取消息
    val dsFromKafka: DStream[(String, String)] = KafkaUtils.createDirectStream[String, String, StringDecoder, StringDecoder](ssc, Map("metadata.broker.list" -> "min1:9092,min2:9092,min3:9092"), Set("ad_real_time_log"))

    //③设置checkPoint
    ssc.checkpoint("ck")

    //步骤：
    //1、实时计算各batch中的每天各用户对各广告的点击次数
    val perDayDS: DStream[(String, Long)] = dsFromKafka.map(perMsg => {
      val msgValue = perMsg._2


      //将DStream中每个元素转换成：（yyyy-MM-dd#userId#adId，1L）, 正则表达式： \s+，匹配：所有的空格：全角，半角，tab
      val arr = msgValue.split("\\s+")
      val day = DateUtils.formatDateKey(new Date(arr(0).trim.toLong))
      val userId = arr(3).trim
      val adId = arr(4).trim
      (day + "#" + userId + "#" + adId, 1L)
    }).updateStateByKey[Long]((nowBatch: Seq[Long], history: Option[Long]) => {
      val nowSum: Long = nowBatch.sum
      val historySum: Long = history.getOrElse(0)
      Some(nowSum + historySum)
    })


    //使用高性能方式将每天各用户对各广告的点击次数写入MySQL中
    perDayDS.foreachRDD(rdd => {
      if (!rdd.isEmpty()) rdd.foreachPartition(itr => {
        val dao: IAdUserClickCountDao = new AdUserClickCountDaoImpl
        val beans: java.util.List[AdUserClickCount] = new java.util.LinkedList

        if (!itr.isEmpty) {
          itr.foreach(tuple => {
            val arr = tuple._1.split("#")
            val click_count = tuple._2.toInt
            val date = arr(0).trim
            val user_id = arr(1).trim.toInt
            val ad_id = arr(2).trim.toInt
            val bean = new AdUserClickCount(date: String, user_id: Int, ad_id: Int, click_count: Int)
            beans.add(bean)
          })
          dao.updateBatch(beans)
        }
      })
    })



    //后续共通的操作

    //启动SparkStreaming
    ssc.start()

    //等待结束 （不能省略）
    ssc.awaitTermination()
  }
}
