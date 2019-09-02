package com.gu.deskcomexporttool

import org.slf4j.LoggerFactory

trait InteractionValidator {
  def isValid(interaction: Interaction):Boolean
}

object InteractionValidator {
  private val log = LoggerFactory.getLogger(this.getClass)

  def apply(): InteractionValidator = new InteractionValidator() {

    override def isValid(interaction: Interaction): Boolean = {
      validateString("body", interaction._links.self.href, interaction.body) &&
      validateString("subject", interaction._links.self.href, interaction.subject)
    }

    private def validateString(fieldName: String, selfLink: String, fieldOption: Option[String]): Boolean = {
      fieldOption match {
        case Some(field) if field.size > 30000 =>
          log.info(s"Interaction $selfLink has an invalid field $fieldName")
          false
        case _ =>
          true
      }
    }
  }
}