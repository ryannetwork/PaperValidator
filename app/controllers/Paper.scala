package controllers

import javax.inject.Inject

import scala.collection.mutable.ListBuffer
import scala.io.Source
import scala.reflect.io.File
import util.control.Breaks._
import helper.{Commons, PaperProcessingManager}
import models._
import play.api.{Logger, Configuration}
import play.api.db.Database
import play.api.mvc.{Action, Controller}

import scala.concurrent.Future
import play.api.libs.concurrent.Execution.Implicits.defaultContext

/**
  * Created by manuel on 11.04.2016.
  */
class Paper @Inject()(database: Database, configuration: Configuration, papersService: PapersService,
                      questionService: QuestionService, method2AssumptionService: Method2AssumptionService,
                      paperResultService: PaperResultService, answerService: AnswerService,
                      conferenceSettingsService: ConferenceSettingsService, paperMethodService: PaperMethodService
                     ) extends Controller {

  def show(id:Int, secret:String) = Action {
    val paper = papersService.findByIdAndSecret(id,secret)
    if(paper.size > 0) {
      var results = paperResultService.findByPaperId(id)
      results = addMethodsAndAssumptions(id,results)
      val filePath = configuration.getString("highlighter.pdfSourceDir").get+"/"+Commons.getSecretHash(secret)+"/log.txt"
      val fileLengh = File(filePath).length
      var log = ""
      if(fileLengh < 9999999) {
        val source = scala.io.Source.fromFile(filePath)
        log = try source.mkString.replace("\n","\n<br>") finally source.close()
      }
      Ok(views.html.paper.showPaper(paper.get,Commons.getSecretHash(secret),results,log))
    } else {
      Unauthorized(views.html.error.unauthorized())
    }
  }

  def addMethodsAndAssumptions(id:Int,results: List[PaperResult]) : List[PaperResult] = {
    var allResults = results
    val paper = papersService.findById(id).get
    val m2aList = answerService.findByPaperId(paper.id.get)
    val conferenceSettings = conferenceSettingsService.findAllByPaperId(paper.id.get,paper.conferenceId).to[ListBuffer]
    m2aList.foreach(m2a => {
      breakable {
        conferenceSettings.zipWithIndex.foreach{case (confSetting,i) => {
          if(confSetting.flag.get != ConferenceSettings.FLAG_IGNORE) {
            if(m2a.method.toLowerCase() == confSetting.methodName.toLowerCase() &&
              m2a.assumption.toLowerCase() == confSetting.assumptionName.toLowerCase()) {
              val m2aDescr = m2a.method+" <span class='glyphicon glyphicon-arrow-right'></span> "+m2a.assumption
              var m2aResult = "Related: <b>"+ m2a.isRelated + "</b>, Checked before: <b>" + m2a.isCheckedBefore + "</b>"
              var symbol = PaperResult.SYMBOL_ERROR
              if(m2a.isRelated && m2a.isCheckedBefore) {
                symbol = PaperResult.SYMBOL_OK
              } else if(m2a.isRelated) {
                symbol = PaperResult.SYMBOL_WARNING
                m2aResult += getM2AFlagText(confSetting)
              } else {
                m2aResult += getM2AFlagText(confSetting)
              }
              allResults = allResults:+ new PaperResult(Some(1L),id,PaperResult.TYPE_M2A,m2aDescr,m2aResult,symbol)
              conferenceSettings.remove(i)
              break
            }
          }
        }}
      }
    })
    Logger.debug(conferenceSettings.mkString(";"))
    conferenceSettings.foreach(confSetting => {
      if(confSetting.flag.get != ConferenceSettings.FLAG_IGNORE){
        val m2aDescr = confSetting.methodName+" <span class='glyphicon glyphicon-arrow-right'></span> "+
          confSetting.assumptionName
        var m2aResult = "Not Found in Paper"
        m2aResult += getM2AFlagText(confSetting)
        var symbol = PaperResult.SYMBOL_ERROR
        if(confSetting.flag.get==ConferenceSettings.FLAG_EXPECT) {
          symbol = PaperResult.SYMBOL_WARNING
        }
        allResults = allResults:+ new PaperResult(Some(1L),id,PaperResult.TYPE_M2A,m2aDescr,m2aResult,symbol)
      }
    })
    allResults
  }

  def getM2AFlagText(confSetting: ConferenceSettings): String = {
    confSetting.flag.get match {
      case ConferenceSettings.FLAG_REQUIRE => {
        "<span class='m2aFlag text-danger glyphicon glyphicon-flag'><span>"
      }
      case ConferenceSettings.FLAG_EXPECT => {
        "<span class='m2aFlag text-warning glyphicon glyphicon-flag'><span>"
      }
      case ConferenceSettings.FLAG_IGNORE => {
        "<span class='m2aFlag text-muted glyphicon glyphicon-flag'><span>"
      }
    }
  }

  def confirmPaper(id:Int, secret:String) = Action {
    if(papersService.findByIdAndSecret(id,secret).size > 0){
      papersService.updateStatus(id,Papers.STATUS_IN_PPLIB_QUEUE)
      Future  {
        PaperProcessingManager.run(database, configuration, papersService, questionService,
          method2AssumptionService, paperResultService, paperMethodService)
      }
      Ok(views.html.paper.confirmPaper())
    } else {
      Unauthorized(views.html.error.unauthorized())
    }
  }

}
