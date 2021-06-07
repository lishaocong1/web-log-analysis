package com.l000phone.jobs.ad

import java.sql.Date

import com.l000phone.bean.ad._
import com.l000phone.dao.ad._
import com.l000phone.dao.ad.impl._
import com.l000phone.util.{DateUtils, ResourcesUtils}
import kafka.serializer.StringDecoder
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.types.{LongType, StringType, StructField, StructType}
import org.apache.spark.sql.{Row, SQLContext}
import org.apache.spark.streaming.dstream.DStream
import org.apache.spark.streaming.kafka.KafkaUtils
import org.apache.spark.streaming.{Seconds, StreamingContext}
import org.apache.spark.{SparkConf, SparkContext}

import scala.collection.mutable.ArrayBuffer

/**
  * 广播流量实时统计
  */
object AdFlowRealTimeCalJob {
  def main(args: Array[String]): Unit = {
    //前提：
    val returnResult: (StreamingContext, DStream[(String, String)]) = operatePrepare(args)
    val ssc = returnResult._1
    val dsFromKafka = returnResult._2
    //新增

    //步骤：
    //1、实时计算各batch中的每天各用户对各广告的点击次数
    val perDayDS: DStream[(String, Long)] = calPerDayUserClickAdCnt(dsFromKafka)

    //2、使用filter过滤出每天对某个广告点击超过100次的黑名单用户，并写入MySQL中
    filterBlackListToDB(perDayDS)

    //3、使用transform操作，对每个batch RDD进行处理，都动态加载MySQL中的黑名单生成RDD，然后进行join （此处要进行左外连接查询）后，过滤掉batch RDD中的黑名单用户的广告点击行为
    val whiteList: DStream[(String)] = getAllWhiteList(dsFromKafka)


    //4、 使用updateStateByKey操作，实时计算每天各省各城市各广告的点击量，并时候更新到MySQL (隐藏者一个前提：基于上一步的白名单)
    val dsClickCnt: DStream[(String, Long)] = calPerDayProvinceCityClickCnt(whiteList)


    //5、使用transform结合Spark SQL，统计每天各省份top3热门广告
    calPerDayProviceHotADTop3(dsClickCnt)

    //6、使用window操作，对最近1小时滑动窗口内的数据，计算出各广告各分钟的点击量，并更新到MySQL中
    calSlideWindow(whiteList)




    //后续共通的操作
    //启动SparkStreaming
    ssc.start()

    //等待结束 （不能省略）
    ssc.awaitTermination()
  }


  /**
    * 使用window操作，对最近1小时滑动窗口内的数据，计算出各广告各分钟的点击量，并更新到MySQL中
    *
    * @param whiteList
    */
  def calSlideWindow(whiteList: DStream[String]) = {
    //reduceByKeyAndWindow((v1: Long, v2: Long) => v1 + v2, Minutes(60), Minutes(1))
    //（1543635439749#河北#秦皇岛#36#2）
    whiteList.map(tuple => {
      val arr = tuple.toString.split("#")
      val time = DateUtils.formatTimeMinute(new Date(arr(0).trim.toLong))
      val adId = arr(4).trim
      (time + "#" + adId, 1L)
    }).reduceByKeyAndWindow((v1: Long, v2: Long) => v1 + v2, Seconds(4),Seconds(2))
      .foreachRDD(rdd => {
        if (!rdd.isEmpty()) rdd.foreachPartition(itr => {
          val dao: IAdClickTrendDao = new AdClickTrendDaoImpl
          val beans: java.util.List[AdClickTrend] = new java.util.LinkedList[AdClickTrend]

          if (!itr.isEmpty) {
            itr.foreach(tuple => {
              val arr = tuple._1.split("#")
              val tempDate = arr(0).trim
              val date = tempDate.substring(0, tempDate.length - 2)
              val minute = tempDate.substring(tempDate.length - 2)
              val ad_id = arr(1).trim.toInt
              val click_count = tuple._2.toInt

              val bean = new AdClickTrend(date: String, ad_id: Int, minute: String, click_count: Int)
              beans.add(bean)
            })

            dao.updateBatch(beans)
          }

        })
      })
  }

  /**
    * 使用transform结合Spark SQL，统计每天各省份top3热门广告
    *
    * @param dsClickCnt
    */
  def calPerDayProviceHotADTop3(dsClickCnt: DStream[(String, Long)]) = {
    //上一步DStream中每个元素为元组，形如：(每天#省#城市#广告id,总次数)，  如：（20181201#辽宁#朝阳#2，1）
    //reduceByKey ~>注意，此处必须使用reduceByKey，不能使用updateStateByKey,因为上一步已经统计迄今为止的总次数了！
    // 此步仅仅是基于上一步，将所有省份下所有城市的广告点击总次数聚合起来即可
    dsClickCnt.map(perEle => {
      val arr = perEle._1.split("#")
      val day = arr(0).trim
      val province = arr(1).trim
      val adId = arr(3).trim
      (day + "#" + province + "#" + adId, perEle._2)
    }).reduceByKey(_ + _)
      //注意：内部会将所有rdd的结果聚合起来
      .foreachRDD(rdd => {
      //步骤：
      //①将RDD转换为DataFrame
      val ssc = new SQLContext(rdd.sparkContext)
      val rddTmp: RDD[Row] = rdd.map(tuple => {
        val arr = tuple._1.split("#")
        val day = arr(0).trim
        val province = arr(1).trim
        val adId = arr(2).trim
        Row(day, province, adId, tuple._2)
      })
      val structType: StructType = StructType(Seq(StructField("day", StringType, false), StructField("province", StringType, false), StructField("adId", StringType, false), StructField("click_count", LongType, false)))
      val df = ssc.createDataFrame(rddTmp, structType)

      //②将DataFrame映射为一张临时表
      df.createOrReplaceTempView("temp_ad_province")

      //③针对临时表求top3,将结果保存到db中
      ssc.sql("select *,row_number() over(partition by province order by click_count desc) level from temp_ad_province having level<=3")
        .rdd.foreachPartition(itr => {
        if (!itr.isEmpty) {
          val dao: IAdProvinceTop3Dao = new AdProvinceTop3DaoImpl
          val beans: java.util.List[AdProvinceTop3] = new java.util.LinkedList[AdProvinceTop3]
          itr.foreach(row => {
            val date = row.getAs[String]("day")
            val province = row.getAs[String]("province")
            val ad_id = row.getAs[String]("adId").toInt
            val click_count = row.getAs[Long]("click_count").toInt

            val bean: AdProvinceTop3 = new AdProvinceTop3(date: String, province: String, ad_id: Int, click_count: Int)
            beans.add(bean)
          })

          dao.updateBatch(beans)
        }
      })
    })
  }

  /**
    * 使用updateStateByKey操作，实时计算每天各省各城市各广告的点击量，并时候更新到MySQL
    *
    * @param whiteList
    */
  def calPerDayProvinceCityClickCnt(whiteList: DStream[String]) = {
    //步骤：
    //①实时计算每天各省各城市各广告的点击量
    //（1543635439749#河北#秦皇岛#36#2）
    val ds: DStream[(String, Long)] = whiteList.map(tuple => {
      val msg = tuple.toString
      val arr = msg.split("#")
      val day = DateUtils.formatDateKey(new Date(arr(0).trim.toLong))
      (day + "#" + arr(1).trim + "#" + arr(2).trim + "#" + arr(4).trim, 1L)
    }).updateStateByKey((nowBatch: Seq[Long], historyAllBatch: Option[Long]) => {
      val nowSum: Long = nowBatch.sum
      val historySum: Long = historyAllBatch.getOrElse(0)
      Some(nowSum + historySum)
    })

    //②将统计的结果实时更新到MySQL
    ds.foreachRDD(rdd => {
      if (!rdd.isEmpty()) {
        rdd.foreachPartition(itr => {
          val dao: IAdStatDao = new AdStatDaoImpl
          val beans: java.util.List[AdStat] = new java.util.LinkedList[AdStat]
          if (!itr.isEmpty) {
            itr.foreach(tuple => {
              val arr = tuple._1.split("#")
              val date = arr(0).trim
              val province = arr(1).trim
              val city = arr(2).trim
              val ad_id = arr(3).trim.toInt
              val click_count = tuple._2.toInt
              val bean = new AdStat(date: String, province: String, city: String, ad_id: Int, click_count: Int)
              beans.add(bean)
            })

            //将迭代器中所有的数据更新到db中
            dao.updateBatch(beans)
          }
        })
      }
    })

    //③返回结果
    ds


  }


  /**
    * 获得所有白名单用户信息
    * @return
    */
  def getAllWhiteList(dsFromKafka: DStream[(String, String)]) = {
    val whiteList: DStream[(String)] = dsFromKafka.transform(rdd => {
      //①动态加载MySQL中的黑名单生成RDD
      val dao: IAdBlackListDao = new AdBlackListDaoImpl
      val allBlackList: java.util.List[AdBlackList] = dao.findAllAdBlackList
      val tempContainer: ArrayBuffer[AdBlackList] = new ArrayBuffer
      //将Java List集合中的元素倒腾到scala对应的集合中
      for (i <- 0 until allBlackList.size()) {
        tempContainer.append(allBlackList.get(i))
      }

      //注意：RDD实例中就能获取其所属的SparkContext的实例，不能从外面来获取
      val blackListRDD: RDD[(Int, String)] = rdd.sparkContext.parallelize(tempContainer).map(perBean => (perBean.getUser_id, ""))

      //将DStream中每个rdd中每个元素进行变形，(null,系统当前时间 省份 城市 用户id 广告id)~>变形为：(用户id，系统当前时间#省份#城市#用户id#广告id)
      val rddTmp: RDD[(Int, String)] = rdd.map(perEle => {
        val arr = perEle._2.split("\\s+")
        val time = arr(0).trim
        val province = arr(1).trim
        val city = arr(2).trim
        val userId = arr(3).trim.toInt
        val adId = arr(4).trim
        (userId, time + "#" + province + "#" + city + "#" + userId + "#" + adId)
      })

      //黑名单RDD与DStream中当前的rdd进行左外连接查询, 设想其中一个结果：(34,(21312312321#湖北省#武汉市#34#2,None))
      val whiteAndBlackRDD: RDD[(Int, (String, Option[String]))] = rddTmp.leftOuterJoin(blackListRDD)

      //找出白名单
      val returnRDD: RDD[(String)] = whiteAndBlackRDD.filter(_._2._2 == None).map(perEle => {
        val arr = perEle._2._1.split("#")
        (arr(0).trim + "#" + arr(1).trim + "#" + arr(2).trim + "#" + arr(3).trim + "#" + arr(4).trim)
      })

      //返回一个全新的rdd，然后再置于DStream中
      returnRDD
    })

    //返回结果给调用点
    whiteList
  }

  /**
    * 使用filter过滤出每天对某个广告点击超过100次的黑名单用户，并写入MySQL中
    *
    * @param perDayDS
    */
  def filterBlackListToDB(perDayDS: DStream[(String, Long)]) = {
    //  基于上一步的结果进行筛选，并将结果保存到db中 （存在则不用处理，不存在就save）
    //  DStream[(每天#用户id#广告id,总次数)]实例.filter(每个元素=>每个元素._2>100).foreachRDD(XXX)
    //在正式提交时，需要将2~>100
    perDayDS.filter(perEle => perEle._2 > 2).foreachRDD(rdd => {
      if (!rdd.isEmpty()) rdd.foreachPartition(itr => {
        val dao: IAdBlackListDao = new AdBlackListDaoImpl

        val beans: java.util.List[AdBlackList] = new java.util.LinkedList[AdBlackList]

        if (!itr.isEmpty) {
          itr.foreach(tuple => {
            val userId = tuple._1.split("#")(1).trim.toInt
            val bean = new AdBlackList(userId)
            beans.add(bean)
          })

          dao.updateBatch(beans)
        }
      })

    })
  }


  /**
    * 实时计算各batch中的每天各用户对各广告的点击次数，并使用高性能方式将每天各用户对各广告的点击次数写入MySQL中
    *
    * @param dsFromKafka
    * @return
    */
  def calPerDayUserClickAdCnt(dsFromKafka: DStream[(String, String)]): DStream[(String, Long)] = {
    //思路：
    //①实时计算各batch中的每天各用户对各广告的点击次数
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


    //②使用高性能方式将每天各用户对各广告的点击次数写入MySQL中
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

    //③返回结果
    perDayDS
  }


  /**
    * 前期准备
    *
    * @param args
    * @return
    */
  def operatePrepare(args: Array[String]) = {
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
    val dsFromKafka: DStream[(String, String)] = KafkaUtils.createDirectStream[String, String, StringDecoder, StringDecoder](ssc, Map("metadata.broker.list" -> "JANSON01:9092,JANSON02:9092,JANSON03:9092"), Set("ad_real_time_log"))

    //③设置checkPoint
    ssc.checkpoint("ck")

    //④返回
    (ssc, dsFromKafka)
  }
}
