import java.text.SimpleDateFormat
import java.time.{ZoneId, LocalDateTime, Instant}
import java.time.temporal.ChronoUnit

import akka.actor.Actor
import reactivemongo.api.collections.bson.BSONCollection
import reactivemongo.bson.{BSONDocumentReader, BSONDocument}
import spray.routing.HttpService
import spray.json._
import DefaultJsonProtocol._
import util._
import concurrent.ExecutionContext.Implicits._
import scala.concurrent.{Future}
import java.time.ZoneOffset
import scalaz.Scalaz._
/**
 * Created by sshilpika on 6/30/15.
 */

case class JsonPResult1(commitInfo: JsValue)

object JsonPResultProtocol1 {
  import spray.json.DefaultJsonProtocol._
  implicit val gitResult = jsonFormat(JsonPResult1,"commitInfo")
}
case class CommitInfo1(date: String,loc: Int, filename: String, since: String, until: String)

object CommitInfo1{
  implicit object PersonReader extends BSONDocumentReader[CommitInfo1]{
    def read(doc: BSONDocument): CommitInfo1 = {
      val date = doc.getAs[String]("date").get
      val loc = doc.getAs[Int]("loc").get
      val filename = doc.getAs[String]("filename").get
      val since = doc.getAs[String]("since").get
      val until = doc.getAs[String]("until").get
      CommitInfo1(date,loc,filename,since,until)
    }
  }
}

trait CommitDensityServiceMonth extends HttpService{
  val sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'")

  def connect(repo: String, branch:String, displayOption: String): Future[JsValue] = {
    import reactivemongo.api._

    //get an instance of the driver
    //(creates an actor system)
    val driver = new MongoDriver
    val connection = driver.connection(List("localhost"))

    //gets a reference of the database
    val db = connection.db(repo)
    val collection = db.collectionNames
    val added = collection.map(p => p.filter(_.contains("COLL")))
    val sorted = added.map(p => p.sorted)

    // grouped by year
    val groupByYear = sorted.map(p => p.groupBy(_.take(8)))

    // split each year to months
    val monthMaps = groupByYear.flatMap(p => {

      val yearLists = Future.sequence(p.map{case(year,yearGroup) =>{

        // list of items in each year group
        val maps = Future.sequence(yearGroup.map(yearlyCollection => {

          val coll = db.collection[BSONCollection](yearlyCollection)
          val collectionsList = coll.find(BSONDocument()).sort(BSONDocument("date" -> 1)).cursor[CommitInfo1].collect[List]()
          val res = collectionsList.map(p => {
            val groupedByMonth = p.groupBy(x => {
              LocalDateTime.ofInstant(Instant.parse(x.date),ZoneId.of("UTC")).getMonth
              })
            var previousCommitLoc = 0
            // this give a map of date and loc for that date for all files commited on that Month
            val tuples = groupedByMonth.map{case(month, commitInfoList) =>{
              val dateGroups = commitInfoList.groupBy(x => x.date).toList.sortBy(_._1).toMap


              val lis1 = dateGroups.map{
                case(d,commitInfo) => {
                  val locPerDate = commitInfo.foldLeft(0){(a,c) => c.loc+a}
                  d -> locPerDate
                }}.toList


              val ldt = LocalDateTime.ofInstant(Instant.parse(lis1(0)._1),ZoneId.of("UTC"))
              println(ldt.toLocalDate.lengthOfMonth()+"ldt.toLocalDate.lengthOfMonth()")
              val startOfMonth = ldt.withDayOfMonth(1).toInstant(ZoneOffset.ofHours(0)).toString
              val endOfMonth = ldt.withDayOfMonth(ldt.toLocalDate.lengthOfMonth()).toInstant(ZoneOffset.ofHours(0)).toString
              val lis2 = (startOfMonth,previousCommitLoc)+:lis1
              val lis3 = lis2.tail:+(endOfMonth,0)
              val lis4 = lis2.zip(lis3)

              previousCommitLoc = lis1(lis1.size-1)._2

              val result = lis4.map(h => {
                (ChronoUnit.MILLIS.between(Instant.parse(h._1._1),Instant.parse(h._2._1)),h._1._2)
              })
              val totalMillis = result.foldLeft(0L:Long){(l,z) => l+z._1}
              val finalResult = (result.map(z => z._1*z._2).sum)/totalMillis
              val r = finalResult.toDouble/1000
              val resultF = s"""{"Month": "$month", "star_Date": "$startOfMonth", "end_date": "$endOfMonth", "KLOC": $r}""".parseJson
              (month->r)


            }}

            tuples
          })

          res

        }))map(x => x.reduce(_ |+| _))

        val yearMaps = maps.map(x => (year.drop(4) -> x))

        yearMaps.map(p => {
          val jsonListMonths = p._2.foldLeft(Nil:List[JsValue]){(lis, monthkLocTuple) => {
            val month = monthkLocTuple._1.getValue
            val kLoc = monthkLocTuple._2
            val JsResult = s"""{"Month": $month, "KLOC": $kLoc}""".parseJson
            lis:+JsResult
          }}.toJson
          val year = p._1
          val yearMonthsJs = s"""{"$year": $jsonListMonths}""".parseJson
          yearMonthsJs
        })


      }})

      yearLists.map(p => p.toList.toJson)

    })

    monthMaps

  }




  val myRoute = path(Segment / Segment) { ( repo, branch) =>
    get {
        optionalHeaderValueByName("Authorization") { (accessToken) =>
          jsonpWithParameter("jsonp") {
            import JsonPResultProtocol1._
            import spray.httpx.SprayJsonSupport._
            onComplete(connect(repo, branch, "week")) {
              case Success(value) => complete(JsonPResult1(value))
              case Failure(value) => complete("Request to github failed with value : " + value)
            }
          }
        }
    }
  }
}
class MyServiceActor1 extends Actor with CommitDensityServiceMonth {

  def actorRefFactory = context

  def receive = runRoute(myRoute)
}
