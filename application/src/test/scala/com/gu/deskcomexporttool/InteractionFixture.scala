package com.gu.deskcomexporttool

object InteractionFixture {
  val interaction = Interaction(1111, Some("2018-01-01T01:01:01Z"), Some("2019-01-01T01:01:01Z"), Some("test body 1111"),
    Some("Test User 1111 <testuser1111@test.com>"), Some("<toaddress1111@test.com>"), Some("<ccaddress1111@test.com>"),
    Some("<bccaddress1111@test.com>"), Some("in"), Some("received"), Some("Test Subject 1111"),
    InteractionLinks(Link("/api/v2/cases/c11111/message"))
  )
}
