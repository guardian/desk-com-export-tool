package com.gu.deskcomexporttool

import com.gu.deskcomexporttool.InteractionFixture.interaction
import com.gu.deskcomexporttool.InteractionValidator.{MaxBodyFieldChars, MaxEmailFieldChars, MaxSubjectFieldChars}
import org.scalatest.{FlatSpec, MustMatchers}

class InteractionValidatorSpec extends FlatSpec with MustMatchers {
  val validator = InteractionValidator()
  def invalidString(maxValidSize: Int) = new String(Array.fill[Char](maxValidSize + 10)('x'))

  "InteractionValidator" must "return true for valid interaction" in {
    validator.isValid(interaction) must equal(true)
  }

  "InteractionValidator" must "return false for interaction with too long body" in {
    validator.isValid(interaction.copy(body = Some(invalidString(MaxBodyFieldChars)))) must equal(false)
  }

  "InteractionValidator" must "return false for interaction with too long subject" in {
    validator.isValid(interaction.copy(subject = Some(invalidString(MaxSubjectFieldChars)))) must equal(false)
  }

  "InteractionValidator" must "return false for interaction with too long to addresss" in {
    validator.isValid(interaction.copy(to = Some(invalidString(MaxEmailFieldChars)))) must equal(false)
  }

  "InteractionValidator" must "return false for interaction with too long cc address" in {
    validator.isValid(interaction.copy(cc = Some(invalidString(MaxEmailFieldChars)))) must equal(false)
  }
  "InteractionValidator" must "return false for interaction with too long bcc address" in {
    validator.isValid(interaction.copy(bcc = Some(invalidString(MaxEmailFieldChars)))) must equal(false)
  }
}
