package com.gu.deskcomexporttool

object InteractionFixture {
  val interaction = Interaction(Some(1111L), "2018-01-01T01:01:01Z", "2019-01-01T01:01:01Z", Some("test body 1111"),
    Some("Test User 1111 <testuser1111@test.com>"), Some("<toaddress1111@test.com>"), Some("<ccaddress1111@test.com>"),
    Some("<bccaddress1111@test.com>"), Some("in"), Some("received"), Some("Test Subject 1111"),
    InteractionLinks(Link("/api/v2/cases/c11111/message"))
  )

  def interactionWithId(id: Long) = interaction.copy(id = Some(id))

  val getInteractionResponse = GetInteractionsResponse(
    InteractionResultsLinks(next = Link("/api/v2/interactions?per_page=12&since_id=next-since-id")),
    GetInteractionsEmbedded(List(InteractionFixture.interaction))
  )
}
