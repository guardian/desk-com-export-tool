package com.gu.deskcomexporttool

import org.slf4j.LoggerFactory

trait InteractionValidator {
  def isValid(interaction: Interaction):Boolean
}

object InteractionValidator {
  private val log = LoggerFactory.getLogger(this.getClass)

  val MaxBodyFieldChars = 30000
  val MaxSubjectFieldChars = 3000
  val MaxEmailFieldChars = 4000

  def apply(): InteractionValidator = new InteractionValidator() {

    override def isValid(interaction: Interaction): Boolean = {
      validateString("body", interaction._links.self.href, interaction.body, MaxBodyFieldChars) &&
      validateString("subject", interaction._links.self.href, interaction.subject, MaxSubjectFieldChars) &&
      validateString("to", interaction._links.self.href, interaction.to, MaxEmailFieldChars) &&
      validateString("bcc", interaction._links.self.href, interaction.bcc, MaxEmailFieldChars) &&
      validateString("cc", interaction._links.self.href, interaction.cc, MaxEmailFieldChars)
    }

    private def validateString(fieldName: String, selfLink: String, fieldOption: Option[String], maxSize: Int): Boolean = {
      fieldOption match {
        case Some(field) if field.size > maxSize =>
          log.info(s"Interaction $selfLink has an invalid field $fieldName")
          false
        case _ =>
          true
      }
    }
  }
}