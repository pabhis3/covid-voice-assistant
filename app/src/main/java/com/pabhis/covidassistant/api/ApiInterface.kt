package com.pabhis.covidassistant.api

import com.pabhis.covidassistant.models.DistrictModel
import com.pabhis.covidassistant.models.StateModel
import retrofit2.Response
import retrofit2.http.GET

interface ApiInterface {
    @GET("data.json")
    suspend fun getStates(): Response<StateModel>

    @GET("v2/state_district_wise.json")
    suspend fun getDistrict(): Response<ArrayList<DistrictModel>>
}