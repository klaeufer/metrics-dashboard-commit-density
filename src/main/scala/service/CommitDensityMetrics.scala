package edu.luc.cs.metrics.defect.density.service

import java.io.PrintWriter
import java.time.temporal.TemporalAdjusters
import java.time.{Duration, Instant, ZoneId, ZonedDateTime}
import akka.actor.Actor
import akka.util.Timeout
import com.mongodb.casbah.Imports
import com.mongodb.casbah.Imports._
import reactivemongo.api.collections.bson.BSONCollection
import reactivemongo.bson.{BSONDocument, BSONDocumentReader}
import spray.json._
import spray.routing.HttpService
import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.Future
import scala.util._
import concurrent.duration._

/**
 * Created by sshilpika on 6/30/15.
 */

case class IssueState(Open:Int, Close:Int)
case class LocIssue(startDate: String, endDate:String,kloc:Double, issues: IssueState)

object JProtocol extends DefaultJsonProtocol{
  implicit val IssueInfoResult:RootJsonFormat[IssueState] = jsonFormat(IssueState,"open","closed")
  implicit val klocFormat:RootJsonFormat[LocIssue] = jsonFormat(LocIssue,"start_date","end_date","kloc","issues")
}

case class JsonPResult(commitInfo: JsValue)

object JsonPProtocol{
  import spray.json.DefaultJsonProtocol._
  implicit val gitResult = jsonFormat(JsonPResult,"defectDensity")
}

case class JsonPResultSpoilage(spoilageInfo: JsValue)

object JsonPProtocolSpoilage{
  import spray.json.DefaultJsonProtocol._
  implicit val gitResult = jsonFormat(JsonPResultSpoilage,"issueSpoilage")
}

case class CommitInfo(date: String,loc: Int, filename: String, rangeLoc: Long)

object CommitInfo{
  implicit object PersonReader extends BSONDocumentReader[CommitInfo]{
    def read(doc: BSONDocument): CommitInfo = {
      val date = doc.getAs[String]("date").get
      val loc = doc.getAs[Int]("loc").get
      val filename = doc.getAs[String]("filename").get
      val rangeLoc = doc.getAs[Long]("rangeLoc").get
      CommitInfo(date,loc,filename,rangeLoc)
    }
  }
}

case class IssueInfo(date: String,state: String/*, created_at: String, closed_at: String*/)

object IssueInfo{
  implicit object PersonReader extends BSONDocumentReader[IssueInfo]{
    def read(doc: BSONDocument): IssueInfo = {
      val date = doc.getAs[String]("date").get
      val state = doc.getAs[String]("state").get
      /*val created_at = doc.getAs[String]("created_at").get
      val closed_at = doc.getAs[String]("closed_at").get*/
      IssueInfo(date,state/*,created_at, closed_at*/)
    }
  }
}

trait CommitDensityService extends HttpService{

  def findTrackedRepo(user: String, repo: String, branch: String): (MongoClient, List[Imports.DBObject]) = {
    val mongoClient = MongoClient("localhost", 27017)
    //check the tracked DB to see if the repos exist
    val reponame = user + "_" + repo + "_" + branch
    val trackedColl = mongoClient("GitTracking")("RepoNames")
    val allDocs = trackedColl.find()
    println(allDocs)
    val dbExists = allDocs.filter(_("repo_name").toString.equalsIgnoreCase(reponame)).toList
    (mongoClient, dbExists)
  }

  def getDataForDensityMetrics(user: String, repo: String, branch:String, groupBy: String): Future[JsValue] ={
    import com.mongodb.casbah.Imports._
    val (mongoClient: MongoClient, dbExists: List[Imports.DBObject]) = findTrackedRepo(user, repo, branch)

    //get the result if repo is tracked
    val result =if(!dbExists.isEmpty) {
      val repositoryName = dbExists(0)("repo_name")
      val db = mongoClient(repositoryName + "_1")
      val coll = db("defect_density_" + groupBy)
      coll.findOne(MongoDBObject("id" -> "1")).toList map (_.getAs[String]("defectDensity").getOrElse(""))
    }else{
      List("""{"error" : "The repository isn't being tracked yet. Please submit a request for tracking the repository"}""")
    }
    Future{result(0).parseJson}
  }


  def dataForIssueSpoilage(user: String, repo: String, branch:String, groupBy: String): Future[JsValue] ={
    import com.mongodb.casbah.Imports._

    val (mongoClient: MongoClient, dbExists: List[Imports.DBObject]) = findTrackedRepo(user, repo, branch)

    val result =if(!dbExists.isEmpty) {
      val repositoryName = dbExists(0)("repo_name")
      val db = mongoClient(repositoryName + "_1")
      val coll = db("issue_spoilage_" + groupBy)
      coll.findOne(MongoDBObject("id" -> "2")).toList map (_.getAs[String]("issueSpoilage").getOrElse(""))
    }else{
      List("""{"error" : "The repository isn't being tracked yet. Please submit a request for tracking the repository"}""")
    }

    Future{result(0).parseJson}
  }




  val myRoute = path("density" / Segment / Segment / Segment) { ( user, repo, branch) =>
    get {
        optionalHeaderValueByName("Authorization") { (accessToken) =>
          jsonpWithParameter("jsonp") {
            import JsonPProtocol._
            import spray.httpx.SprayJsonSupport._
            parameters('groupBy ? "week") {(groupBy) =>
              onComplete(getDataForDensityMetrics(user, repo, branch, groupBy)) {
                case Success(value) => complete(JsonPResult(value))
                case Failure(value) => complete("Request to github failed with value : " + value)

              }
            }
          }
        }
    }
  }~ path("spoilage" /Segment / Segment / Segment) { ( user, repo, branch) =>
    get {
      optionalHeaderValueByName("Authorization") { (accessToken) =>
        jsonpWithParameter("jsonp") {
          import JsonPProtocolSpoilage._
          import spray.httpx.SprayJsonSupport._
          parameters('groupBy ? "week") {(groupBy) =>
            onComplete(dataForIssueSpoilage(user, repo, branch, groupBy)) {
              case Success(value) => complete(JsonPResultSpoilage(value))
              case Failure(value) => complete("Request to github failed with value : " + value)

            }
          }
        }
      }
    }
  }
}

class MyServiceActor extends Actor with CommitDensityService {

  def actorRefFactory = context

  def receive = runRoute(myRoute)
}

