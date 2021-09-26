package com.pabhis.covidassistant.models

data class StateModel(
	val statewise: ArrayList<StatewiseItem>
)

data class StatewiseItem(

	val active: String? = null,
	val confirmed: String? = null,
	val deaths: String? = null,
	val recovered: String? = null,

	val deltaconfirmed: String? = null,
	val deltadeaths: String? = null,
	val deltarecovered: String? = null,
	val deltaactive: String? = null,

	val state: String? = null,
	val statecode: String? = null,

	val lastupdatedtime: String? = null
)

