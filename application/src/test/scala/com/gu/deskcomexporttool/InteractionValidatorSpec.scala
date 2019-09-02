package com.gu.deskcomexporttool

import org.scalatest.{FlatSpec, MustMatchers}

class InteractionValidatorSpec extends FlatSpec with MustMatchers {
  val validator = InteractionValidator()
  val invalidString = new String(Array.fill[Char](30001)('x'))

  "InteractionValidator" must "return true for valid interaction" in {
    validator.isValid(InteractionFixture.interaction) must equal(true)
  }

  "InteractionValidator" must "return false for interaction with too long body" in {
    validator.isValid(InteractionFixture.interaction.copy(body = Some(invalidString))) must equal(false)
  }

  "InteractionValidator" must "return false for interaction with too long subject" in {
    validator.isValid(InteractionFixture.interaction.copy(subject = Some(invalidString))) must equal(false)
  }
}
