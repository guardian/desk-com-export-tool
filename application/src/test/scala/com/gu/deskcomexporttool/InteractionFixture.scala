package com.gu.deskcomexporttool

object InteractionFixture {
  val interaction = Interaction(1111, "2018-01-01T01:01:01Z", "2019-01-01T01:01:01Z", "test body 1111",
    "Test User 1111 <testuser1111@test.com>", Some("<toaddress1111@test.com>"), Some("<ccaddress1111@test.com>"),
    Some("<bccaddress1111@test.com>"), "in", "received", "Test Subject 1111",
    InteractionLinks(Link("/api/v2/cases/c11111/message"))
  )
}
