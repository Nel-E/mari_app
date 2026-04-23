package com.mari.app.data.remote

import com.mari.app.data.remote.dto.AppUpdateLatestDto
import com.mari.app.data.remote.dto.AppUpdateReleaseNoteDto
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming

interface AppUpdateApiService {

    @GET("api/app-update/latest")
    suspend fun getLatest(
        @Query("track") track: String,
        @Query("component") component: String = "phone",
    ): AppUpdateLatestDto

    @GET("api/app-update/releases")
    suspend fun getReleases(
        @Query("track") track: String,
        @Query("component") component: String,
        @Query("after_version_code") after: Int,
    ): List<AppUpdateReleaseNoteDto>

    @Streaming
    @GET("api/app-update/artifacts/{track}/{component}/{file_name}")
    suspend fun downloadArtifact(
        @Path("track") track: String,
        @Path("component") component: String,
        @Path("file_name", encoded = true) fileName: String,
    ): Response<ResponseBody>
}
