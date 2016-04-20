package controllers

import java.io.{BufferedWriter, FileWriter, File}
import javax.inject.Inject

import com.typesafe.config.ConfigFactory
import helper.Commons
import helper.email.MailTemplates
import models.{ConferenceSettingsService, ConferenceService}
import play.Configuration
import play.api.mvc.{Action, Controller}

/**
  * Created by manuel on 11.04.2016.
  */
class Conference @Inject() (configuration: Configuration, conferenceService: ConferenceService, conferenceSettingsService: ConferenceSettingsService) extends Controller {
  def conferenceCreator = Action {
    Ok(views.html.conference.conferenceCreator())
  }

  def conferenceCreated = Action(parse.urlFormEncoded) { request =>
    val name = request.body.get("name").get(0)
    val email = request.body.get("email").get(0)
    val secret = Commons.generateSecret()
    val id = conferenceService.create(name, email, secret)
    val conferenceLink = routes.Conference.conferenceEdit(id,secret).url
    MailTemplates.sendConferenceMail(name,conferenceLink,email)
    Ok(views.html.conference.conferenceCreated())
  }

  def conferenceEdit(id: Int, secret: String) = Action {
    val conference = conferenceService.findByIdAndSecret(id,secret)
    if(conference.size > 0) {
      val name = conference.get.name
      Ok(views.html.conference.conferenceEditor(id,secret,name,conferenceSettingsService.findAllByConference(id)))
    } else {
      NotFound("Invalid Url")
    }
  }

  def writeMet2AssFile = Action(parse.json) { request =>
    val writer = new BufferedWriter(new FileWriter(new File(ConfigFactory.load().getString("highlighter.statFile"))))
    request.body.asOpt[Map[String,Map[String,String]]].map { met2ass =>
      met2ass.foreach{ keyValMeth =>
        keyValMeth._2.foreach { keyValAss =>
          if(keyValAss._2=="danger" || keyValAss._2=="warning")
          writer.write(keyValMeth._1+","+keyValAss._1+"\n")
        }
      }
      writer.close()
      Ok("Ok")
    }.getOrElse {
      Ok("Error")
    }
  }
}